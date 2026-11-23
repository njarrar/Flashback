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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
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
 * year shows at once. Each day with photos shows a tiny copy of one of them. Past-year days
 * after today that have photos are unlocked and tappable; empty upcoming days are disabled.
 */
@Composable
fun CalendarSheet(
    start: MonthDay,
    index: DayIndex,
    coversForMonth: suspend (Int) -> Map<String, Photo?>,
    hiddenDates: Set<String> = emptySet(),
    onPick: (MonthDay) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val today = MonthDay.today()
    val pager = rememberPagerState(initialPage = start.month) { 12 }
    val scope = rememberCoroutineScope()
    val month = pager.currentPage
    val view = LocalView.current
    var shown by remember { mutableStateOf(month) }
    var monthMenu by remember { mutableStateOf(false) }
    LaunchedEffect(month) { if (month != shown) { shown = month; Haptics.tick(view) } }

    fun jumpTo(target: Int) {
        scope.launch {
            val wrap = (month == 0 && target == 11) || (month == 11 && target == 0)
            if (wrap || reduce) pager.scrollToPage(target) else pager.animateScrollToPage(target)
        }
    }

    BottomSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { monthMenu = true }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .clearAndSetSemantics {
                            heading()
                            contentDescription = monthName(month)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    T(monthName(month), size = 24, weight = FontWeight.ExtraBold, tight = true, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Filled.ArrowDropDown, null, tint = Accent, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }, containerColor = p.surface) {
                    (0..11).forEach { m ->
                        val on = m == month
                        DropdownMenuItem(
                            text = { T(monthName(m), weight = if (on) FontWeight.Bold else FontWeight.Medium) },
                            trailingIcon = { if (on) Icon(Icons.Filled.Check, null, tint = Accent, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                monthMenu = false
                                scope.launch { pager.scrollToPage(m) }
                            },
                        )
                    }
                }
            }
            if (month != today.month || start != today) {
                Box(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button) { onPick(today) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(p.fill).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        T(L.s("today"), size = 15, weight = FontWeight.SemiBold)
                    }
                }
            }
            Arrow(left = true) { jumpTo((month + 11) % 12) }
            Arrow(left = false) { jumpTo((month + 1) % 12) }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { m ->
                val monthHasAny = remember(m, index) {
                    (1..DAYS_IN_MONTH[m]).any { d -> hasPhotos(index, MonthDay(m, d)) }
                }
                val covers by produceState<Map<String, Photo?>>(emptyMap(), m, index, monthHasAny) {
                    value = if (monthHasAny) coversForMonth(m) else emptyMap()
                }
                Column {
                    (1..DAYS_IN_MONTH[m]).chunked(7).forEach { week ->
                        Row {
                            for (d in week) {
                                val md = MonthDay(m, d)
                                val future = md.isAfter(today)
                                Day(
                                    md = md,
                                    index = index,
                                    photo = if (future) null else covers[md.key],
                                    selected = start == md,
                                    isToday = today == md,
                                    future = future,
                                    hidden = md.key in hiddenDates,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onPick(md) },
                                )
                            }
                            repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** One day: a tiny copy of one of its photos, dimmed, with the number on top, or a plain number. */
@Composable
private fun Day(
    md: MonthDay,
    index: DayIndex,
    photo: Photo?,
    selected: Boolean,
    isToday: Boolean,
    future: Boolean,
    hidden: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    // Days after today stay locked and faint, as in 1.7.
    val photos = !future && hasPhotos(index, md)
    val upcomingEmpty = future
    val enabled = !upcomingEmpty
    val shape = RoundedCornerShape(11.dp)
    val request = rememberThumbnailRequest(photo?.uri, maxPx = 160, crossfade = false)

    val dayDesc = remember(md, index, isToday, upcomingEmpty, hidden) {
        val base = when {
            upcomingEmpty -> L.s("day_upcoming", "day" to md.label())
            else -> {
                val years = yearsForDay(index, md).size
                if (years == 0) L.s("day_no_photos", "day" to md.label())
                else L.s(
                    "day_photos_from",
                    "day" to md.label(),
                    "photos" to L.p("photos", photoCount(index, md)),
                    "years" to L.p("years", years),
                )
            }
        }
        buildString {
            append(base)
            if (isToday) append(", ").append(L.s("today"))
            if (hidden) append(", ").append(L.s("you_hid_date"))
        }
    }

    Box(
        modifier
            .height(54.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = dayDesc
                this.selected = selected
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(48.dp).border(2.5.dp, Accent, RoundedCornerShape(14.dp)))
        Box(
            Modifier
                .size(42.dp)
                .clip(shape)
                .then(if (photos) Modifier.background(p.fill) else Modifier)
                .then(if (isToday && !selected) Modifier.border(1.5.dp, p.ink, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (request != null) {
                AsyncImage(request, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(DayWash))
            }
            val onPhoto = photo != null
            val textColor = when {
                onPhoto -> Color.White
                upcomingEmpty -> p.muted.copy(alpha = 0.65f)
                photos -> p.ink
                else -> p.muted2
            }
            BasicText(
                text = L.num(md.day),
                maxLines = 1,
                style = TextStyle(
                    color = textColor,
                    fontFamily = Nunito,
                    fontWeight = if (photos) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp, stepSize = 0.5.sp),
            )
            if (hidden) {
                Icon(
                    Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = if (onPhoto) Color.White else p.muted,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(11.dp),
                )
            }
        }
    }
}

@Composable
private fun Arrow(left: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(p.fill)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            L.s(if (left) "previous_month" else "next_month"),
            tint = p.ink,
        )
    }
}
