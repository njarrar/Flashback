package com.njarrar.backthen.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreTest {
    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 10, 2)
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m + 1, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun shiftWrapsAcrossMonthsAndYears() {
        assertEquals(MonthDay(11, 31), MonthDay(0, 1).shift(-1))
        assertEquals(MonthDay(0, 1), MonthDay(11, 31).shift(1))
        assertEquals(MonthDay(1, 29), MonthDay(1, 28).shift(1))
    }

    @Test fun labels() {
        assertEquals("1 year ago", agoLabel(2025, 2026))
        assertEquals("10 years ago", agoLabel(2016, 2026))
        assertEquals("Earlier this year", agoLabel(2026, 2026))
        assertEquals("1 photo", plural(1, "photo"))
        assertEquals("3 photos", plural(3, "photo"))
        assertEquals("9:00 AM", formatTime(9, 0))
        assertEquals("12:05 AM", formatTime(0, 5))
        assertEquals("6:30 PM", formatTime(18, 30))
        assertEquals("18:30", formatTime(18, 30, use24 = true))
        assertEquals("00:05", formatTime(0, 5, use24 = true))
    }

    private val index = buildDayIndex(
        listOf(at(2014, 9, 2), at(2019, 9, 2), at(2019, 9, 2, 15), at(2026, 9, 2, 8), at(2025, 9, 9), at(2026, 8, 20)),
        zone,
    )

    @Test fun indexCountsPerDayAndYear() {
        assertEquals(mapOf(2014 to 1, 2019 to 2, 2026 to 1), index["9-2"])
    }

    @Test fun yearsSkipTodayInThisYear() {
        assertEquals(listOf(2019, 2014), yearsForDay(index, MonthDay(9, 2), today))
        assertEquals(listOf(2026), yearsForDay(index, MonthDay(8, 20), today))
    }

    @Test fun nearbyDaysFound() {
        val (before, after) = nearbyDays(index, MonthDay(9, 5), today)
        assertEquals(MonthDay(9, 2), before)
        assertEquals(MonthDay(9, 9), after)
    }

    @Test fun merges() {
        assertEquals(
            mapOf("1-1" to mapOf(2020 to 3, 2021 to 1)),
            mergeIndexes(mapOf("1-1" to mapOf(2020 to 1)), mapOf("1-1" to mapOf(2020 to 2, 2021 to 1))),
        )
    }

    @Test fun dropsGoogleCopies() {
        fun p(key: String, time: Long, w: Int = 4, h: Int = 3) =
            Photo(key, if (key[0] == 'd') PhotoSource.DEVICE else PhotoSource.GOOGLE, key, w, h, time)
        val out = dropDuplicates(listOf(p("d:1", 1000)), listOf(p("g:1", 1500), p("g:2", 9000), p("g:3", 1200, 3, 4)))
        assertEquals(listOf("d:1", "g:2", "g:3"), out.map { it.key })
    }

    @Test fun mosaicPacksWithoutGaps() {
        for (n in listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 25)) {
            val (tiles, rows) = layoutMosaic(n)
            assertEquals(n, tiles.size)
            val grid = Array(rows) { IntArray(COLUMNS) }
            for (t in tiles) {
                assertTrue(t.col + t.cols <= COLUMNS)
                for (r in t.row until t.row + t.rows) for (c in t.col until t.col + t.cols) grid[r][c]++
            }
            assertTrue(grid.all { row -> row.all { it == 1 } }, "gap or overlap for $n")
        }
    }

    @Test fun masonryBalancesColumns() {
        // A tall photo first, so the next three fit beside it.
        assertEquals(listOf(listOf(0), listOf(1, 2, 3)), masonryColumns(listOf(0.6f, 1.5f, 1.5f, 1f)))
        assertEquals(listOf(emptyList<Int>(), emptyList()), masonryColumns(emptyList()))
        assertEquals(1f, tileAspect(0, 0))
        assertEquals(1.8f, tileAspect(4000, 1000))
    }

    @Test fun monthDayKeysRoundTrip() {
        assertEquals(MonthDay(9, 2), MonthDay.fromKey(MonthDay(9, 2).key))
        assertEquals(MonthDay(1, 29), MonthDay.fromKey("1-29"))
        assertEquals(null, MonthDay.fromKey("1-30"))
        assertEquals(null, MonthDay.fromKey("x"))
    }
}
