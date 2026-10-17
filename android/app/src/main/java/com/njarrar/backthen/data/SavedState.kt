package com.njarrar.backthen.data

import android.content.Context
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.DayIndex
import com.njarrar.backthen.core.Photo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class Sources(val device: Boolean = false)

@Serializable
data class Reminder(val on: Boolean = false, val hour: Int = 9, val minute: Int = 0)

/** How Today lays out each year: all years in mosaics, or one year at a time. */
@Serializable
enum class YearLayout { MOSAIC, STRIP }

/** Everything the app keeps between launches. */
@Serializable
data class SavedState(
    val onboarded: Boolean = false,
    val sources: Sources = Sources(),
    val reminder: Reminder = Reminder(),
    val favorites: List<Photo> = emptyList(),
    val albums: List<Album> = emptyList(),
    /** Cached photo counts per day for the phone's library. */
    val deviceIndex: Map<String, Map<Int, Int>> = emptyMap(),
    /** Days the user hid, as [com.njarrar.backthen.core.MonthDay.key]. They don't come up on their own or in reminders. */
    val hiddenDates: Set<String> = emptySet(),
    val layout: YearLayout = YearLayout.MOSAIC,
)

/** Photo counts per day in the phone's library, leaving out hidden days. */
fun visibleIndex(s: SavedState): DayIndex = if (s.sources.device) s.deviceIndex - s.hiddenDates else emptyMap()

/** Stores [SavedState] as one JSON file in app storage. */
class StateFile(context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    fun read(): SavedState = runCatching {
        json.decodeFromJsonElement(SavedState.serializer(), dropGooglePhotos(json.parseToJsonElement(file.readText())))
    }.getOrDefault(SavedState())

    @Synchronized
    fun write(state: SavedState) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(SavedState.serializer(), state))
        runCatching {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/**
 * Version 1.8 and earlier could import from Google Photos. Leaves those photos out of
 * favorites and albums in an old save, so the rest of it still loads.
 */
internal fun dropGooglePhotos(state: JsonElement): JsonElement {
    if (state !is JsonObject) return state
    fun photos(list: JsonElement?) = JsonArray((list as? JsonArray).orEmpty().filter {
        ((it as? JsonObject)?.get("source") as? JsonPrimitive)?.content != "GOOGLE"
    })
    val out = state.toMutableMap()
    state["favorites"]?.let { out["favorites"] = photos(it) }
    (state["albums"] as? JsonArray)?.let { albums ->
        out["albums"] = JsonArray(albums.map { a -> if (a is JsonObject) JsonObject(a + ("photos" to photos(a["photos"]))) else a })
    }
    return JsonObject(out)
}
