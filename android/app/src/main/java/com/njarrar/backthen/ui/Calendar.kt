package com.njarrar.backthen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.njarrar.backthen.core.DAYS_IN_MONTH
import com.njarrar.backthen.core.DayIndex
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.dayLevels
import com.njarrar.backthen.core.label
import com.njarrar.backthen.core.monthName
import com.njarrar.backthen.core.photoCount
import com.njarrar.backthen.core.shortLabel
import com.njarrar.backthen.core.yearsForDay
import kotlinx.coroutines.launch

/**
 * Android's centered date dialog. Picks a month and day, not a year, since every year
 * shows at once. Each day is shaded by how many photos it has. Days after today can't be picked.
 */
@Composable
fun CalendarDialog(start: MonthDay, index: DayIndex, onPick: (MonthDay) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var chosen by remember { mutableStateOf(start) }
    val today = MonthDay.today()
    // One page per month, opening on the chosen month. Swipe or use the arrows.
    val levels = remember(index) { dayLevels(index) }
    val pager = rememberPagerState(initialPage = start.month) { 12 }
    val scope = rememberCoroutineScope()
    val month = pager.currentPage
    val view = LocalView.current
    var shown by remember { mutableStateOf(month) }
    LaunchedEffect(month) { if (month != shown) { shown = month; Haptics.tick(view) } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.surface2).padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                T(L.s("pick_a_day"), size = 14, weight = FontWeight.Medium, muted = true)
                T(chosen.shortLabel(), size = 32, weight = FontWeight.Bold, tight = true)
            }
            HorizontalDivider(color = p.line)
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    T(monthName(month), size = 24, weight = FontWeight.ExtraBold, tight = true, modifier = Modifier.semantics { heading() })
                    T(L.s("calendar_hint"), size = 13, muted = true)
                }
                if (month != today.month || chosen != today) {
                    Box(
                        Modifier.height(38.dp).clip(RoundedCornerShape(50)).clickable(role = Role.Button) {
                            chosen = today
                            scope.launch { pager.animateScrollToPage(today.month) }
                        }.padding(horizontal = 8.dp),
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
                                Day(md, index, levels[md.key] ?: 0, chosen == md, today == md, md.isAfter(today), Modifier.weight(1f)) { chosen = md }
                            }
                            repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Legend()
            Row(Modifier.align(Alignment.End)) {
                Pill(L.s("cancel"), onDismiss, kind = Kind.Ghost, height = 40.dp)
                Pill(L.s("ok"), { onPick(chosen) }, kind = Kind.Ghost, height = 40.dp)
            }
        }
    }
}

/** Fill and number color for a day at fullness [level], 0 (no photos) to 5. */
private fun shade(p: Palette, level: Int): Pair<Color, Color> = when (level) {
    0 -> Color.Transparent to p.faint
    1 -> p.cal1 to p.cal1Ink
    2 -> p.cal2 to p.cal2Ink
    3 -> p.cal3 to p.cal3Ink
    4 -> p.cal4 to p.cal4Ink
    else -> p.cal5 to p.cal5Ink
}

@Composable
private fun Day(
    md: MonthDay, index: DayIndex, level: Int, selected: Boolean, isToday: Boolean, future: Boolean,
    modifier: Modifier, onClick: () -> Unit,
) {
    val p = LocalPalette.current
    // Days still to come this year stay plain and can't be tapped.
    val (fill, ink) = when {
        selected -> Accent to Color.White
        future -> Color.Transparent to p.faint.copy(alpha = 0.5f)
        else -> shade(p, level)
    }
    Box(modifier.height(46.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(fill)
                .then(if (isToday && !selected) Modifier.border(1.5.dp, p.ink, CircleShape) else Modifier)
                .clickable(enabled = !future, onClick = onClick)
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
            Text(
                L.num(md.day), color = ink,
                style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFeatureSettings = "tnum"),
            )
        }
    }
}

/** Fewer ○○○○● More, so the shades read without the hint. */
@Composable
private fun Legend() {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(L.s("calendar_fewer"), size = 12, weight = FontWeight.SemiBold, muted = true)
        for (level in 1..5) Box(Modifier.size(12.dp).clip(CircleShape).background(shade(p, level).first))
        T(L.s("calendar_more"), size = 12, weight = FontWeight.SemiBold, muted = true)
    }
}

@Composable
private fun Arrow(left: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(38.dp).clip(CircleShape).background(p.fill).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(
            if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            L.s(if (left) "previous_month" else "next_month"),
            tint = p.ink,
        )
    }
}
