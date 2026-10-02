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
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.PhotoSource
import com.njarrar.backthen.core.dayRange
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

    /** Photos taken on one calendar day of one year, oldest first. */
    suspend fun photosOn(year: Int, md: MonthDay): List<Photo> = withContext(Dispatchers.IO) {
        val (start, end) = dayRange(year, md) ?: return@withContext emptyList()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        // Some files have no capture time; fall back to when they were added (in seconds).
        val selection = "(${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?) OR " +
            "((${MediaStore.Images.Media.DATE_TAKEN} IS NULL OR ${MediaStore.Images.Media.DATE_TAKEN} = 0) AND " +
            "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?)"
        val args = arrayOf(start.toString(), end.toString(), (start / 1000).toString(), (end / 1000).toString())
        val out = ArrayList<Photo>()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val w = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val h = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (c.moveToNext()) {
                val assetId = c.getLong(id)
                val time = if (c.isNull(taken) || c.getLong(taken) == 0L) c.getLong(added) * 1000 else c.getLong(taken)
                out += Photo(
                    key = "d:$assetId",
                    source = PhotoSource.DEVICE,
                    uri = ContentUris.withAppendedId(collection, assetId).toString(),
                    width = c.getInt(w),
                    height = c.getInt(h),
                    time = time,
                )
            }
        }
        out.sortedBy { it.time }
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
