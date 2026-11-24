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
import android.os.SystemClock
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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.ZoneId
import kotlin.coroutines.coroutineContext
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

    @Volatile
    private var citiesAvailable: Boolean? = null

    /** True if the bundled city list exists. Pre-checked off the main thread via [checkCitiesAvailable]. */
    val hasCities: Boolean
        get() = citiesAvailable ?: runCatching {
            context.assets.open("cities.tsv").use { true }
        }.getOrDefault(false).also { citiesAvailable = it }

    /** Pre-warms [hasCities] off the main thread. */
    suspend fun checkCitiesAvailable(): Boolean = withContext(Dispatchers.IO) { hasCities }

    private val cityList: CityList? by lazy { runCatching { context.assets.open("cities.tsv").use(CityList::read) }.getOrNull() }

    /** The city list, read once, off the main thread: it has about 12,000 lines. */
    suspend fun cities(): CityList? = withContext(Dispatchers.Default) { cityList }

    private sealed interface ReadCityResult {
        data class Found(val cityId: Int) : ReadCityResult
        data object NoLocation : ReadCityResult
        data object TransientError : ReadCityResult
    }

    /** Photo days per city for the whole library. Only photos not seen before get read. */
    suspend fun index(cities: CityList, partialAccess: Boolean = false): PlaceIndex = withContext(Dispatchers.IO) {
        val known = lock.withLock { loadCache(expectedHash = cities.hash) }
        val times = allTimes()
        var read = 0
        for (id in times.keys) {
            coroutineContext.ensureActive()
            if (id in known) continue
            when (val res = readCity(id, cities)) {
                is ReadCityResult.Found -> known[id] = res.cityId
                ReadCityResult.NoLocation -> known[id] = -1
                ReadCityResult.TransientError -> continue
            }
            // Keep what was read so far, in case the app closes during a long first pass.
            if (++read % 500 == 0) {
                val snapshot = HashMap(known)
                lock.withLock { saveCache(snapshot, cities.hash) }
            }
        }
        coroutineContext.ensureActive()
        if (!partialAccess) {
            known.keys.retainAll(times.keys)
        }
        val finalSnapshot = HashMap(known)
        lock.withLock { saveCache(finalSnapshot, cities.hash) }
        val out = HashMap<Int, HashMap<String, Int>>()
        for ((id, time) in times) {
            val city = known[id]?.takeIf { it >= 0 } ?: continue
            val days = out.getOrPut(city) { HashMap() }
            val key = dayKey(time)
            days[key] = (days[key] ?: 0) + 1
        }
        out
    }

    /** The photos from one visit to a city, oldest first. */
    suspend fun photos(city: Int, trip: Trip): List<Photo> = withContext(Dispatchers.IO) {
        val expectedHash = cities()?.hash
        val known = lock.withLock { loadCache(expectedHash = expectedHash) }
        val zone = ZoneId.systemDefault()
        val from = trip.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = trip.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
        )
        val (exclSql, exclArgs) = excludeSavedFlashbackSelection()
        val dateClause = "((${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?) OR " +
            "((${MediaStore.Images.Media.DATE_TAKEN} IS NULL OR ${MediaStore.Images.Media.DATE_TAKEN} = 0) AND " +
            "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?))"
        val selection = "$dateClause AND $exclSql"
        val args = arrayOf(from.toString(), to.toString(), (from / 1000L).toString(), (to / 1000L).toString()) + exclArgs
        val out = ArrayList<Photo>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            null,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val w = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val h = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val orient = c.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                if (known[id] != city) continue
                val time = if (c.isNull(takenCol) || c.getLong(takenCol) == 0L) c.getLong(addedCol) * 1000L else c.getLong(takenCol)
                val rawW = c.getInt(w)
                val rawH = c.getInt(h)
                val deg = if (orient >= 0 && !c.isNull(orient)) c.getInt(orient) else 0
                val swap = deg == 90 || deg == 270
                out += Photo(
                    key = "d:$id",
                    source = PhotoSource.DEVICE,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    width = if (swap) rawH else rawW,
                    height = if (swap) rawW else rawH,
                    time = time,
                )
            }
        }
        out.sortBy { it.time }
        out
    }

    /** Where the phone is now, roughly. Null without permission or when it can't tell. */
    @SuppressLint("MissingPermission")
    suspend fun currentSpot(): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = when {
            Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
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
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val fallbackProviders = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val spot = now ?: fallbackProviders
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { loc ->
                val age = nowNs - loc.elapsedRealtimeNanos
                age in 0L..MAX_LAST_KNOWN_AGE_NS
            }
            .minByOrNull { nowNs - it.elapsedRealtimeNanos }
        return spot?.let { it.latitude to it.longitude }
    }

    /** Capture time of every photo, by MediaStore ID (excluding Flashback's saved copies). */
    private fun allTimes(): LinkedHashMap<Long, Long> {
        val out = LinkedHashMap<Long, Long>()
        val (exclSql, exclArgs) = excludeSavedFlashbackSelection()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
        context.contentResolver.query(collection, projection, exclSql, exclArgs, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val t = if (c.isNull(taken) || c.getLong(taken) == 0L) c.getLong(added) * 1000L else c.getLong(taken)
                if (t > 0L) out[c.getLong(id)] = t
            }
        }
        return out
    }

    private fun readCity(id: Long, cities: CityList): ReadCityResult {
        var uri = ContentUris.withAppendedId(collection, id)
        // Without this, Android 10 and later blank out the location in the file.
        if (Build.VERSION.SDK_INT >= 29) uri = MediaStore.setRequireOriginal(uri)
        val at = try {
            val stream = context.contentResolver.openInputStream(uri) ?: return ReadCityResult.TransientError
            stream.use { ExifInterface(it).latLong }
        } catch (_: SecurityException) {
            return ReadCityResult.TransientError
        } catch (_: IOException) {
            return ReadCityResult.TransientError
        } catch (_: UnsupportedOperationException) {
            return ReadCityResult.TransientError
        } catch (_: IllegalStateException) {
            return ReadCityResult.TransientError
        } catch (_: Exception) {
            return ReadCityResult.NoLocation
        } ?: return ReadCityResult.NoLocation
        val cityId = cities.city(at[0], at[1]) ?: return ReadCityResult.NoLocation
        return ReadCityResult.Found(cityId)
    }

    private fun loadCache(expectedHash: String?): HashMap<Long, Int> {
        val out = HashMap<Long, Int>()
        if (!cacheFile.exists()) return out
        var fileHash: String? = null
        var firstLine = true
        runCatching {
            cacheFile.forEachLine { line ->
                if (firstLine) {
                    firstLine = false
                    if (line.startsWith(HASH_PREFIX)) {
                        fileHash = line.removePrefix(HASH_PREFIX)
                        return@forEachLine
                    }
                }
                if (line.startsWith("#")) return@forEachLine
                val tab = line.indexOf('\t')
                if (tab > 0) {
                    val id = line.substring(0, tab).toLongOrNull()
                    val city = line.substring(tab + 1).toIntOrNull()
                    if (id != null && city != null) out[id] = city
                }
            }
        }
        // If cities.tsv changed (or legacy cache predates hash header), discard stale positional IDs.
        if (expectedHash != null && fileHash != expectedHash) {
            out.clear()
        }
        return out
    }

    private fun saveCache(known: Map<Long, Int>, hash: String) {
        val tmp = File(cacheFile.path + ".tmp")
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val sb = StringBuilder(known.size * 14 + hash.length + 16)
            sb.append(HASH_PREFIX).append(hash).append('\n')
            for ((id, city) in known) {
                sb.append(id).append('\t').append(city).append('\n')
            }
            val bytes = sb.toString().toByteArray(Charsets.UTF_8)
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                runCatching { out.fd.sync() }
            }
            runCatching {
                Files.move(tmp.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.onFailure {
                Files.move(tmp.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { tmp.delete() }
    }

    private companion object {
        const val HASH_PREFIX = "#hash:"
        const val MAX_LAST_KNOWN_AGE_NS = 6L * 60L * 60L * 1_000_000_000L
    }
}
