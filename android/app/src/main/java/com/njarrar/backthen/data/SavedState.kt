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

/** Places you go back to: shows photos from a city when you're there again. [asked]: the app asked once, whatever the answer. */
@Serializable
data class PlaceSetting(val on: Boolean = false, val asked: Boolean = false)

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
    /** The library as it was when [deviceIndex] was built. */
    val librarySignature: LibrarySignature? = null,
    /** Days the user hid, as [com.njarrar.backthen.core.MonthDay.key]. They don't come up on their own or in reminders. */
    val hiddenDates: Set<String> = emptySet(),
    val layout: YearLayout = YearLayout.MOSAIC,
    val places: PlaceSetting = PlaceSetting(),
    /** Photo days per city, from photo locations. Kept on the phone like everything else. */
    val placeIndex: Map<Int, Map<String, Int>> = emptyMap(),
    /** The city whose card was closed, until the phone is somewhere else. */
    val placeDismissed: Int? = null,
)

/** Photo counts per day in the phone's library, leaving out hidden days. */
fun visibleIndex(s: SavedState): DayIndex = if (s.sources.device) s.deviceIndex - s.hiddenDates else emptyMap()

/** Stores [SavedState] as one JSON file in app storage. */
class StateFile(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "state.json"))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    /**
     * The saved state, or a fresh one if there is none. A file that won't load is kept as
     * state.bad.json first, since the next save would write over it and lose every album.
     */
    @Synchronized
    fun read(): SavedState {
        if (!file.exists()) return SavedState()
        return runCatching {
            json.decodeFromJsonElement(SavedState.serializer(), dropGooglePhotos(json.parseToJsonElement(file.readText())))
        }.getOrElse {
            runCatching { file.renameTo(File(file.parentFile, "state.bad.json")) }
            SavedState()
        }
    }

    /** Writes the whole state. A failed write (a full disk) leaves the last good file in place. */
    @Synchronized
    fun write(state: SavedState) {
        val tmp = File(file.path + ".tmp")
        runCatching {
            tmp.writeText(json.encodeToString(SavedState.serializer(), state))
            runCatching {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.onFailure {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { tmp.delete() }
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
