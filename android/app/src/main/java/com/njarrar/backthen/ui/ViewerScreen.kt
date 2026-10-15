package com.njarrar.backthen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.njarrar.backthen.core.MONTHS
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.localDate

private val Ink = Color(0xFFF4F4F2)

/** Full-screen photo viewer: swipe between photos, pinch or double-tap to zoom, heart, share, add to album. */
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
    var zoomed by remember { mutableStateOf(false) }
    val photo = photos.getOrNull(pager.currentPage) ?: return
    val date = remember(photo.time) { localDate(photo.time) }
    val fav = photo.key in favorites

    Column(Modifier.fillMaxSize().background(Color(0xFF0D0D0E)).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                Icon(Icons.Filled.Close, "Close", tint = Ink)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                T("${MONTHS[date.monthValue - 1]} ${date.dayOfMonth}, ${date.year}", size = 15, weight = FontWeight.SemiBold, color = Ink)
                T("${agoLabel(date.year)}  ·  ${pager.currentPage + 1} of ${photos.size}", size = 13, color = Ink.copy(alpha = 0.62f))
            }
            Spacer(Modifier.size(44.dp))
        }
        // While zoomed in, a drag moves the photo instead of turning the page.
        HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.weight(1f), key = { photos[it].key }) { i ->
            ZoomablePhoto(photos[i].uri, current = i == pager.currentPage, onZoom = { if (i == pager.currentPage) zoomed = it })
        }
        if (photos.size in 2..20) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
                photos.indices.forEach { i ->
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (i == pager.currentPage) Ink else Ink.copy(alpha = 0.3f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            IconButton(onClick = { onFavorite(photo) }, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    if (fav) "Remove from favorites" else "Favorite",
                    tint = if (fav) Color(0xFFFF6A52) else Ink,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = { onShare(photo) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.IosShare, "Share", tint = Ink, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { onAlbum(photo) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.AddBox, "Add to album", tint = Ink, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** One photo that zooms with a pinch (up to 4x) or a double tap, and pans while zoomed. */
@Composable
private fun ZoomablePhoto(uri: String, current: Boolean, onZoom: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    fun reset() {
        scale = 1f
        offset = Offset.Zero
        onZoom(false)
    }
    // Swiping away resets the zoom, so the photo looks normal when you come back to it.
    LaunchedEffect(current) { if (!current && scale != 1f) reset() }

    AsyncImage(
        uri, "Photo", contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { if (scale > 1f) reset() else { scale = 2.5f; onZoom(true) } })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // One finger at normal size is left alone, so the pager can still turn pages.
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                            val maxX = size.width * (scale - 1f) / 2f
                            val maxY = size.height * (scale - 1f) / 2f
                            val pan = event.calculatePan()
                            offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < 1.05f) reset() else onZoom(true)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
