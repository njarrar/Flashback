package com.njarrar.backthen.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun antimeridianHighLatitudeAndNanCoordinates() {
        val edgeCities = CityList(
            sequenceOf(
                "Taveuni\tFJ\t-16.82\t-179.95\t60000",
                "Tromso\tNO\t69.65\t18.96\t77000",
                "BadRow\tXX\tNaN\t10.0\t50000",
                "ShortRow\tXX",
            ),
        )
        assertEquals(2, edgeCities.cities.size)
        // Crosses the ±180° antimeridian (~16 km away across 180°).
        assertTrue(distanceKm(-16.82, 179.90, -16.82, -179.95) < 20.0)
        assertEquals(0, edgeCities.city(-16.82, 179.90))
        // High latitude (>69°N): 1.0° longitude is ~38.7 km < 40 km reach.
        assertTrue(distanceKm(69.65, 19.96, 69.65, 18.96) < CityList.REACH_KM)
        assertEquals(1, edgeCities.city(69.65, 19.96))
        // NaN, Infinite, and out-of-range coordinates must never match a city.
        assertNull(edgeCities.city(Double.NaN, 18.96))
        assertNull(edgeCities.city(69.65, Double.NaN))
        assertNull(edgeCities.city(Double.POSITIVE_INFINITY, 0.0))
        assertNull(edgeCities.city(95.0, 10.0))
        assertTrue(distanceKm(Double.NaN, 0.0, 0.0, 0.0).isNaN())
    }

    @Test
    fun cityListHashChangesWhenDatasetChanges() {
        val other = CityList(
            sequenceOf(
                "London\tGB\t51.508\t-0.126\t8961989",
                "Paris\tFR\t48.853\t2.349\t2138551",
            ),
        )
        assertTrue(cities.hash.length == 64)
        assertNotEquals(cities.hash, other.hash)
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
        // Custom home city override flips which city is treated as home.
        assertNull(makePlaceCard(index, 2, cities, today, customHomeCity = 2))
        val londonCard = makePlaceCard(index, 0, cities, today, customHomeCity = 2)
        assertEquals("London", londonCard?.city?.name)
        // Exact 7-day cutoff boundary: a trip ending exactly 7 days ago is excluded (< cutoff).
        val boundary = pastTrips(mapOf("2026-10-11" to 2, "2026-10-10" to 3), today)
        assertEquals(listOf(LocalDate.of(2026, 10, 10)), boundary.map { it.end })
    }
}
