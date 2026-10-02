package com.njarrar.backthen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.njarrar.backthen.core.MONTHS
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.localDate

private val Ink = Color(0xFFF4F4F2)

/** Full-screen photo viewer: swipe between photos, heart, share, add to album. */
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
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), key = { photos[it].key }) { i ->
            AsyncImage(photos[i].uri, "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
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
