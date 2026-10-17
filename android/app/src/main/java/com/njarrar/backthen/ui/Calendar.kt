package com.njarrar.backthen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.njarrar.backthen.core.DAYS_IN_MONTH
import com.njarrar.backthen.core.DayIndex
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.hasPhotos
import com.njarrar.backthen.core.label
import com.njarrar.backthen.core.monthName
import com.njarrar.backthen.core.photoCount
import com.njarrar.backthen.core.yearsForDay
import kotlinx.coroutines.launch

/**
 * Calendar bottom sheet, like the iPhone one. Picks a month and day, not a year, since every
 * year shows at once. Each day with photos shows a tiny copy of one of them. One tap on a day
 * opens it; days after today can't be picked.
 */
@Composable
fun CalendarSheet(start: MonthDay, index: DayIndex, cover: suspend (MonthDay) -> Photo?, onPick: (MonthDay) -> Unit, onDismiss: () -> Unit) {
    val today = MonthDay.today()
    // One page per month, opening on the chosen month. Swipe or use the arrows.
    val pager = rememberPagerState(initialPage = start.month) { 12 }
    val scope = rememberCoroutineScope()
    val month = pager.currentPage
    val view = LocalView.current
    var shown by remember { mutableStateOf(month) }
    LaunchedEffect(month) { if (month != shown) { shown = month; Haptics.tick(view) } }
    BottomSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T(monthName(month), size = 24, weight = FontWeight.ExtraBold, tight = true, modifier = Modifier.weight(1f).semantics { heading() })
            if (month != today.month || start != today) {
                Box(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button) { onPick(today) }.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { T(L.s("today"), size = 15, weight = FontWeight.SemiBold) }
            }
            Arrow(left = true) { scope.launch { pager.animateScrollToPage((month + 11) % 12) } }
            Arrow(left = false) { scope.launch { pager.animateScrollToPage((month + 1) % 12) } }
        }
        HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { m ->
            Column {
                (1..DAYS_IN_MONTH[m]).chunked(7).forEach { week ->
                    Row {
                        for (d in week) {
                            val md = MonthDay(m, d)
                            Day(md, index, cover, start == md, today == md, md.isAfter(today), Modifier.weight(1f)) { onPick(md) }
                        }
                        repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** One day: a tiny copy of one of its photos with the number on top, or a plain number. */
@Composable
private fun Day(
    md: MonthDay, index: DayIndex, cover: suspend (MonthDay) -> Photo?, selected: Boolean, isToday: Boolean, future: Boolean,
    modifier: Modifier, onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val photos = hasPhotos(index, md)
    val photo by produceState<Photo?>(null, md, index) { value = if (photos) cover(md) else null }
    val shape = RoundedCornerShape(11.dp)
    // The whole cell reacts, not only the square in the middle.
    Box(
        modifier.height(52.dp)
            .clickable(enabled = !future, role = Role.Button, onClick = onClick)
            .semantics {
                // "October 16, 12 photos from 3 years", or "October 16, no photos".
                val years = yearsForDay(index, md).size
                contentDescription = if (years == 0) L.s("day_no_photos", "day" to md.label())
                else L.s(
                    "day_photos_from", "day" to md.label(),
                    "photos" to L.p("photos", photoCount(index, md)), "years" to L.p("years", years),
                )
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        // The picked day gets a coral ring, set off from the photo by a thin gap. Today gets a thin ink ring.
        if (selected) Box(Modifier.size(46.dp).border(2.5.dp, Accent, RoundedCornerShape(14.dp)))
        Box(
            Modifier.size(38.dp).clip(shape)
                .then(if (photos) Modifier.background(p.fill) else Modifier)
                .then(if (isToday && !selected) Modifier.border(1.5.dp, p.ink, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            photo?.let { AsyncImage(it.uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            val onPhoto = photo != null
            Text(
                L.num(md.day),
                color = when {
                    onPhoto -> Color.White
                    future -> p.faint.copy(alpha = 0.5f)
                    photos -> p.ink
                    // Past days with no photos: muted, so "nothing here" reads apart from "not yet".
                    else -> p.muted
                },
                style = TextStyle(
                    fontFamily = Nunito, fontWeight = if (photos) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 16.sp, fontFeatureSettings = "tnum",
                    shadow = if (onPhoto) Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 4f) else null,
                ),
            )
        }
    }
}

@Composable
private fun Arrow(left: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(48.dp).clip(CircleShape).background(p.fill).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(
            if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            L.s(if (left) "previous_month" else "next_month"),
            tint = p.ink,
        )
    }
}
