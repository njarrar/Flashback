package com.njarrar.backthen.core

import java.time.LocalDate
import java.time.ZoneId

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

/** The favorite to show for a day in the calendar: one taken on that day, in a year the day counts. */
fun favoriteCover(favorites: List<Photo>, md: MonthDay, years: List<Int>): Photo? =
    favorites.firstOrNull { val d = localDate(it.time); MonthDay.of(d) == md && d.year in years }

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

/**
 * Where a swipe on Today lands: the next day with photos going forward ([step] 1) or back
 * ([step] -1), and how many empty days it passed. Stays between January 1 and today, the days
 * the calendar lets you pick.
 */
fun swipeTarget(index: DayIndex, from: MonthDay, step: Int, today: LocalDate = LocalDate.now()): Pair<MonthDay, Int>? {
    val now = MonthDay.of(today)
    var at = from
    for (skipped in 0 until 366) {
        val next = at.shift(step)
        if (next.isAfter(now) || (step < 0 && next.isAfter(at))) return null
        if (hasPhotos(index, next, today)) return next to skipped
        at = next
    }
    return null
}
