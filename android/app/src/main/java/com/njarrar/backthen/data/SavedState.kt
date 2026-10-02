package com.njarrar.backthen.data

import android.content.Context
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.Photo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Sources(val device: Boolean = false, val google: Boolean = false)

@Serializable
data class Reminder(val on: Boolean = false, val hour: Int = 9, val minute: Int = 0)

/** Everything the app keeps between launches. */
@Serializable
data class SavedState(
    val onboarded: Boolean = false,
    val sources: Sources = Sources(),
    val reminder: Reminder = Reminder(),
    val favorites: List<Photo> = emptyList(),
    val albums: List<Album> = emptyList(),
    val googlePhotos: List<Photo> = emptyList(),
    /** Cached photo counts per day for the phone's library. */
    val deviceIndex: Map<String, Map<Int, Int>> = emptyMap(),
)

/** Stores [SavedState] as one JSON file in app storage. */
class StateFile(context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(): SavedState = runCatching { json.decodeFromString<SavedState>(file.readText()) }.getOrDefault(SavedState())

    @Synchronized
    fun write(state: SavedState) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(SavedState.serializer(), state))
        tmp.renameTo(file)
    }
}
