package com.njarrar.backthen.core

import java.time.LocalDate
import java.time.ZoneId

/** A calendar day without a year. [month] is 0-based, to match the design's logic. */
data class MonthDay(val month: Int, val day: Int) {
    val key: String get() = "$month-$day"

    companion object {
        fun of(date: LocalDate) = MonthDay(date.monthValue - 1, date.dayOfMonth)
        fun today() = of(LocalDate.now())
    }
}

val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
val MONTHS_SHORT = MONTHS.map { it.take(3) }

// February has 29 so Feb 29 can always be picked.
val DAYS_IN_MONTH = listOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

/** Moves a month and day forward or back, wrapping around the year. */
fun MonthDay.shift(delta: Int): MonthDay {
    var m = month
    var d = day + delta
    while (d < 1) {
        m = (m + 11) % 12
        d += DAYS_IN_MONTH[m]
    }
    while (d > DAYS_IN_MONTH[m]) {
        d -= DAYS_IN_MONTH[m]
        m = (m + 1) % 12
    }
    return MonthDay(m, d)
}

fun MonthDay.label() = "${MONTHS[month]} $day"
fun MonthDay.shortLabel() = "${MONTHS_SHORT[month]} $day"

fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

fun agoLabel(year: Int, thisYear: Int = LocalDate.now().year): String = when (val diff = thisYear - year) {
    0 -> "Earlier this year"
    1 -> "1 year ago"
    else -> "$diff years ago"
}

fun formatTime(hour: Int, minute: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "$h:${minute.toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}"
}

/** Start and end (exclusive) of a local calendar day, in epoch ms. Null if the day doesn't exist that year. */
fun dayRange(year: Int, md: MonthDay, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long>? {
    val date = runCatching { LocalDate.of(year, md.month + 1, md.day) }.getOrNull() ?: return null
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
}

fun localDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
