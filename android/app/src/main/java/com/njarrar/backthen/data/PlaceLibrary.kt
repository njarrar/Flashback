package com.njarrar.backthen.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.njarrar.backthen.core.CityList
import com.njarrar.backthen.core.PlaceIndex
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.PhotoSource
import com.njarrar.backthen.core.Trip
import com.njarrar.backthen.core.dayKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.ZoneId
import kotlin.coroutines.resume

/**
 * Reads where photos were taken and roughly where the phone is now. Android keeps photo
 * locations only inside each file, so each photo is read once and its city kept in a small
 * file (photo-cities.txt). Nothing leaves the phone.
 */
class PlaceLibrary(private val context: Context) {
    private val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val cacheFile = File(context.filesDir, "photo-cities.txt")
    private val lock = Mutex()

    /** Rough location for the phone, and from Android 10 on, leave to read photo locations. */
    val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= 29) arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_MEDIA_LOCATION)
        else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasAccess(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val cities: CityList? by lazy { runCatching { context.assets.open("cities.tsv").use(CityList::read) }.getOrNull() }

    /** Photo days per city for the whole library. Only photos not seen before get read. */
    suspend fun index(cities: CityList): PlaceIndex = withContext(Dispatchers.IO) {
        lock.withLock {
            val known = loadCache()
            val times = allTimes()
            var read = 0
            for (id in times.keys) {
                if (id in known) continue
                known[id] = readCity(id, cities) ?: -1
                // Keep what was read so far, in case the app closes during a long first pass.
                if (++read % 500 == 0) saveCache(known)
            }
            known.keys.retainAll(times.keys)
            saveCache(known)
            val out = HashMap<Int, HashMap<String, Int>>()
            for ((id, time) in times) {
                val city = known[id]?.takeIf { it >= 0 } ?: continue
                val days = out.getOrPut(city) { HashMap() }
                val key = dayKey(time)
                days[key] = (days[key] ?: 0) + 1
            }
            out
        }
    }

    /** The photos from one visit to a city, oldest first. */
    suspend fun photos(city: Int, trip: Trip): List<Photo> = withContext(Dispatchers.IO) {
        val known = lock.withLock { loadCache() }
        val zone = ZoneId.systemDefault()
        val from = trip.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = trip.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val out = ArrayList<Photo>()
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?",
            arrayOf(from.toString(), to.toString()),
            "${MediaStore.Images.Media.DATE_TAKEN} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val w = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val h = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                if (known[id] != city) continue
                out += Photo(
                    key = "d:$id",
                    source = PhotoSource.DEVICE,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    width = c.getInt(w),
                    height = c.getInt(h),
                    time = c.getLong(takenCol),
                )
            }
        }
        out
    }

    /** Where the phone is now, roughly. Null without permission or when it can't tell. */
    @SuppressLint("MissingPermission")
    suspend fun currentSpot(): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = when {
            Build.VERSION.SDK_INT >= 31 && lm.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        val now: Location? = if (provider != null && Build.VERSION.SDK_INT >= 30) {
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                }
            }
        } else {
            null
        }
        val spot = now ?: listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        return spot?.let { it.latitude to it.longitude }
    }

    /** Capture time of every photo, by MediaStore ID. */
    private fun allTimes(): LinkedHashMap<Long, Long> {
        val out = LinkedHashMap<Long, Long>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val t = if (c.isNull(taken) || c.getLong(taken) == 0L) c.getLong(added) * 1000 else c.getLong(taken)
                if (t > 0) out[c.getLong(id)] = t
            }
        }
        return out
    }

    private fun readCity(id: Long, cities: CityList): Int? {
        var uri = ContentUris.withAppendedId(collection, id)
        // Without this, Android 10 and later blank out the location in the file.
        if (Build.VERSION.SDK_INT >= 29) uri = MediaStore.setRequireOriginal(uri)
        val at = runCatching { context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).latLong } }.getOrNull() ?: return null
        return cities.city(at[0], at[1])
    }

    private fun loadCache(): HashMap<Long, Int> {
        val out = HashMap<Long, Int>()
        runCatching {
            cacheFile.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) {
                    val id = line.substring(0, tab).toLongOrNull()
                    val city = line.substring(tab + 1).toIntOrNull()
                    if (id != null && city != null) out[id] = city
                }
            }
        }
        return out
    }

    private fun saveCache(known: Map<Long, Int>) {
        val tmp = File(cacheFile.path + ".tmp")
        runCatching {
            tmp.bufferedWriter().use { w -> for ((id, city) in known) { w.write("$id\t$city\n") } }
            tmp.renameTo(cacheFile)
        }
    }
}
