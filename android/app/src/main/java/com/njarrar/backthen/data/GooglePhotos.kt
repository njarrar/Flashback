package com.njarrar.backthen.data

import android.content.Context
import android.net.Uri
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.PhotoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.OffsetDateTime

// Google Photos import through the Picker API.
//
// Since March 31, 2025 Google no longer lets third-party apps read a user's whole
// Google Photos library. The Picker API is the supported way in: the user picks photos
// in a Google screen and the app gets only those. Back Then saves a copy of each pick
// on the phone and folds it into the same day-by-day view.
// https://developers.google.com/photos/support/updates

const val PICKER_SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
private const val API = "https://photospicker.googleapis.com/v1"

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class PollingConfig(val pollInterval: String? = null, val timeoutIn: String? = null)

@Serializable
private data class PickerSession(
    val id: String,
    val pickerUri: String = "",
    val mediaItemsSet: Boolean = false,
    val pollingConfig: PollingConfig? = null,
)

@Serializable
private data class MediaFileMetadata(val width: Int? = null, val height: Int? = null)

@Serializable
private data class MediaFile(val baseUrl: String, val mimeType: String = "", val mediaFileMetadata: MediaFileMetadata? = null)

@Serializable
private data class PickedItem(val id: String, val createTime: String, val type: String = "", val mediaFile: MediaFile)

@Serializable
private data class PickedPage(val mediaItems: List<PickedItem> = emptyList(), val nextPageToken: String? = null)

/** "5s" or "3.5s" to ms. */
private fun seconds(s: String?, fallback: Long) =
    s?.removeSuffix("s")?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: fallback

/** True for HTTPS links on the Google hosts the Picker API uses. Only these get the sign-in token. */
fun isGoogleHttps(url: String): Boolean {
    val u = runCatching { URL(url) }.getOrNull() ?: return false
    val host = u.host.lowercase()
    return u.protocol == "https" &&
        (host == "photospicker.googleapis.com" || host.endsWith(".googleusercontent.com") || host.endsWith(".ggpht.com"))
}

private fun googleUrl(url: String): URL {
    require(isGoogleHttps(url)) { "Refusing to send the Google token to $url" }
    return URL(url)
}

class GooglePhotos(private val context: Context) {

    private val dir get() = File(context.filesDir, "google-photos").apply { mkdirs() }

    /** Opens a connection that carries the token, only to Google over HTTPS, with timeouts. */
    private fun open(url: String, token: String): HttpURLConnection {
        val conn = googleUrl(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        return conn
    }

    private fun call(token: String, path: String, method: String = "GET", body: String? = null): String {
        val conn = open("$API$path", token)
        try {
            conn.requestMethod = method
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("Google Photos returned $code: $text")
            return text
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Starts a picker session, hands its link to [open] (a browser tab or the Google
     * Photos app), waits for the user to tap Done, then saves the picked photos.
     * Returns null if the user never finishes picking.
     */
    suspend fun pick(token: String, open: (Uri) -> Unit, onProgress: (Int, Int) -> Unit): List<Photo>? = withContext(Dispatchers.IO) {
        val session = json.decodeFromString<PickerSession>(call(token, "/sessions", "POST", "{}"))
        val every = seconds(session.pollingConfig?.pollInterval, 3000)
        val giveUpAt = System.currentTimeMillis() + seconds(session.pollingConfig?.timeoutIn, 10 * 60 * 1000)
        withContext(Dispatchers.Main) { open(Uri.parse("${session.pickerUri}/autoclose")) }

        try {
            var ready = false
            while (!ready && System.currentTimeMillis() < giveUpAt) {
                delay(every)
                ready = json.decodeFromString<PickerSession>(call(token, "/sessions/${session.id}")).mediaItemsSet
            }
            if (!ready) return@withContext null

            val items = ArrayList<PickedItem>()
            var pageToken: String? = null
            do {
                val q = "sessionId=${session.id}&pageSize=100" + (pageToken?.let { "&pageToken=$it" } ?: "")
                val page = json.decodeFromString<PickedPage>(call(token, "/mediaItems?$q"))
                items += page.mediaItems
                pageToken = page.nextPageToken
            } while (!pageToken.isNullOrEmpty())

            val photos = items.filter { it.type == "PHOTO" }
            photos.mapIndexed { n, item ->
                withContext(Dispatchers.Main) { onProgress(n, photos.size) }
                // Base URLs expire after an hour and need the token, so keep a local copy.
                val file = File(dir, item.id.replace(Regex("[^\\w-]"), "_") + ".jpg")
                if (!file.exists()) download("${item.mediaFile.baseUrl}=w2048-h2048", token, file)
                Photo(
                    key = "g:${item.id}",
                    source = PhotoSource.GOOGLE,
                    uri = Uri.fromFile(file).toString(),
                    width = item.mediaFile.mediaFileMetadata?.width ?: 1,
                    height = item.mediaFile.mediaFileMetadata?.height ?: 1,
                    time = runCatching { Instant.parse(item.createTime).toEpochMilli() }
                        .getOrElse {
                            runCatching { OffsetDateTime.parse(item.createTime).toInstant().toEpochMilli() }
                                .getOrDefault(System.currentTimeMillis())
                        },
                )
            }
        } finally {
            runCatching { call(token, "/sessions/${session.id}", "DELETE") }
        }
    }

    private fun download(url: String, token: String, file: File) {
        val conn = open(url, token)
        val tmp = File(file.path + ".part")
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("Download failed: ${conn.responseCode}")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            runCatching {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.onFailure {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    fun deleteAll() {
        dir.deleteRecursively()
    }
}
