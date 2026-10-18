package com.njarrar.backthen.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlacesTest {
    private val cities = CityList(
        sequenceOf(
            "London\tGB\t51.508\t-0.126\t8961989",
            "Croydon\tGB\t51.376\t-0.098\t173314",
            "Paris\tFR\t48.853\t2.349\t2138551",
        ),
    )

    @Test
    fun bigCityNextDoorWins() {
        assertEquals(3, cities.cities.size)
        // In Croydon, 15 km from central London: still London, as people say it.
        assertEquals(0, cities.city(51.37, -0.10))
        assertEquals(2, cities.city(48.86, 2.35))
        // Far from every city, or no location at all.
        assertNull(cities.city(45.0, -30.0))
        assertNull(cities.city(0.0, 0.0))
    }

    @Test
    fun daysGroupIntoTripsNewestFirst() {
        val t = trips(mapOf("2019-05-10" to 3, "2019-05-12" to 2, "2019-05-20" to 1, "2023-06-01" to 4))
        assertEquals(listOf(1, 1, 2), t.map { it.days })
        assertEquals(listOf(4, 1, 5), t.map { it.photos })
        assertEquals(LocalDate.of(2019, 5, 10), t[2].start)
        assertEquals(LocalDate.of(2019, 5, 12), t[2].end)
    }

    @Test
    fun cardOnlyAwayFromHomeAndForPastTrips() {
        val today = LocalDate.of(2026, 10, 18)
        val index: PlaceIndex = mapOf(
            0 to mapOf("2026-09-01" to 5, "2026-09-10" to 2, "2026-10-01" to 3),
            2 to mapOf("2019-05-10" to 3, "2023-06-01" to 4, "2026-10-17" to 6),
        )
        assertEquals(0, homeCity(index, today))
        assertNull(makePlaceCard(index, 0, cities, today))
        assertNull(makePlaceCard(index, 1, cities, today))
        val card = makePlaceCard(index, 2, cities, today)
        assertEquals("Paris", card?.city?.name)
        // Yesterday's photos are this trip, not one before.
        assertEquals(2, card?.trips?.size)
        assertEquals(7, card?.photos)
    }
}
