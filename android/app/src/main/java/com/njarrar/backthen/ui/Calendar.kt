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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.njarrar.backthen.core.DAYS_IN_MONTH
import com.njarrar.backthen.core.DayIndex
import com.njarrar.backthen.core.MONTHS
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.hasPhotos
import com.njarrar.backthen.core.shortLabel

/**
 * Android's centered date dialog from the design. Picks a month and day, not a year,
 * since every year shows at once. Dots mark days with photos.
 */
@Composable
fun CalendarDialog(start: MonthDay, index: DayIndex, onPick: (MonthDay) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var month by remember { mutableIntStateOf(start.month) }
    var chosen by remember { mutableStateOf(start) }
    val today = MonthDay.today()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.surface2).padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                T("Pick a day", size = 14, weight = FontWeight.Medium, muted = true)
                T(chosen.shortLabel(), size = 32, weight = FontWeight.Bold, tight = true)
            }
            HorizontalDivider(color = p.line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Arrow(left = true) { month = (month + 11) % 12 }
                T(MONTHS[month], size = 16, weight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Arrow(left = false) { month = (month + 1) % 12 }
            }
            (1..DAYS_IN_MONTH[month]).chunked(7).forEach { week ->
                Row {
                    for (d in week) {
                        val md = MonthDay(month, d)
                        val has = hasPhotos(index, md)
                        val sel = chosen == md
                        val isToday = today == md
                        Column(
                            Modifier.weight(1f).padding(2.dp).height(46.dp).clip(CircleShape)
                                .background(if (sel) Accent else Color.Transparent)
                                .then(if (isToday && !sel) Modifier.border(1.5.dp, p.ink, CircleShape) else Modifier)
                                .clickable { chosen = md }
                                .semantics {
                                    contentDescription = "${MONTHS[month]} $d" + if (has) ", has photos" else ""
                                    selected = sel
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            T(d.toString(), weight = FontWeight.SemiBold, color = if (sel) Color.White else if (has) p.ink else p.faint)
                            Dot(if (has) (if (sel) Color.White else Accent) else Color.Transparent)
                        }
                    }
                    repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dot(Accent)
                    T("Has photos", size = 13, muted = true)
                }
                Pill("Cancel", onDismiss, kind = Kind.Ghost, height = 40.dp)
                Pill("OK", { onPick(chosen) }, kind = Kind.Ghost, height = 40.dp)
            }
        }
    }
}

@Composable
private fun Arrow(left: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.size(44.dp).clip(CircleShape).background(p.fill).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(
            if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            if (left) "Previous month" else "Next month",
            tint = p.ink,
        )
    }
}
