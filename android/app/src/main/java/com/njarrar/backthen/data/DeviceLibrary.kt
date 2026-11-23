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
import kotlinx.serialization.Serializable
import java.util.Locale

/** Saving copies to the gallery needs Android 10 or later, so older phones don't show the button. */
val CAN_SAVE_TO_GALLERY = Build.VERSION.SDK_INT >= 29

/** A quick fingerprint of the library. Saved with the day index, so a launch can skip the full scan when nothing changed. */
@Serializable
data class LibrarySignature(
    val count: Int = 0,
    val latestId: Long = 0L,
    val latestModified: Long = 0L,
    val oldestId: Long = 0L,
    val mediaVersion: String? = null,
    val mediaGeneration: Long? = null,
)

/** SQL filter excluding copies saved by [DeviceLibrary.saveAlbum] into Pictures/Flashback/. */
internal fun excludeSavedFlashbackSelection(): Pair<String, Array<String>> =
    if (Build.VERSION.SDK_INT >= 29) {
        "(${MediaStore.Images.Media.RELATIVE_PATH} IS NULL OR ${MediaStore.Images.Media.RELATIVE_PATH} NOT LIKE ?)" to
            arrayOf("${Environment.DIRECTORY_PICTURES}/Flashback/%")
    } else {
        @Suppress("DEPRECATION")
        "(${MediaStore.Images.Media.DATA} IS NULL OR ${MediaStore.Images.Media.DATA} NOT LIKE ?)" to
            arrayOf("%/${Environment.DIRECTORY_PICTURES}/Flashback/%")
    }

internal fun sanitizeAlbumFolderName(name: String): String {
    val cleaned = name
        .replace(Regex("""\.\.+"""), "")
        .replace(Regex("""[^\p{L}\p{N} _-]"""), "-")
        .replace(Regex("""-+"""), "-")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '.', '-', '_')
        .take(64)
    return cleaned.ifBlank { "Album" }
}

internal fun extensionForMimeType(mime: String): String = when (mime.lowercase(Locale.ROOT)) {
    "image/jpeg", "image/jpg" -> "jpg"
    "image/png" -> "png"
    "image/heic" -> "heic"
    "image/heif" -> "heif"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/avif" -> "avif"
    else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
}

/** Reads photos on this phone through MediaStore. On most phones this includes Google Photos backups stored locally. */
class DeviceLibrary(private val context: Context) {

    /** The permissions to ask for on this Android version. */
    val permissions: Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** True when the user granted full photo library access. */
    fun hasFullAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= 33 ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        else ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    /** True on Android 14+ when the user granted selected-photos access only. */
    fun hasPartialAccess(): Boolean =
        Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED &&
            !hasFullAccess()

