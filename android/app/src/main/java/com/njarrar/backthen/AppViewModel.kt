package com.njarrar.backthen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.DayIndex
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.PhotoSource
import com.njarrar.backthen.core.YearGroup
import com.njarrar.backthen.core.buildDayIndex
import com.njarrar.backthen.core.dropDuplicates
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.shortLabel
import com.njarrar.backthen.core.yearsForDay
import com.njarrar.backthen.data.DeviceLibrary
import com.njarrar.backthen.data.GooglePhotos
import com.njarrar.backthen.data.Reminder
import com.njarrar.backthen.data.Reminders
import com.njarrar.backthen.data.SavedState
import com.njarrar.backthen.data.StateFile
import com.njarrar.backthen.data.YearLayout
import com.njarrar.backthen.data.visibleIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Viewer(val photos: List<Photo>, val start: Int)

/** Progress of a Google Photos import: waiting for picks, or saving n of total. */
data class GoogleImport(val done: Int = 0, val total: Int = 0)

/** App state: what is saved on disk, plus what only lives while the app runs. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val library = DeviceLibrary(app)
    private val google = GooglePhotos(app)
    private val stateFile = StateFile(app)

    private val _saved = MutableStateFlow(stateFile.read())
    val saved: StateFlow<SavedState> = _saved.asStateFlow()

    val day = MutableStateFlow(MonthDay.today())
    val indexing = MutableStateFlow(false)
    val viewer = MutableStateFlow<Viewer?>(null)
    val toast = MutableStateFlow<String?>(null)
    val googleImport = MutableStateFlow<GoogleImport?>(null)
    /** Debug screenshots only: the scene to show, see Demo.kt. */
    val demoScene = MutableStateFlow<String?>(null)
    private var toastJob: Job? = null
    private var importJob: Job? = null
    // One write at a time, in order, so an older snapshot never lands after a newer one.
    private val writer = Dispatchers.IO.limitedParallelism(1)

    internal fun updateSaved(change: (SavedState) -> SavedState) = update(change)

    private fun update(change: (SavedState) -> SavedState) {
        _saved.update(change)
        val snapshot = _saved.value
        viewModelScope.launch(writer) { stateFile.write(snapshot) }
    }

    /** Photo counts per day across every connected source. Hidden days count as empty. */
    fun index(s: SavedState = _saved.value): DayIndex = visibleIndex(s)

    fun refreshIndex() {
        if (!library.hasAccess()) return
        viewModelScope.launch {
            indexing.value = true
            try {
                val index = buildDayIndex(library.readAllTimes())
                update { it.copy(deviceIndex = index) }
            } finally {
                indexing.value = false
            }
        }
    }

    suspend fun photosFor(md: MonthDay): List<YearGroup> {
        val s = _saved.value
        return yearsForDay(index(s), md).map { year ->
            val device = if (s.sources.device) runCatching { library.photosOn(year, md) }.getOrDefault(emptyList()) else emptyList()
            val google = if (s.sources.google) {
                s.googlePhotos.filter { p ->
                    val d = localDate(p.time)
                    d.year == year && d.monthValue - 1 == md.month && d.dayOfMonth == md.day
                }
            } else emptyList()
            YearGroup(year, dropDuplicates(device, google).sortedBy { it.time })
        }.filter { it.photos.isNotEmpty() }
    }

    fun say(message: String) {
        toastJob?.cancel()
        toast.value = message
        toastJob = viewModelScope.launch {
            delay(2200)
            toast.value = null
        }
    }

    fun connectDevice() {
        update { it.copy(sources = it.sources.copy(device = true)) }
        refreshIndex()
    }

    fun finishOnboarding(reminderOn: Boolean) {
        update { it.copy(onboarded = true, reminder = it.reminder.copy(on = reminderOn)) }
        applyReminder()
    }

    fun setReminder(change: (Reminder) -> Reminder) {
        update { it.copy(reminder = change(it.reminder)) }
        applyReminder()
    }

    private fun applyReminder() {
        val r = _saved.value.reminder
        if (r.on) Reminders.schedule(getApplication(), r.hour, r.minute) else Reminders.cancel(getApplication())
    }

    fun isFavorite(key: String) = _saved.value.favorites.any { it.key == key }

    fun toggleFavorite(photo: Photo) {
        val on = !isFavorite(photo.key)
        update { s -> s.copy(favorites = if (on) listOf(photo) + s.favorites else s.favorites.filterNot { it.key == photo.key }) }
        say(if (on) "Added to Favorites" else "Removed from Favorites")
    }

    fun createAlbum(name: String, photos: List<Photo>): Album {
        val album = Album("a${System.currentTimeMillis()}", name, photos, System.currentTimeMillis())
        update { it.copy(albums = listOf(album) + it.albums) }
        say("Album created")
        return album
    }

    fun addToAlbum(id: String, photos: List<Photo>) {
        update { s ->
            s.copy(albums = s.albums.map { a ->
                if (a.id != id) a else a.copy(photos = a.photos + photos.filter { p -> a.photos.none { it.key == p.key } })
            })
        }
        say("Added to ${_saved.value.albums.firstOrNull { it.id == id }?.name ?: "album"}")
    }

    fun deleteAlbum(id: String) = update { s -> s.copy(albums = s.albums.filterNot { it.id == id }) }

    /** Copies photos into Pictures/Back Then/<name>. */
    fun saveToGallery(name: String, photos: List<Photo>) = viewModelScope.launch {
        val ok = runCatching { library.saveAlbum(name, photos) }.getOrDefault(false)
        say(if (ok) "Saved to Pictures/Back Then" else "Could not save every photo")
    }

    fun isHidden(md: MonthDay) = md.key in _saved.value.hiddenDates

    fun setHidden(md: MonthDay, hidden: Boolean) {
        update { s -> s.copy(hiddenDates = if (hidden) s.hiddenDates + md.key else s.hiddenDates - md.key) }
        say(if (hidden) "${md.shortLabel()} hidden" else "${md.shortLabel()} shown again")
    }

    fun setLayout(layout: YearLayout) = update { it.copy(layout = layout) }

    /** Runs the Google picker with a token from Google sign-in, then adds the picks. */
    fun importFromGoogle(token: String, open: (Uri) -> Unit) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            googleImport.value = GoogleImport()
            try {
                val photos = google.pick(token, open) { done, total -> googleImport.value = GoogleImport(done, total) }
                if (photos == null) {
                    say("No photos picked")
                } else {
                    update { s ->
                        val known = s.googlePhotos.map { it.key }.toSet()
                        s.copy(googlePhotos = s.googlePhotos + photos.filter { it.key !in known }, sources = s.sources.copy(google = true))
                    }
                    say(if (photos.isEmpty()) "Google Photos connected" else "Added ${photos.size} from Google Photos")
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) say("Could not reach Google Photos")
            } finally {
                googleImport.value = null
            }
        }
    }

    fun cancelGoogleImport() {
        importJob?.cancel()
        googleImport.value = null
    }

    fun disconnectGoogle() {
        google.deleteAll()
        update { s ->
            s.copy(
                googlePhotos = emptyList(),
                sources = s.sources.copy(google = false),
                favorites = s.favorites.filter { it.source != PhotoSource.GOOGLE },
                albums = s.albums.map { a -> a.copy(photos = a.photos.filter { it.source != PhotoSource.GOOGLE }) },
            )
        }
        say("Google Photos disconnected")
    }
}
