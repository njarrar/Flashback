package com.njarrar.backthen.core

import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

// Places: which city each photo was taken in, from a list of cities built into the app
// (places/cities.tsv). Nothing about where you are or were leaves the phone.

/** Photo days per city: city row in cities.tsv -> "yyyy-MM-dd" -> photo count. */
typealias PlaceIndex = Map<Int, Map<String, Int>>

data class City(val id: Int, val name: String, val country: String, val lat: Double, val lon: Double, val population: Int)

/**
 * The cities in places/cities.tsv (50,000 people or more), with a grid of one-degree
 * cells so finding the city for a spot looks at a few dozen cities, not thousands.
 */
class CityList(lines: Sequence<String>) {
    val cities: List<City>
    private val grid = HashMap<Int, MutableList<Int>>()

    init {
        val out = ArrayList<City>()
        for (line in lines) {
            val f = line.split('\t')
            if (f.size < 5) continue
            val lat = f[2].toDoubleOrNull() ?: continue
            val lon = f[3].toDoubleOrNull() ?: continue
            val pop = f[4].toIntOrNull() ?: continue
            val city = City(out.size, f[0], f[1], lat, lon, pop)
            grid.getOrPut(cell(lat, lon)) { ArrayList() } += city.id
            out += city
        }
        cities = out
    }

    /**
     * The city a spot belongs to: among cities within reach, big and near wins, so a photo
     * in a suburb counts for the big city next door, as people say it.
     */
    fun city(lat: Double, lon: Double): Int? {
        if (lat == 0.0 && lon == 0.0) return null
        var best = -1
        var bestScore = 0.0
        for (dLat in -1..1) for (dLon in -1..1) {
            for (id in grid[cell(lat + dLat, lon + dLon)].orEmpty()) {
                val c = cities[id]
                val d = distanceKm(lat, lon, c.lat, c.lon)
                if (d > REACH_KM) continue
                val score = c.population / ((d + 5) * (d + 5))
                if (best < 0 || score > bestScore) { best = id; bestScore = score }
            }
        }
        return best.takeIf { it >= 0 }
    }

    companion object {
        /** Photos farther than this from every city have no city. */
        const val REACH_KM = 40.0

        fun read(input: InputStream) = input.bufferedReader().useLines { CityList(it) }

        private fun cell(lat: Double, lon: Double) = floor(lat + 90).toInt() * 361 + floor(lon + 180).toInt()
    }
}

/** Close enough for distances under a few hundred kilometers. */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val x = (lon2 - lon1) * cos((lat1 + lat2) / 2 * Math.PI / 180) * 111.32
    val y = (lat2 - lat1) * 110.57
    return sqrt(x * x + y * y)
}

/** "2019-05-12" for a photo's local day. */
fun dayKey(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String = localDate(epochMs, zone).toString()

/** One visit to a city: days with photos there, with no more than two days between them. */
data class Trip(val start: LocalDate, val end: LocalDate, val days: Int, val photos: Int)

/** A city's visits, newest first. */
fun trips(days: Map<String, Int>): List<Trip> {
    val sorted = days.mapNotNull { (k, n) -> runCatching { LocalDate.parse(k) }.getOrNull()?.let { it to n } }.sortedBy { it.first }
    val out = ArrayList<Trip>()
    var start: LocalDate? = null
    var last: LocalDate? = null
    var count = 0
    var photos = 0
    for ((day, n) in sorted) {
        val l = last
        if (l != null && ChronoUnit.DAYS.between(l, day) <= 3) {
            count++
            photos += n
        } else {
            if (start != null && l != null) out += Trip(start, l, count, photos)
            start = day
            count = 1
            photos = n
        }
        last = day
    }
    if (start != null && last != null) out += Trip(start, last, count, photos)
    return out.reversed()
}

/** Home: the city with the most photo days in the two years before today, or ever. */
fun homeCity(index: PlaceIndex, today: LocalDate = LocalDate.now()): Int? {
    val since = today.minusYears(2).toString()
    val recent = index.mapValues { (_, days) -> days.keys.count { it >= since } }
    val pick = if (recent.values.any { it > 0 }) recent else index.mapValues { it.value.size }
    return pick.entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).firstOrNull()?.key
}

/** Visits to a city that ended at least a week ago, newest first. A trip still going on isn't "before". */
fun pastTrips(days: Map<String, Int>, today: LocalDate = LocalDate.now()): List<Trip> {
    val cutoff = today.minusDays(7)
    return trips(days).filter { it.end < cutoff }
}

/** What Today shows when you're in a city you've been to before, away from home. */
data class PlaceCard(val city: City, val trips: List<Trip>) {
    val photos: Int get() = trips.sumOf { it.photos }
}

fun makePlaceCard(index: PlaceIndex, here: Int?, cities: CityList, today: LocalDate = LocalDate.now()): PlaceCard? {
    if (here == null || here == homeCity(index, today)) return null
    val past = pastTrips(index[here] ?: return null, today)
    if (past.isEmpty() || here >= cities.cities.size) return null
    return PlaceCard(cities.cities[here], past)
}
