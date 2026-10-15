package com.njarrar.backthen.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.njarrar.backthen.core.DayIndex
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.PhotoSource
import com.njarrar.backthen.core.dayRange
import com.njarrar.backthen.core.localDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads photos on this phone through MediaStore. On most phones this includes Google Photos backups stored locally. */
class DeviceLibrary(private val context: Context) {

    /** The permissions to ask for on this Android version. */
    val permissions: Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** True with full or partial access. */
    fun hasAccess(): Boolean = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    /** Capture times of every photo the app can see. Reads two columns only, so it stays fast. */
    suspend fun readAllTimes(): List<Long> = withContext(Dispatchers.IO) {
        val times = ArrayList<Long>()
        val projection = arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val t = if (c.isNull(taken) || c.getLong(taken) == 0L) c.getLong(added) * 1000 else c.getLong(taken)
                if (t > 0) times += t
            }
        }
        times
    }

    /** Photo count, newest id and when it was added: if none of these moved, the index is still right. */
    private data class Signature(val count: Int, val newestId: Long, val newestAdded: Long)
    @Volatile private var lastSignature: Signature? = null

    /**
     * Counts photos per day straight from MediaStore, without building a list of times first.
     * Returns null when the library looks the same as last time, unless [force] is set.
     */
    suspend fun readDeviceIndex(force: Boolean = false): DayIndex? = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
        val index = HashMap<String, HashMap<Int, Int>>(366)
        context.contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media._ID} DESC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val first = c.moveToFirst()
            val sig = Signature(c.count, if (first) c.getLong(id) else -1, if (first) c.getLong(added) else -1)
            if (!force && sig == lastSignature) return@withContext null
            lastSignature = sig
            if (first) {
                do {
                    val t = if (c.isNull(taken) || c.getLong(taken) == 0L) c.getLong(added) * 1000 else c.getLong(taken)
                    if (t > 0) {
                        val date = localDate(t)
                        index.getOrPut(MonthDay.of(date).key) { HashMap() }.merge(date.year, 1, Int::plus)
                    }
                } while (c.moveToNext())
            }
        }
        index
    }

    /** Photos taken on one calendar day in each of [years], oldest first, from one query. */
    suspend fun photosOn(years: List<Int>, md: MonthDay): Map<Int, List<Photo>> = withContext(Dispatchers.IO) {
        val ranges = years.mapNotNull { dayRange(it, md) }
        if (ranges.isEmpty()) return@withContext emptyMap()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        // Some files have no capture time; fall back to when they were added (in seconds).
        val oneDay = "((${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?) OR " +
            "((${MediaStore.Images.Media.DATE_TAKEN} IS NULL OR ${MediaStore.Images.Media.DATE_TAKEN} = 0) AND " +
            "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?))"
        val selection = ranges.joinToString(" OR ") { oneDay }
        val args = ranges.flatMap { (start, end) -> listOf(start, end, start / 1000, end / 1000).map { it.toString() } }
        val byYear = HashMap<Int, ArrayList<Photo>>()
        context.contentResolver.query(collection, projection, selection, args.toTypedArray(), null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val w = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val h = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (c.moveToNext()) {
                val assetId = c.getLong(id)
                val time = if (c.isNull(taken) || c.getLong(taken) == 0L) c.getLong(added) * 1000 else c.getLong(taken)
                byYear.getOrPut(localDate(time).year) { ArrayList() } += Photo(
                    key = "d:$assetId",
                    source = PhotoSource.DEVICE,
                    uri = ContentUris.withAppendedId(collection, assetId).toString(),
                    width = c.getInt(w),
                    height = c.getInt(h),
                    time = time,
                )
            }
        }
        byYear.mapValues { (_, list) -> list.sortedBy { it.time } }
    }

    /**
     * Copies photos into Pictures/Back Then/<album>, so the album shows up in the gallery
     * and Google Photos. Needs Android 10 or later.
     */
    suspend fun saveAlbum(name: String, photos: List<Photo>): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext false
        val folder = "${Environment.DIRECTORY_PICTURES}/Back Then/${name.replace('/', '-')}"
        val resolver = context.contentResolver
        var failed = 0
        for ((i, p) in photos.withIndex()) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "backthen_${p.time}_$i.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, folder)
                put(MediaStore.Images.Media.DATE_TAKEN, p.time)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val target = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            if (target == null) { failed++; continue }
            val copied = runCatching {
                resolver.openOutputStream(target)?.use { out -> openPhoto(p)?.use { it.copyTo(out) } } != null
            }.getOrDefault(false)
            if (!copied) {
                // Don't leave an empty or half-written file behind in the gallery.
                resolver.delete(target, null, null)
                failed++
                continue
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(target, values, null, null)
        }
        failed == 0
    }

    private fun openPhoto(p: Photo) = when (p.source) {
        PhotoSource.DEVICE -> context.contentResolver.openInputStream(Uri.parse(p.uri))
        PhotoSource.GOOGLE -> Uri.parse(p.uri).path?.let { java.io.File(it).inputStream() }
    }
}
