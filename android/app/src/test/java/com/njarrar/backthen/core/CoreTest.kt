package com.njarrar.backthen.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        // Future-dated photos never render negative years ago.
        assertEquals("Earlier this year", agoLabel(2028, 2026))
    }

    @Test fun dayRangeHandlesDstAndLeapDays() {
        val ny = ZoneId.of("America/New_York")
        // Spring forward (2026-03-08): 23-hour day.
        val spring = dayRange(2026, MonthDay(2, 8), ny)!!
        assertEquals(23L * 3_600_000L, spring.second - spring.first)
        // Fall back (2026-11-01): 25-hour day.
        val fall = dayRange(2026, MonthDay(10, 1), ny)!!
        assertEquals(25L * 3_600_000L, fall.second - fall.first)
        // Non-leap Feb 29 doesn't exist; leap Feb 29 is 24h.
        assertNull(dayRange(2026, MonthDay(1, 29), ny))
        val leap = dayRange(2024, MonthDay(1, 29), ny)!!
        assertEquals(24L * 3_600_000L, leap.second - leap.first)
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

    @Test fun swipeSkipsEmptyDaysWrapsAndVisitsPastYearDays() {
        assertEquals(MonthDay(8, 20) to 11, swipeTarget(index, MonthDay(9, 2), -1, today))
        assertEquals(MonthDay(9, 2) to 11, swipeTarget(index, MonthDay(8, 20), 1, today))
        // Oct 9 has 2025 photos, so swiping forward from Oct 2 lands there (6 empty days: Oct 3..8) and back works too.
        assertEquals(MonthDay(9, 9) to 6, swipeTarget(index, MonthDay(9, 2), 1, today))
        assertEquals(MonthDay(9, 2) to 6, swipeTarget(index, MonthDay(9, 9), -1, today))
        // Wraps around the year boundary from Oct 9 forward to Sep 20, and from Sep 20 back to Oct 9.
        val wrappedForward = swipeTarget(index, MonthDay(9, 9), 1, today)
        assertEquals(MonthDay(8, 20), wrappedForward?.first)
        val wrappedBack = swipeTarget(index, MonthDay(8, 20), -1, today)
        assertEquals(MonthDay(9, 9), wrappedBack?.first)
        // Single-day index returns null rather than looping back onto itself.
        val single: DayIndex = mapOf("9-2" to mapOf(2019 to 1))
        assertNull(swipeTarget(single, MonthDay(9, 2), 1, today))
        assertNull(swipeTarget(single, MonthDay(9, 2), -1, today))
        assertNull(swipeTarget(index, MonthDay(9, 2), 0, today))
    }

    @Test fun swipeSkipsNonLeapFeb29WithoutCountingEmptyDay() {
        val febMar: DayIndex = mapOf("1-28" to mapOf(2020 to 1), "2-1" to mapOf(2020 to 1))
        val nonLeap = LocalDate.of(2026, 3, 5)
        val leap = LocalDate.of(2024, 3, 5)
        // In 2026 (non-leap), Feb 28 and Mar 1 are adjacent -> 0 empty days skipped.
        assertEquals(MonthDay(1, 28) to 0, swipeTarget(febMar, MonthDay(2, 1), -1, nonLeap))
        assertEquals(MonthDay(2, 1) to 0, swipeTarget(febMar, MonthDay(1, 28), 1, nonLeap))
        // In 2024 (leap), Feb 29 exists and is empty -> 1 empty day skipped.
        assertEquals(MonthDay(1, 28) to 1, swipeTarget(febMar, MonthDay(2, 1), -1, leap))
        // If Feb 29 has photos from a past leap year, swiping lands on Feb 29 even in a non-leap year.
        val withLeapPhoto: DayIndex = febMar + ("1-29" to mapOf(2024 to 2))
        assertEquals(MonthDay(1, 29) to 0, swipeTarget(withLeapPhoto, MonthDay(2, 1), -1, nonLeap))
    }

    @Test fun photoCountAndFavoriteCover() {
        assertEquals(3, photoCount(index, MonthDay(9, 2), today))
        val tokyo = ZoneId.of("Asia/Tokyo")
        val time = LocalDate.of(2020, 10, 2).atTime(1, 0).atZone(tokyo).toInstant().toEpochMilli()
        val fav = Photo("f", PhotoSource.DEVICE, "content://f", 1, 1, time)
        assertEquals("f", favoriteCover(listOf(fav), MonthDay(9, 2), listOf(2021, 2020), tokyo)?.key)
        // Same instant is Oct 1 in New York.
        assertNull(favoriteCover(listOf(fav), MonthDay(9, 2), listOf(2021, 2020), zone))
        assertEquals("f", favoriteCover(listOf(fav), MonthDay(9, 1), listOf(2021, 2020), zone)?.key)
        // Not a year the day counts, or another day: no favorite.
        assertNull(favoriteCover(listOf(fav), MonthDay(9, 2), listOf(2021), tokyo))
        assertNull(favoriteCover(listOf(fav), MonthDay(9, 3), listOf(2020), tokyo))
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

    @Test fun masonryBalancesColumnsAndGuardsBadInputs() {
        // A tall photo first, so the next three fit beside it.
        assertEquals(listOf(listOf(0), listOf(1, 2, 3)), masonryColumns(listOf(0.6f, 1.5f, 1.5f, 1f)))
        assertEquals(listOf(emptyList<Int>(), emptyList()), masonryColumns(emptyList()))
        assertEquals(emptyList(), masonryColumns(listOf(1f, 2f), columns = 0))
        assertEquals(listOf(listOf(0, 2), listOf(1)), masonryColumns(listOf(Float.NaN, 0f, -1f), columns = 2))
        assertEquals(1f, tileAspect(0, 0))
        assertEquals(1.8f, tileAspect(4000, 1000))
    }

    @Test fun monthDayKeysRoundTrip() {
        assertEquals(MonthDay(9, 2), MonthDay.fromKey(MonthDay(9, 2).key))
        assertEquals(MonthDay(1, 29), MonthDay.fromKey("1-29"))
        assertNull(MonthDay.fromKey("1-30"))
        assertNull(MonthDay.fromKey("x"))
    }
}
