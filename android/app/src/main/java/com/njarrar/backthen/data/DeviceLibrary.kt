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
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.PhotoSource
import com.njarrar.backthen.core.dayRange
import com.njarrar.backthen.core.localDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Saving copies to the gallery needs Android 10 or later, so older phones don't show the button. */
val CAN_SAVE_TO_GALLERY = Build.VERSION.SDK_INT >= 29

data class LibrarySignature(
    val count: Int,
    val latestId: Long,
    val latestModified: Long,
    val oldestId: Long,
)

/** Reads photos on this phone through MediaStore. On most phones this includes Google Photos backups stored locally. */
class DeviceLibrary(private val context: Context) {

    @Volatile
    private var lastSignature: LibrarySignature? = null

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

    /** Quick check of photo count and newest/oldest IDs so unchanged resumes can skip a full scan. */
    suspend fun signature(): LibrarySignature? = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_MODIFIED)
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { c ->
            val count = c.count
            if (count == 0 || !c.moveToFirst()) return@use LibrarySignature(0, 0L, 0L, 0L)
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val latestId = c.getLong(idCol)
            val latestMod = if (c.isNull(modCol)) 0L else c.getLong(modCol)
            val oldestId = if (c.moveToLast()) c.getLong(idCol) else latestId
            LibrarySignature(count, latestId, latestMod, oldestId)
        }
    }

    fun isUnchanged(sig: LibrarySignature?): Boolean =
        sig != null && sig == lastSignature

    fun recordSignature(sig: LibrarySignature?) {
        lastSignature = sig
    }

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

    /** Photos taken on one calendar day in each of [years], in one MediaStore query. Oldest first within a year. */
    suspend fun photosOn(years: List<Int>, md: MonthDay): Map<Int, List<Photo>> = withContext(Dispatchers.IO) {
        val ranges = years.mapNotNull { y -> dayRange(y, md)?.let { y to it } }
        if (ranges.isEmpty()) return@withContext emptyMap()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        // Some files have no capture time; fall back to when they were added (in seconds).
        val clause = "((${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?) OR " +
            "((${MediaStore.Images.Media.DATE_TAKEN} IS NULL OR ${MediaStore.Images.Media.DATE_TAKEN} = 0) AND " +
            "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?))"
        val selection = List(ranges.size) { clause }.joinToString(" OR ")
        val args = ArrayList<String>(ranges.size * 4)
        for ((_, r) in ranges) {
            args += r.first.toString()
            args += r.second.toString()
            args += (r.first / 1000).toString()
            args += (r.second / 1000).toString()
        }
        val byYear = HashMap<Int, MutableList<Photo>>()
        context.contentResolver.query(collection, projection, selection, args.toTypedArray(), null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val w = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val h = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (c.moveToNext()) {
                val assetId = c.getLong(id)
                val time = if (c.isNull(taken) || c.getLong(taken) == 0L) c.getLong(added) * 1000 else c.getLong(taken)
                val year = ranges.firstOrNull { (_, r) -> time >= r.first && time < r.second }?.first ?: localDate(time).year
                byYear.getOrPut(year) { ArrayList() } += Photo(
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
     * and Google Photos. Each file keeps its own type (JPEG, HEIC, PNG), and a photo already
     * in that folder is skipped, so saving again doesn't make copies. See [CAN_SAVE_TO_GALLERY].
     */
    suspend fun saveAlbum(name: String, photos: List<Photo>): Boolean = withContext(Dispatchers.IO) {
        if (!CAN_SAVE_TO_GALLERY) return@withContext false
        val folder = "${Environment.DIRECTORY_PICTURES}/Back Then/${name.replace('/', '-')}"
        val resolver = context.contentResolver
        val saveTo = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val there = HashSet<String>()
        resolver.query(
            saveTo, arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
            "${MediaStore.Images.Media.RELATIVE_PATH} = ?", arrayOf("$folder/"), null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let(there::add) }
        var failed = 0
        for (p in photos) {
            val source = Uri.parse(p.uri)
            val type = resolver.getType(source) ?: "image/jpeg"
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "jpg"
            val file = "backthen_${p.key.substringAfter(':')}.$ext"
            if (file in there) continue
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file)
                put(MediaStore.Images.Media.MIME_TYPE, type)
                put(MediaStore.Images.Media.RELATIVE_PATH, folder)
                put(MediaStore.Images.Media.DATE_TAKEN, p.time)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val target = resolver.insert(saveTo, values)
            if (target == null) { failed++; continue }
            val copied = runCatching {
                resolver.openOutputStream(target)?.use { out -> resolver.openInputStream(source)?.use { it.copyTo(out) } } != null
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
            there += file
        }
        failed == 0
    }
}
