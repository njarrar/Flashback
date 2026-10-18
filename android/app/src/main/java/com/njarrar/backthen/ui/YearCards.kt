package com.njarrar.backthen.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.YearGroup
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.weekdayOf

/**
 * Today's years as a deck of cards, one per year, newest first. Swipe up and down to move
 * between years; sideways still moves between days. Tap a card to see that year's photos.
 */
@Composable
fun YearCards(
    groups: List<YearGroup>,
    day: MonthDay,
    favorites: Set<String>,
    selection: Selection,
    onOpen: (List<Photo>, Int) -> Unit,
    onShare: (Int, List<Photo>) -> Unit,
) {
    val p = LocalPalette.current
    val height = (LocalConfiguration.current.screenHeightDp * 0.6f).coerceAtLeast(360f).dp
    val pager = rememberPagerState { groups.size }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().height(height),
            contentPadding = PaddingValues(vertical = 6.dp),
            pageSpacing = 12.dp,
            key = { groups[it].year },
        ) { i ->
            val (year, photos) = groups[i]
            YearCard(year, photos, day, favorites, yearSelection(photos, selection), onOpen = { at -> onOpen(photos, at) }, onShare = { onShare(year, photos) })
        }
        if (groups.size > 1) {
            Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.indices.forEach { i ->
                    val on = i == pager.currentPage
                    val w by animateDpAsState(if (on) 18.dp else 6.dp, label = "dot")
                    Box(Modifier.width(w).height(6.dp).clip(CircleShape).background(if (on) p.ink else p.faint))
                }
            }
        }
    }
}

/** A card picks or unpicks its whole year at once. */
private fun yearSelection(photos: List<Photo>, base: Selection): Selection {
    val all = photos.all { it.key in base.keys }
    return Selection(
        on = base.on,
        keys = if (all) photos.map { it.key }.toSet() else emptySet(),
        start = base.start?.let { start ->
            { _: Photo ->
                start(photos[0])
                photos.drop(1).forEach(base.toggle)
            }
        },
        toggle = { _ -> photos.filter { (it.key in base.keys) == all }.forEach(base.toggle) },
    )
}

/**
 * One year: a favorite from that year (or its first photo) filling the card, with the year
 * and "6 years ago · Friday, 4 photos" over a dark wash at the bottom.
 */
@Composable
private fun YearCard(
    year: Int,
    photos: List<Photo>,
    day: MonthDay,
    favorites: Set<String>,
    selection: Selection,
    onOpen: (Int) -> Unit,
    onShare: () -> Unit,
) {
    val cover = photos.indexOfFirst { it.key in favorites }.coerceAtLeast(0)
    val facts = L.s("ago_weekday_photos", "ago" to agoLabel(year), "weekday" to weekdayOf(year, day), "photos" to L.p("photos", photos.size))
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PhotoTile(photos[cover], 24.dp, favorite = false, selection = selection, onOpen = { onOpen(cover) }, modifier = Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Brush.verticalGradient(listOf(Color.Transparent, CardScrim)))
                .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 20.dp)
                .clearAndSetSemantics {},
        ) {
            T(L.num(year), size = 52, weight = FontWeight.Black, color = ChipInk, tight = true)
            T(facts, size = 15, weight = FontWeight.SemiBold, color = ChipInk)
        }
        if (!selection.on) {
            IconButton(onClick = onShare, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Chip), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.IosShare, L.s("share_year", "year" to year), tint = ChipInk, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
