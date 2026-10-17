package com.njarrar.backthen.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ln

/** Counts photos per calendar day and year from their capture times. */
fun buildDayIndex(times: Iterable<Long>, zone: ZoneId = ZoneId.systemDefault()): DayIndex {
    val index = HashMap<String, HashMap<Int, Int>>()
    for (t in times) {
        val date = localDate(t, zone)
        val byYear = index.getOrPut(MonthDay.of(date).key) { HashMap() }
        byYear.merge(date.year, 1, Int::plus)
    }
    return index
}

/**
 * Past years with photos on this day, newest first. This year counts only once the
 * day has passed, so "today" never shows photos from earlier today.
 */
fun yearsForDay(index: DayIndex, md: MonthDay, today: LocalDate = LocalDate.now()): List<Int> {
    val byYear = index[md.key] ?: return emptyList()
    val passed = md.month < today.monthValue - 1 || (md.month == today.monthValue - 1 && md.day < today.dayOfMonth)
    return byYear.filter { (y, n) -> n > 0 && (y < today.year || (y == today.year && passed)) }
        .keys.sortedDescending()
}

fun hasPhotos(index: DayIndex, md: MonthDay, today: LocalDate = LocalDate.now()) =
    yearsForDay(index, md, today).isNotEmpty()

/** Photos on this day across the past years [yearsForDay] counts. */
fun photoCount(index: DayIndex, md: MonthDay, today: LocalDate = LocalDate.now()): Int {
    val byYear = index[md.key] ?: return 0
    return yearsForDay(index, md, today).sumOf { byYear[it] ?: 0 }
}

/**
 * How full each day of the year is, for the calendar's shading: 0 for no photos, then 1 to 5.
 * The level follows the photo count on a log scale, so 1, 2, 5, 20 and 100 photos each look
 * different. The top level starts at the count that only the busiest tenth of days reach (at
 * least 8), so one huge day doesn't wash out the rest. Days after today are left out, since the
 * calendar doesn't open them. Keyed by [MonthDay.key].
 */
fun dayLevels(index: DayIndex, today: LocalDate = LocalDate.now()): Map<String, Int> {
    val now = MonthDay.of(today)
    val counts = HashMap<String, Int>()
    for (m in 0..11) for (d in 1..DAYS_IN_MONTH[m]) {
        val md = MonthDay(m, d)
        if (md.isAfter(now)) continue
        val n = photoCount(index, md, today)
        if (n > 0) counts[md.key] = n
    }
    if (counts.isEmpty()) return emptyMap()
    val sorted = counts.values.sorted()
    val top = maxOf(8, sorted[(sorted.size - 1) * 9 / 10])
    return counts.mapValues { (_, n) -> levelFor(n, top) }
}

/** 1 to 5 for [n] photos, where [top] or more is 5. */
internal fun levelFor(n: Int, top: Int): Int {
    if (n >= top) return 5
    val t = ln(n.toDouble()) / ln(top.toDouble())
    return 1 + minOf(3, (t * 4 + 1e-9).toInt())
}

/** The closest earlier and later days that have photos, within 60 days. */
fun nearbyDays(index: DayIndex, md: MonthDay, today: LocalDate = LocalDate.now()): Pair<MonthDay?, MonthDay?> {
    var before: MonthDay? = null
    var after: MonthDay? = null
    for (k in 1..60) {
        if (before == null) md.shift(-k).let { if (hasPhotos(index, it, today)) before = it }
        if (after == null) md.shift(k).let { if (hasPhotos(index, it, today)) after = it }
        if (before != null && after != null) break
    }
    return before to after
}
