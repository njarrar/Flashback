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
        val en = LangFiles.load("en")
        L.use(en, en, java.util.Locale.ENGLISH)
        assertEquals("1 year ago", agoLabel(2025, 2026))
        assertEquals("10 years ago", agoLabel(2016, 2026))
        assertEquals("Earlier this year", agoLabel(2026, 2026))
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

    @Test fun dayLevelsFollowPhotoCount() {
        assertEquals(3, photoCount(index, MonthDay(9, 2), today))
        // 9-2 has 3 photos and 8-20 has 1; 9-9 comes after today, and empty days are left out.
        assertEquals(mapOf("9-2" to 3, "8-20" to 1), dayLevels(index, today))
        // Eight days with 1 to 8 photos: the shade grows with the count.
        val spread = (1..8).associate { "0-$it" to mapOf(2020 to it) }
        assertEquals(listOf(1, 2, 3, 3, 4, 4, 4, 5), (1..8).map { dayLevels(spread, today)["0-$it"] })
        // One huge day doesn't wash out the rest: days with 1 photo stay at the lightest shade.
        val outlier = (1..9).associate { "0-$it" to mapOf(2020 to 1) } + ("0-10" to mapOf(2020 to 1000))
        assertEquals(setOf(1), dayLevels(outlier, today).filterKeys { it != "0-10" }.values.toSet())
        assertEquals(5, dayLevels(outlier, today)["0-10"])
        // On a log scale: 1, 4, 10, 40 and 100 photos each get their own shade when 100 is the top.
        assertEquals(listOf(1, 2, 3, 4, 5, 5), listOf(1, 4, 10, 40, 100, 5000).map { levelFor(it, 100) })
    }

    @Test fun laterDaysComeAfter() {
        assertTrue(MonthDay(9, 3).isAfter(MonthDay(9, 2)))
        assertTrue(MonthDay(10, 1).isAfter(MonthDay(9, 31)))
        assertTrue(!MonthDay(9, 2).isAfter(MonthDay(9, 2)))
        assertTrue(!MonthDay(0, 31).isAfter(MonthDay(9, 2)))
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