    /** True with full or partial access. */
    fun hasAccess(): Boolean = hasFullAccess() || hasPartialAccess()

    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    /** Quick check of library version/generation across all external volumes (API 30+) or IDs/mod times so unchanged resumes skip a full scan. */
    suspend fun signature(): LibrarySignature? = withContext(Dispatchers.IO) {
        val partial = hasPartialAccess()
        if (Build.VERSION.SDK_INT >= 30 && !partial) {
            val volumes = runCatching { MediaStore.getExternalVolumeNames(context).sorted() }
                .getOrDefault(listOf(MediaStore.VOLUME_EXTERNAL_PRIMARY))
                .ifEmpty { listOf(MediaStore.VOLUME_EXTERNAL_PRIMARY) }
            val version = buildString {
                volumes.forEachIndexed { idx, vol ->
                    if (idx > 0) append('|')
                    val v = runCatching { MediaStore.getVersion(context, vol) }.getOrDefault("")
                    val g = runCatching { MediaStore.getGeneration(context, vol) }.getOrDefault(0L)
                    append(vol).append(':').append(v).append(':').append(g)
                }
            }
            val primaryGen = runCatching { MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY) }.getOrNull()
            return@withContext LibrarySignature(mediaVersion = version, mediaGeneration = primaryGen)
        }
        val (exclSql, exclArgs) = excludeSavedFlashbackSelection()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_MODIFIED)
        context.contentResolver.query(
            collection,
            projection,
            exclSql,
            exclArgs,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { c ->
            val count = c.count
            if (count == 0 || !c.moveToFirst()) return@use LibrarySignature(0, 0L, 0L, 0L)
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val latestId = c.getLong(idCol)
            val latestMod = if (c.isNull(modCol)) 0L else c.getLong(modCol)
            var oldestId = latestId
            var idHash: Long? = null
            if (partial) {
                // Hash all visible IDs in selected-photos mode so same-count swaps in Settings always re-index.
                var h = 1125899906842597L
                do {
                    val curId = c.getLong(idCol)
                    oldestId = curId
                    h = h * 31L + curId
                } while (c.moveToNext())
                idHash = h
            } else if (c.moveToLast()) {
                oldestId = c.getLong(idCol)
            }
            LibrarySignature(count, latestId, latestMod, oldestId, mediaGeneration = idHash)
        }
    }

    /** Capture times of every photo the app can see. Reads two columns only, so it stays fast. */
    suspend fun readAllTimes(): List<Long> = withContext(Dispatchers.IO) {
        val times = ArrayList<Long>()
        val (exclSql, exclArgs) = excludeSavedFlashbackSelection()
        val projection = arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
        context.contentResolver.query(collection, projection, exclSql, exclArgs, null)?.use { c ->
            val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val t = if (c.isNull(taken) || c.getLong(taken) == 0L) c.getLong(added) * 1000L else c.getLong(taken)
                if (t > 0L) times += t
            }
        }
        times
    }

    /** Photos taken on one calendar day in each of [years], chunked to stay below SQLite bind limits. Oldest first within a year. */
    suspend fun photosOn(years: List<Int>, md: MonthDay): Map<Int, List<Photo>> = withContext(Dispatchers.IO) {
        val ranges = years.mapNotNull { y -> dayRange(y, md)?.let { y to it } }
        if (ranges.isEmpty()) return@withContext emptyMap()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
        )
        // Some files have no capture time; fall back to when they were added (in seconds).
        val clause = "((${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?) OR " +
            "((${MediaStore.Images.Media.DATE_TAKEN} IS NULL OR ${MediaStore.Images.Media.DATE_TAKEN} = 0) AND " +
            "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?))"
        val (exclSql, exclArgs) = excludeSavedFlashbackSelection()
        val byYear = HashMap<Int, MutableList<Photo>>()
        for (chunk in ranges.chunked(MAX_SQL_RANGE_CHUNK)) {
            val dateSelection = List(chunk.size) { clause }.joinToString(" OR ")
            val selection = "($dateSelection) AND $exclSql"
            val args = ArrayList<String>(chunk.size * 4 + exclArgs.size)
            for ((_, r) in chunk) {
                args += r.first.toString()
                args += r.second.toString()
                args += (r.first / 1000L).toString()
                args += (r.second / 1000L).toString()
            }
            args.addAll(exclArgs)
            context.contentResolver.query(collection, projection, selection, args.toTypedArray(), null)?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val w = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val h = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val orient = c.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
                while (c.moveToNext()) {
                    val assetId = c.getLong(id)
                    val time = if (c.isNull(taken) || c.getLong(taken) == 0L) c.getLong(added) * 1000L else c.getLong(taken)
                    val year = chunk.firstOrNull { (_, r) -> time >= r.first && time < r.second }?.first ?: localDate(time).year
                    val rawW = c.getInt(w)
                    val rawH = c.getInt(h)
                    val deg = if (orient >= 0 && !c.isNull(orient)) c.getInt(orient) else 0
                    val swap = deg == 90 || deg == 270
                    byYear.getOrPut(year) { ArrayList() } += Photo(
                        key = "d:$assetId",
                        source = PhotoSource.DEVICE,
                        uri = ContentUris.withAppendedId(collection, assetId).toString(),
                        width = if (swap) rawH else rawW,
                        height = if (swap) rawW else rawH,
                        time = time,
                    )
                }
            }
        }
        byYear.mapValues { (_, list) -> list.sortedBy { it.time } }
    }

    /** Earliest photo on each (day, year) pair in a single batched MediaStore query (chunked <= 200). */
    suspend fun firstPhotosFor(targets: List<Pair<MonthDay, Int>>): Map<String, Photo> = withContext(Dispatchers.IO) {
        val ranges = targets.mapNotNull { (md, y) -> dayRange(y, md)?.let { md.key to it } }
        if (ranges.isEmpty()) return@withContext emptyMap()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
        )
        val clause = "((${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?) OR " +
            "((${MediaStore.Images.Media.DATE_TAKEN} IS NULL OR ${MediaStore.Images.Media.DATE_TAKEN} = 0) AND " +
            "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?))"
        val (exclSql, exclArgs) = excludeSavedFlashbackSelection()
        val earliest = HashMap<String, Photo>(ranges.size)
        for (chunk in ranges.chunked(MAX_SQL_RANGE_CHUNK)) {
            val dateSelection = List(chunk.size) { clause }.joinToString(" OR ")
            val selection = "($dateSelection) AND $exclSql"
            val args = ArrayList<String>(chunk.size * 4 + exclArgs.size)
            for ((_, r) in chunk) {
                args += r.first.toString()
                args += r.second.toString()
                args += (r.first / 1000L).toString()
                args += (r.second / 1000L).toString()
            }
            args.addAll(exclArgs)
            context.contentResolver.query(collection, projection, selection, args.toTypedArray(), null)?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val taken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val w = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val h = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val orient = c.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
                while (c.moveToNext()) {
                    val assetId = c.getLong(id)
                    val time = if (c.isNull(taken) || c.getLong(taken) == 0L) c.getLong(added) * 1000L else c.getLong(taken)
                    val key = chunk.firstOrNull { (_, r) -> time >= r.first && time < r.second }?.first ?: continue
                    val prev = earliest[key]
                    if (prev != null && prev.time <= time) continue
                    val rawW = c.getInt(w)
                    val rawH = c.getInt(h)
                    val deg = if (orient >= 0 && !c.isNull(orient)) c.getInt(orient) else 0
                    val swap = deg == 90 || deg == 270
                    earliest[key] = Photo(
                        key = "d:$assetId",
                        source = PhotoSource.DEVICE,
                        uri = ContentUris.withAppendedId(collection, assetId).toString(),
                        width = if (swap) rawH else rawW,
                        height = if (swap) rawW else rawH,
                        time = time,
                    )
                }
            }
        }
        earliest
    }

    /**
     * Copies photos into Pictures/Flashback/<album>, so the album shows up in the gallery
     * and Google Photos. Each file keeps its own type (JPEG, HEIC, PNG), and a photo already
     * in that folder is skipped, so saving again doesn't make copies. See [CAN_SAVE_TO_GALLERY].
     */
    suspend fun saveAlbum(name: String, photos: List<Photo>): Boolean = withContext(Dispatchers.IO) {
        if (!CAN_SAVE_TO_GALLERY || photos.isEmpty()) return@withContext false
        val safeName = sanitizeAlbumFolderName(name)
        val folder = "${Environment.DIRECTORY_PICTURES}/Flashback/$safeName"
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
            val ext = extensionForMimeType(type)
            val safeId = p.key.substringAfter(':').replace(Regex("""[^A-Za-z0-9_-]"""), "_")
            val file = "flashback_$safeId.$ext"
            if (file in there || "backthen_$safeId.$ext" in there) continue
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file)
                put(MediaStore.Images.Media.MIME_TYPE, type)
                put(MediaStore.Images.Media.RELATIVE_PATH, folder)
                put(MediaStore.Images.Media.DATE_TAKEN, p.time)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val target = resolver.insert(saveTo, values)
            if (target == null) { failed++; continue }
            val bytesCopied = runCatching {
                resolver.openOutputStream(target)?.use { out ->
                    resolver.openInputStream(source)?.use { it.copyTo(out) } ?: 0L
                } ?: 0L
            }.getOrDefault(0L)
            if (bytesCopied <= 0L) {
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

    private companion object {
        // 200 ranges * 4 bind args = 800 <= SQLite's 999 variable limit on API 26-29.
        const val MAX_SQL_RANGE_CHUNK = 200
    }
}
