package com.njarrar.backthen.ui

import android.app.Activity
import android.content.ContextWrapper
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.formatDate
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.weekdayTime
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/** Double tap goes to 2.5x at the tap, or back to fit from any zoom. */
private val ToggleZoom = object : DoubleClickToZoomListener {
    override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
        if ((state.zoomFraction ?: 0f) > 0.01f) state.resetZoom() else state.zoomTo(2.5f, centroid)
    }
}

/**
 * Full-screen photo viewer: swipe between photos, pinch or double tap to zoom, swipe down
 * to close, tap to hide the controls. Heart, share and add to album at the bottom.
 */
@Composable
fun ViewerScreen(
    photos: List<Photo>,
    start: Int,
    favorites: Set<String>,
    onFavorite: (Photo) -> Unit,
    onShare: (Photo) -> Unit,
    onAlbum: (Photo) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = start) { photos.size }
    val photo = photos.getOrNull(pager.currentPage) ?: return
    val date = localDate(photo.time)
    val use24 = DateFormat.is24HourFormat(LocalContext.current)
    val fav = photo.key in favorites
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    // How far the photo has been pulled down, in pixels.
    val drag = remember { Animatable(0f) }
    val fullPull = with(density) { 300.dp.toPx() }
    val closeAt = with(density) { 120.dp.toPx() }
    val flick = with(density) { 800.dp.toPx() }
    val pull = (drag.value.coerceAtLeast(0f) / fullPull).coerceIn(0f, 1f)
    val view = LocalView.current
    // Light status bar icons on the black viewer, back to what they were when it closes.
    DisposableEffect(view) {
        var c = view.context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val bars = (c as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val was = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = false
        onDispose { if (bars != null && was != null) bars.isAppearanceLightStatusBars = was }
    }
    var shown by remember { mutableStateOf(pager.currentPage) }
    LaunchedEffect(pager.currentPage) {
        drag.snapTo(0f)
        if (pager.currentPage != shown) { shown = pager.currentPage; Haptics.tick(view) }
    }
    val chromeAlpha by animateFloatAsState(if (chrome) 1f else 0f, tween(200), label = "chrome")

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(ViewerBg.copy(alpha = 1f - pull)))
        HorizontalPager(
            state = pager,
            key = { photos[it].key },
            userScrollEnabled = !zoomed && drag.value == 0f,
            modifier = Modifier.fillMaxSize().draggable(
                orientation = Orientation.Vertical,
                enabled = !zoomed,
                state = rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } },
                onDragStopped = { velocity ->
                    if (drag.value > 0f && (drag.value > closeAt || velocity > flick)) onClose()
                    else drag.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                },
            ),
        ) { i ->
            val current = i == pager.currentPage
            val zoom = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 4f)))
            val fraction = zoom.zoomableState.zoomFraction ?: 0f
            LaunchedEffect(current, fraction) { if (current) zoomed = fraction > 0.01f }
            // A page that is swiped away goes back to fit.
            LaunchedEffect(current) { if (!current) zoom.zoomableState.resetZoom(snap()) }
            ZoomableAsyncImage(
                model = photos[i].uri,
                contentDescription = L.s("photo_from", "date" to formatDate(localDate(photos[i].time), "yMMMMd")),
                state = zoom,
                contentScale = ContentScale.Fit,
                onClick = { chrome = !chrome },
                onDoubleClick = ToggleZoom,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    if (current) {
                        translationY = drag.value
                        val s = 1f - 0.15f * (drag.value.coerceAtLeast(0f) / fullPull).coerceIn(0f, 1f)
                        scaleX = s
                        scaleY = s
                    }
                },
            )
        }

        if (chromeAlpha > 0.01f) {
            Column(Modifier.fillMaxSize().systemBarsPadding().graphicsLayer { alpha = chromeAlpha * (1f - pull) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, enabled = chrome, modifier = Modifier.size(44.dp).clip(CircleShape).background(ViewerInk.copy(alpha = 0.14f))) {
                        Icon(Icons.Filled.Close, L.s("close"), tint = ViewerInk)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        T(formatDate(date, "yMMMMd"), size = 16, weight = FontWeight.Bold, color = ViewerInk)
                        var sub = L.s("ago_weekday", "ago" to agoLabel(date.year), "weekday" to weekdayTime(photo.time, use24))
                        // Where you are in the set: "3 of 5".
                        if (photos.size > 1) sub += " · " + L.s("n_of_total", "n" to pager.currentPage + 1, "total" to photos.size)
                        T(sub, size = 13, color = ViewerInk.copy(alpha = 0.62f))
                    }
                    Spacer(Modifier.size(44.dp))
                }
                Spacer(Modifier.weight(1f))
                if (photos.size > 1) {
                    Filmstrip(photos, pager.currentPage, enabled = chrome) { i -> scope.launch { pager.scrollToPage(i) } }
                    Spacer(Modifier.height(24.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    Action(
                        if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, L.s("favorite_short"), L.s(if (fav) "unfavorite" else "favorite"),
                        tint = if (fav) ViewerHeart else ViewerInk, enabled = chrome,
                    ) { if (!fav) Haptics.confirm(view); onFavorite(photo) }
                    Action(Icons.Outlined.IosShare, L.s("share"), L.s("share"), enabled = chrome) { onShare(photo) }
                    Action(Icons.Outlined.LibraryAdd, L.s("add_to_album"), L.s("add_to_album"), enabled = chrome) { onAlbum(photo) }
                }
            }
        }
    }
}

/** An icon with a short word under it. */
@Composable
private fun Action(icon: ImageVector, word: String, label: String, tint: Color = ViewerInk, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 72.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        T(word, size = 11, weight = FontWeight.SemiBold, color = ViewerInk.copy(alpha = 0.75f), maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** Small thumbnails of every photo, with the current one wider and outlined. */
@Composable
private fun Filmstrip(photos: List<Photo>, index: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    val strip = rememberLazyListState(initialFirstVisibleItemIndex = index)
    LaunchedEffect(index) { strip.animateScrollToItem(index) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(52.dp)) {
        // Padding on both sides so the current photo, scrolled to the start, sits in the middle.
        val side = (maxWidth / 2 - 34.dp).coerceAtLeast(0.dp)
        LazyRow(
            state = strip,
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(photos, key = { _, p -> p.key }) { i, p ->
                val on = i == index
                AsyncImage(
                    p.uri, null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(horizontal = if (on) 4.dp else 0.dp)
                        .size(if (on) 60.dp else 42.dp, 44.dp)
                        .drawWithContent {
                            drawContent()
                            if (on) {
                                // 2 dp outline, 1 dp outside the photo.
                                val o = 2.dp.toPx()
                                drawRoundRect(
                                    ViewerInk, topLeft = Offset(-o, -o), size = Size(size.width + 2 * o, size.height + 2 * o),
                                    cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()),
                                )
                            }
                        }
                        .graphicsLayer { alpha = if (on) 1f else 0.7f }
                        .clickable(enabled = enabled) { onPick(i) }
                        .semantics {
                            contentDescription = L.s("photo_n_of", "n" to i + 1, "total" to photos.size)
                            selected = on
                        },
                )
            }
        }
    }
}
