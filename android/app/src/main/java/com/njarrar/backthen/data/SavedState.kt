package com.njarrar.backthen.data

import android.content.Context
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.DayIndex
import com.njarrar.backthen.core.Photo
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class Sources(val device: Boolean = false)

@Serializable
data class Reminder(val on: Boolean = false, val hour: Int = 9, val minute: Int = 0)

/** Places you go back to: shows photos from a city when you're there again. [asked]: the app asked once, whatever the answer. */
@Serializable
data class PlaceSetting(val on: Boolean = false, val asked: Boolean = false)

/** How Today lays out each year: all years in mosaics, one year at a time, or a deck of year cards. */
@Serializable
enum class YearLayout { MOSAIC, STRIP, CARDS }

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
    /** Timezone ID used when [deviceIndex] and [placeIndex] were built, so travel invalidates them. */
    val indexZone: String? = null,
    /** Days the user hid, as [com.njarrar.backthen.core.MonthDay.key]. They don't come up on their own or in reminders. */
    val hiddenDates: Set<String> = emptySet(),
    val layout: YearLayout = YearLayout.MOSAIC,
    val places: PlaceSetting = PlaceSetting(),
    /** Photo days per city, from photo locations. Kept on the phone like everything else. */
    val placeIndex: Map<Int, Map<String, Int>> = emptyMap(),
    /** The city whose card was closed, until the phone is somewhere else. */
    val placeDismissed: Int? = null,
    /** Checksum of cities.tsv used when [placeIndex] was built. */
    val citiesHash: String? = null,
    /** Optional user-selected home city override (city row in cities.tsv). */
    val customHomeCity: Int? = null,
    /** Epoch day when the daily reminder last fired, preventing duplicate notifications. */
    val lastNotifiedEpochDay: Long? = null,
)

/** Photo counts per day in the phone's library, leaving out hidden days. */
fun visibleIndex(s: SavedState): DayIndex = if (s.sources.device) s.deviceIndex - s.hiddenDates else emptyMap()

/** Stores [SavedState] as one JSON file in app storage. */
class StateFile(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "state.json"))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    /**
     * The saved state, or a fresh one if there is none. A file with invalid JSON syntax is kept
     * as a timestamped `state.bad.<ts>.json` first, since the next save would write over it.
     * Single corrupt photos/albums are dropped without losing the rest of the state.
     */
    fun read(): SavedState = synchronized(fileLock) {
        if (!file.exists()) return SavedState()
        val text = try {
            file.readText()
        } catch (_: IOException) {
            return SavedState()
        }
        // Fast path: stream directly when legacy GOOGLE entries are absent.
        if ("\"GOOGLE\"" !in text) {
            try {
                return json.decodeFromString(SavedState.serializer(), text)
            } catch (_: SerializationException) {
                // Fall through to per-field lossy decoding.
            }
        }
        try {
            val root = json.parseToJsonElement(text)
            decodeLossy(json, root)
        } catch (_: SerializationException) {
            backupBadFile()
            SavedState()
        }
    }

    /** Writes the whole state atomically with fsync. A failed write leaves the last good file in place. */
    fun write(state: SavedState) = synchronized(fileLock) {
        val tmp = File(file.path + ".tmp")
        runCatching {
            file.parentFile?.mkdirs()
            val bytes = json.encodeToString(SavedState.serializer(), state).toByteArray(Charsets.UTF_8)
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                runCatching { out.fd.sync() }
            }
            runCatching {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.onFailure {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { tmp.delete() }
    }

    /** Atomically reads, transforms, and writes [SavedState] under the process-wide lock. */
    fun update(change: (SavedState) -> SavedState): SavedState = synchronized(fileLock) {
        val next = change(read())
        write(next)
        next
    }

    private fun backupBadFile() {
        val ts = System.currentTimeMillis()
        var backup = File(file.parentFile, "state.bad.$ts.json")
        var suffix = 1
        while (backup.exists()) {
            backup = File(file.parentFile, "state.bad.$ts.$suffix.json")
            suffix++
        }
        runCatching {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.onFailure {
            runCatching { file.renameTo(backup) }
        }
    }

    companion object {
        // Process-wide lock so AppViewModel, TodayWidget, and ReminderWorker never race on state.json.
        private val fileLock = Any()
    }
}

/**
 * Per-field and per-item resilient decoder matching iOS's Lossy decoding: one corrupt photo,
 * album, or unexpected field type never wipes the rest of the user's saved state.
 */
internal fun decodeLossy(json: Json, root: JsonElement): SavedState {
    val obj = root as? JsonObject ?: throw SerializationException("SavedState root is not a JSON object")
    val cleaned = dropGooglePhotos(obj) as JsonObject
    fun <T> field(name: String, deserializer: DeserializationStrategy<T>, default: T): T =
        cleaned[name]?.let { runCatching { json.decodeFromJsonElement(deserializer, it) }.getOrNull() } ?: default
    fun lossyPhotos(el: JsonElement?): List<Photo> =
        (el as? JsonArray).orEmpty().mapNotNull { item ->
            runCatching { json.decodeFromJsonElement(Photo.serializer(), item) }.getOrNull()
        }
    fun lossyAlbums(el: JsonElement?): List<Album> =
        (el as? JsonArray).orEmpty().mapNotNull { item ->
            val aObj = item as? JsonObject ?: return@mapNotNull null
            val id = (aObj["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            val name = (aObj["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            val createdAt = (aObj["createdAt"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
            Album(id = id, name = name, photos = lossyPhotos(aObj["photos"]), createdAt = createdAt)
        }
    val dayIndexSerializer = MapSerializer(String.serializer(), MapSerializer(Int.serializer(), Int.serializer()))
    val placeIndexSerializer = MapSerializer(Int.serializer(), MapSerializer(String.serializer(), Int.serializer()))
    return SavedState(
        onboarded = field("onboarded", Boolean.serializer(), false),
        sources = field("sources", Sources.serializer(), Sources()),
        reminder = field("reminder", Reminder.serializer(), Reminder()),
        favorites = lossyPhotos(cleaned["favorites"]),
        albums = lossyAlbums(cleaned["albums"]),
        deviceIndex = field("deviceIndex", dayIndexSerializer, emptyMap()),
        librarySignature = field("librarySignature", LibrarySignature.serializer().nullable, null),
        indexZone = field("indexZone", String.serializer().nullable, null),
        hiddenDates = field("hiddenDates", SetSerializer(String.serializer()), emptySet()),
        layout = field("layout", YearLayout.serializer(), YearLayout.MOSAIC),
        places = field("places", PlaceSetting.serializer(), PlaceSetting()),
        placeIndex = field("placeIndex", placeIndexSerializer, emptyMap()),
        placeDismissed = field("placeDismissed", Int.serializer().nullable, null),
        citiesHash = field("citiesHash", String.serializer().nullable, null),
        customHomeCity = field("customHomeCity", Int.serializer().nullable, null),
        lastNotifiedEpochDay = field("lastNotifiedEpochDay", Long.serializer().nullable, null),
    )
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
