package com.njarrar.backthen.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.YearGroup
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.weekdayOf
import kotlinx.coroutines.launch

/**
 * Today's years as a deck of cards, one per year, newest first. Swipe up and down to move
 * between years; sideways still moves between days. Tap a card to see that year's photos.
 * Sized directly from [BoxWithConstraints] without nesting inside a vertical LazyColumn.
 */
@Composable
fun YearCards(
    groups: List<YearGroup>,
    day: MonthDay,
    favorites: Set<String>,
    selection: Selection,
    onOpen: (List<Photo>, Int) -> Unit,
    onShare: (Int, List<Photo>) -> Unit,
    modifier: Modifier = Modifier,
    pager: PagerState = rememberPagerState { groups.size },
) {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val indicatorSpace = if (groups.size > 1) 42.dp else 12.dp
        val pagerHeight = (maxHeight - indicatorSpace).coerceIn(280.dp, 640.dp)
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height(pagerHeight),
                contentPadding = PaddingValues(vertical = 6.dp),
                pageSpacing = 12.dp,
                key = { groups.getOrNull(it)?.year ?: it },
            ) { i ->
                val group = groups.getOrNull(i) ?: return@VerticalPager
                val (year, photos) = group
                val prevYear = groups.getOrNull(i - 1)?.year
                val nextYear = groups.getOrNull(i + 1)?.year
                val actions = buildList {
                    if (prevYear != null) {
                        add(CustomAccessibilityAction(L.num(prevYear)) {
                            scope.launch { if (reduce) pager.scrollToPage(i - 1) else pager.animateScrollToPage(i - 1) }
                            true
                        })
                    }
                    if (nextYear != null) {
                        add(CustomAccessibilityAction(L.num(nextYear)) {
                            scope.launch { if (reduce) pager.scrollToPage(i + 1) else pager.animateScrollToPage(i + 1) }
                            true
                        })
                    }
                }
                val pickedCount = photos.count { it.key in selection.keys }
                YearCard(
                    year = year,
                    photos = photos,
                    day = day,
                    favorites = favorites,
                    selection = yearSelection(photos, selection),
                    pickedCount = pickedCount,
                    pageIndex = i,
                    pageCount = groups.size,
                    pagerActions = actions,
                    onOpen = { at -> onOpen(photos, at) },
                    onShare = { onShare(year, photos) },
                )
            }
            if (groups.size > 7) {
                val cur = groups.getOrNull(pager.currentPage)
                if (cur != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(p.fill)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clearAndSetSemantics {},
                    ) {
                        T(
                            "${L.num(cur.year)} · ${ltrIsolate("${L.num(pager.currentPage + 1)} / ${L.num(groups.size)}")}",
                            size = 13,
                            weight = FontWeight.SemiBold,
                            color = p.muted2,
                            maxLines = 1,
                        )
                    }
                }
            } else if (groups.size > 1) {
                Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.indices.forEach { i ->
                        val on = i == pager.currentPage
                        val w by animateDpAsState(
                            targetValue = if (on) 18.dp else 6.dp,
                            animationSpec = if (reduce) snap() else tween(180),
                            label = "dot",
                        )
                        Box(Modifier.width(w).height(6.dp).clip(CircleShape).background(if (on) p.ink else p.faint))
                    }
                }
            }
        }
    }
}

/** A card picks or unpicks its whole year at once using a single batch update & haptic. */
private fun yearSelection(photos: List<Photo>, base: Selection): Selection {
    val all = photos.all { it.key in base.keys }
    val batch = base.toggleAll
    return Selection(
        on = base.on,
        keys = if (all) photos.map { it.key }.toSet() else emptySet(),
        start = base.start?.let { start ->
            { _: Photo ->
                if (batch != null) {
                    start(photos[0])
                    if (photos.size > 1) batch(photos, true)
                } else {
                    start(photos[0])
                    photos.drop(1).forEach(base.toggle)
                }
            }
        },
        toggle = { _ ->
            if (batch != null) {
                batch(photos, !all)
            } else {
                photos.filter { (it.key in base.keys) == all }.forEach(base.toggle)
            }
        },
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
    pickedCount: Int,
    pageIndex: Int,
    pageCount: Int,
    pagerActions: List<CustomAccessibilityAction>,
    onOpen: (Int) -> Unit,
    onShare: () -> Unit,
) {
    val cover = photos.indexOfFirst { it.key in favorites }.coerceAtLeast(0)
    val facts = L.s("ago_weekday_photos", "ago" to agoLabel(year), "weekday" to weekdayOf(year, day), "photos" to L.p("photos", photos.size))
    val a11yLabel = "${L.num(year)}, $facts"
    val a11yState = if (pageCount > 1) L.s("n_of_total", "n" to pageIndex + 1, "total" to pageCount) else null
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PhotoTile(
            photo = photos[cover],
            radius = 24.dp,
            favorite = false,
            selection = selection,
            onOpen = { onOpen(cover) },
            thumbPx = 1024,
            semanticsLabel = a11yLabel,
            semanticsState = a11yState,
            customActionsList = pagerActions,
            modifier = Modifier.fillMaxSize(),
        )
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
        if (selection.on && pickedCount > 0) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (pickedCount == photos.size) AccentFill else Chip)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clearAndSetSemantics {},
            ) {
                T(
                    ltrIsolate("${L.num(pickedCount)} / ${L.num(photos.size)}"),
                    size = 12,
                    weight = FontWeight.Bold,
                    color = ChipInk,
                    maxLines = 1,
                )
            }
        }
        if (!selection.on) {
            IconButton(onClick = onShare, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Chip), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Share, L.s("share_year", "year" to year), tint = ChipInk, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
