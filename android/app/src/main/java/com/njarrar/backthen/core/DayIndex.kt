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

fun hasPhotos(index: DayIndex, md: MonthDay, today: LocalDate = LocalDate.now()) =
    yearsForDay(index, md, today).isNotEmpty()

/** Photos on this day across the past years [yearsForDay] counts. */
fun photoCount(index: DayIndex, md: MonthDay, today: LocalDate = LocalDate.now()): Int {
    val byYear = index[md.key] ?: return 0
    return yearsForDay(index, md, today).sumOf { byYear[it] ?: 0 }
}

/**
 * How full each day of the year is, for the calendar's shading: 0 for no photos, then 1 to 4.
 * Days with photos are ranked by how many they have and split into four even groups, so the
 * shades spread out the same way for a small library or a huge one. Keyed by [MonthDay.key].
 */
fun dayLevels(index: DayIndex, today: LocalDate = LocalDate.now()): Map<String, Int> {
    val counts = HashMap<String, Int>()
    for (m in 0..11) for (d in 1..DAYS_IN_MONTH[m]) {
        val md = MonthDay(m, d)
        val n = photoCount(index, md, today)
        if (n > 0) counts[md.key] = n
    }
    if (counts.isEmpty()) return emptyMap()
    val sorted = counts.values.sorted().toIntArray()
    val total = sorted.size.toDouble()
    return counts.mapValues { (_, n) ->
        // Where this count sits among all days, 0 to 1. Ties share the middle of their run.
        val below = lowerBound(sorted, n)
        val same = lowerBound(sorted, n + 1) - below
        val place = (below + same / 2.0) / total
        1 + minOf(3, (place * 4).toInt())
    }
}

private fun lowerBound(sorted: IntArray, n: Int): Int {
    var lo = 0
    var hi = sorted.size
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (sorted[mid] < n) lo = mid + 1 else hi = mid
    }
    return lo
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
    if (device.isEmpty() || google.isEmpty()) return device + google
    val landscapeTimes = LongArray(device.count { it.width >= it.height })
    val portraitTimes = LongArray(device.size - landscapeTimes.size)
    var li = 0
    var pi = 0
    for (p in device) {
        if (p.width >= p.height) landscapeTimes[li++] = p.time else portraitTimes[pi++] = p.time
    }
    landscapeTimes.sort()
    portraitTimes.sort()

    fun hasNear(sorted: LongArray, target: Long): Boolean {
        if (sorted.isEmpty()) return false
        val idx = sorted.binarySearch(target)
        if (idx >= 0) return true
        val ins = -idx - 1
        if (ins < sorted.size && abs(sorted[ins] - target) < 2000L) return true
        if (ins > 0 && abs(sorted[ins - 1] - target) < 2000L) return true
        return false
    }

    val kept = google.filter { g ->
        val bucket = if (g.width >= g.height) landscapeTimes else portraitTimes
        !hasNear(bucket, g.time)
    }
    return device + kept
}
