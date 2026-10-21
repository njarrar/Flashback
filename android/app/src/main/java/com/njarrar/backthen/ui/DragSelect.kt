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
import androidx.compose.ui.layout.boundsInRoot
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
 * scrolls on its own, by [edge] pixels a frame.
 */
class DragSelect {
    private val tiles = mutableMapOf<String, Pair<Photo, Rect>>()
    private var origin = Offset.Zero
    val edge = mutableFloatStateOf(0f)
    /** Checks the photo under the finger again, after the list scrolled under it. */
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
                val adding = !(wasSelecting && before.any { it.key == first.key })
                var last: Photo? = null
                var at = down.position
                fun update() {
                    val now = hit(at) ?: return
                    if (now.key == last?.key) return
                    last = now
                    val all = order()
                    val i = all.indexOfFirst { it.key == first.key }
                    val j = all.indexOfFirst { it.key == now.key }
                    if (i < 0 || j < 0) return
                    val range = all.subList(minOf(i, j), maxOf(i, j) + 1)
                    val keys = range.map { it.key }.toSet()
                    onPick(if (adding) before + range.filter { r -> before.none { it.key == r.key } } else before.filterNot { it.key in keys })
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
                            at.y < zone -> -(zone - at.y) / 4
                            at.y > size.height - zone -> (at.y - size.height + zone) / 4
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
