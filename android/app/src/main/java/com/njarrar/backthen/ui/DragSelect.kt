package com.njarrar.backthen.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.njarrar.backthen.core.Photo
import kotlin.math.abs

/**
 * Drag a finger across photos to pick or unpick them in one move, as in the Photos app.
 * In select mode a sideways drag that starts on a photo picks; up and down still scrolls.
 * After a long press starts selecting, moving the same finger picks in any direction.
 * Everything between the first photo and the one under the finger follows the first
 * photo: picked if it was not, unpicked if it was. Near the top or bottom edge the list
 * scrolls on its own, using [edge] as a normalized velocity factor in [-1f, 1f] so callers
 * can scale by frame delta time (`dt`) consistently across 60Hz/90Hz/120Hz displays.
 */
class DragSelect {
    private val tiles = mutableMapOf<String, Pair<Photo, Rect>>()
    private var origin = Offset.Zero
    /** Normalized edge scroll factor in [-1f, 1f], multiplied by dp/sec * dt in the scrolling list. */
    val edge = mutableFloatStateOf(0f)
    /** Checks the photo under the finger again, after layout commits a scroll frame. */
    var recheck: (() -> Unit)? = null

    fun place(photo: Photo, bounds: Rect) { tiles[photo.key] = photo to bounds }
    fun forget(key: String) { tiles.remove(key) }

    private fun hit(local: Offset): Photo? {
        val at = local + origin
        return tiles.values.firstOrNull { it.second.contains(at) }?.first
    }

    fun modifier(
        order: () -> List<Photo>,
        selecting: () -> Boolean,
        picked: () -> List<Photo>,
        onPick: (List<Photo>) -> Unit,
    ): Modifier = Modifier
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(this) {
            val zone = 64.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val first = hit(down.position) ?: return@awaitEachGesture
                val wasSelecting = selecting()
                // Wait for the finger to move past the touch slop, then decide: pick, or let it scroll.
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed) return@awaitEachGesture
                    val d = change.position - down.position
                    if (d.getDistance() < viewConfiguration.touchSlop) continue
                    val sideways = abs(d.x) > abs(d.y)
                    if (wasSelecting && sideways || !wasSelecting && selecting()) {
                        change.consume()
                        break
                    }
                    return@awaitEachGesture
                }
                val before = picked()
                val beforeKeys = before.mapTo(HashSet(before.size)) { it.key }
                val adding = !(wasSelecting && first.key in beforeKeys)
                var cachedOrder: List<Photo>? = null
                var indexByKey: Map<String, Int> = emptyMap()
                var last: Photo? = null
                var at = down.position

                fun ensureIndex(all: List<Photo>): Map<String, Int> {
                    if (cachedOrder === all) return indexByKey
                    cachedOrder = all
                    val map = HashMap<String, Int>(all.size)
                    all.forEachIndexed { idx, p -> map[p.key] = idx }
                    indexByKey = map
                    return map
                }

                fun update() {
                    val now = hit(at) ?: return
                    if (now.key == last?.key) return
                    val all = order()
                    val map = ensureIndex(all)
                    val i = map[first.key] ?: return
                    val j = map[now.key] ?: return
                    last = now
                    val lo = minOf(i, j)
                    val hi = maxOf(i, j)
                    if (adding) {
                        val out = ArrayList<Photo>(before.size + (hi - lo + 1))
                        out.addAll(before)
                        for (k in lo..hi) {
                            val p = all[k]
                            if (p.key !in beforeKeys) out.add(p)
                        }
                        onPick(out)
                    } else {
                        val removeKeys = HashSet<String>(hi - lo + 1)
                        for (k in lo..hi) removeKeys.add(all[k].key)
                        onPick(before.filterNot { it.key in removeKeys })
                    }
                }
                recheck = ::update
                try {
                    update()
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        at = change.position
                        edge.floatValue = when {
                            zone <= 0f -> 0f
                            at.y < zone -> -((zone - at.y) / zone).coerceIn(0f, 1f)
                            at.y > size.height - zone -> ((at.y - (size.height - zone)) / zone).coerceIn(0f, 1f)
                            else -> 0f
                        }
                        update()
                    }
                } finally {
                    edge.floatValue = 0f
                    recheck = null
                }
            }
        }
}

/** Set by a screen that lets a finger drag across its photos; tiles report where they are. */
val LocalDragSelect = compositionLocalOf<DragSelect?> { null }
