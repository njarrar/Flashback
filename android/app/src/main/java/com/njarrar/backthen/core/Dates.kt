package com.njarrar.backthen.core

import android.icu.text.DateFormat
import android.icu.util.GregorianCalendar
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** A calendar day without a year. [month] is 0-based, to match the design's logic. */
data class MonthDay(val month: Int, val day: Int) {
    val key: String get() = "$month-$day"

    companion object {
        fun of(date: LocalDate) = MonthDay(date.monthValue - 1, date.dayOfMonth)
        fun today() = of(LocalDate.now())

        /** Reads a [key] back, or null if it isn't one. */
        fun fromKey(key: String): MonthDay? {
            val (m, d) = key.split("-").takeIf { it.size == 2 }?.map { it.toIntOrNull() ?: return null } ?: return null
            return if (m in 0..11 && d in 1..DAYS_IN_MONTH[m]) MonthDay(m, d) else null
        }
    }
}

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

fun agoLabel(year: Int, thisYear: Int = LocalDate.now().year): String {
    val diff = thisYear - year
    return if (diff == 0) L.s("ago_this_year") else L.p("ago_years", diff)
}

// Dates in the app's language, from CLDR skeletons such as "MMMMd" (October 16) or "EEEE" (Friday).
// These use Android's ICU, so they run on a phone, not in plain JVM unit tests.
// Always the Gregorian calendar: the app matches photos by Gregorian date, and a phone set to
// Saudi Arabia would otherwise write the Hijri date.

private val dateFormats = HashMap<String, DateFormat>()
private val timeFormats = HashMap<String, DateFormat>()

fun formatDate(date: LocalDate, skeleton: String): String {
    val locale = L.locale
    val at = Date(date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    return synchronized(dateFormats) {
        dateFormats.getOrPut("${locale.toLanguageTag()}|$skeleton") {
            DateFormat.getInstanceForSkeleton(GregorianCalendar(locale), skeleton, locale)
        }.format(at)
    }
}

/** A month and day with no year, in a leap year so February 29 works. */
private fun MonthDay.inLeapYear(): LocalDate = LocalDate.of(2024, month + 1, day)

/** "October 16" in the app's language. */
fun MonthDay.label() = formatDate(inLeapYear(), "MMMMd")

/** "Oct 16" in the app's language. */
fun MonthDay.shortLabel() = formatDate(inLeapYear(), "MMMd")

fun monthName(month: Int) = formatDate(LocalDate.of(2024, month + 1, 1), "LLLL")

/** "October 16, 2021", or just the month and day if that year has no such day. */
fun fullDate(year: Int, md: MonthDay): String =
    runCatching { formatDate(LocalDate.of(year, md.month + 1, md.day), "yMMMMd") }.getOrDefault(md.label())

fun weekdayOf(year: Int, md: MonthDay): String =
    runCatching { formatDate(LocalDate.of(year, md.month + 1, md.day), "EEEE") }.getOrDefault("")

/** The reminder time in the app's language. [use24] is the phone's 12 or 24 hour setting; null lets the language pick. */
fun formatTime(hour: Int, minute: Int, use24: Boolean? = null): String {
    val locale = L.locale
    val at = Date(LocalDate.of(2024, 1, 1).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    val skeleton = when (use24) { true -> "Hmm"; false -> "hmm"; null -> "jmm" }
    return synchronized(timeFormats) {
        timeFormats.getOrPut("${locale.toLanguageTag()}|$skeleton") {
            DateFormat.getInstanceForSkeleton(skeleton, locale)
        }.format(at)
    }
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
