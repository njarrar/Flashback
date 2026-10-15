package com.njarrar.backthen.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

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

fun mergeIndexes(vararg indexes: DayIndex): DayIndex {
    val out = HashMap<String, HashMap<Int, Int>>()
    for (index in indexes) for ((key, byYear) in index) {
        val target = out.getOrPut(key) { HashMap() }
        for ((y, n) in byYear) target.merge(y, n, Int::plus)
    }
    return out
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

/** Same test as [yearsForDay], but stops at the first match and sorts nothing. Calendar dots call it a lot. */
fun hasPhotos(index: DayIndex, md: MonthDay, today: LocalDate = LocalDate.now()): Boolean {
    val byYear = index[md.key] ?: return false
    val passed = md.month < today.monthValue - 1 || (md.month == today.monthValue - 1 && md.day < today.dayOfMonth)
    return byYear.any { (y, n) -> n > 0 && (y < today.year || (y == today.year && passed)) }
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

/**
 * Drops Google copies of shots already on the phone. Google keeps the capture time,
 * so a match within two seconds and the same orientation counts as the same shot.
 */
fun dropDuplicates(device: List<Photo>, google: List<Photo>): List<Photo> {
    fun Photo.landscape() = width >= height
    val kept = google.filter { g -> device.none { abs(it.time - g.time) < 2000 && it.landscape() == g.landscape() } }
    return device + kept
}
