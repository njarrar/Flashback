package com.njarrar.backthen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.DayIndex
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.YearGroup
import com.njarrar.backthen.core.buildDayIndex
import com.njarrar.backthen.core.favoriteCover
import com.njarrar.backthen.core.shortLabel
import com.njarrar.backthen.core.yearsForDay
import com.njarrar.backthen.data.DeviceLibrary
import com.njarrar.backthen.data.Reminder
import com.njarrar.backthen.data.Reminders
import com.njarrar.backthen.data.SavedState
import com.njarrar.backthen.data.Sources
import com.njarrar.backthen.data.StateFile
import com.njarrar.backthen.data.YearLayout
import com.njarrar.backthen.data.visibleIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Viewer(val photos: List<Photo>, val start: Int)

/** App state: what is saved on disk, plus what only lives while the app runs. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val library = DeviceLibrary(app)
    private val stateFile = StateFile(app)

    private val _saved = MutableStateFlow(stateFile.read())
    val saved: StateFlow<SavedState> = _saved.asStateFlow()

    val day = MutableStateFlow(MonthDay.today())
    /** What today was when the app last checked, so it can tell when midnight has passed. */
    private var lastToday = MonthDay.today()
    val indexing = MutableStateFlow(false)
    val viewer = MutableStateFlow<Viewer?>(null)
    val toast = MutableStateFlow<String?>(null)
    /** Fires when a reminder opens a day, so the app switches to Today. */
    val openToday = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Debug screenshots only: the scene to show, see Demo.kt. */
    val demoScene = MutableStateFlow<String?>(null)
    /** The last day Today showed, so it can come back with photos in place. */
    var lastDay: Pair<MonthDay, List<YearGroup>>? = null
    private var toastJob: Job? = null
    private var refreshJob: Job? = null
    // One write at a time, in order, so an older snapshot never lands after a newer one.
    private val writer = Dispatchers.IO.limitedParallelism(1)

    // The last index built and what it was built from, so screens don't rebuild it on every frame.
    @Volatile
    private var cachedIndexInputs: IndexInputs? = null
    @Volatile
    private var cachedIndex: DayIndex = emptyMap()

    private data class IndexInputs(
        val sources: Sources,
        val deviceIndex: Map<String, Map<Int, Int>>,
        val hiddenDates: Set<String>,
    )

    internal fun updateSaved(change: (SavedState) -> SavedState) = update(change)

    private fun update(change: (SavedState) -> SavedState) {
        _saved.update(change)
        val snapshot = _saved.value
        viewModelScope.launch(writer) { stateFile.write(snapshot) }
    }

    /** After midnight, Today moves on to the new day, unless the user had picked another day. */
    fun catchUpToToday() {
        val now = MonthDay.today()
        if (now == lastToday) return
        if (day.value == lastToday) day.value = now
        lastToday = now
    }

    /** A tap on the daily reminder: show that day on Today, over any open photo. */
    fun openDay(md: MonthDay) {
        day.value = md
        viewer.value = null
        openToday.tryEmit(Unit)
    }

    /** Photo counts per day in the phone's library. Hidden days count as empty. */
    fun index(s: SavedState = _saved.value): DayIndex {
        val inputs = IndexInputs(s.sources, s.deviceIndex, s.hiddenDates)
        if (inputs == cachedIndexInputs) return cachedIndex
        return visibleIndex(s).also {
            cachedIndexInputs = inputs
            cachedIndex = it
        }
    }

    /** Rebuilds the phone's day index. Skips the full scan when the library looks the same, unless [force]. */
    fun refreshIndex(force: Boolean = false) {
        if (!library.hasAccess() || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val sig = runCatching { library.signature() }.getOrNull()
            if (!force && _saved.value.deviceIndex.isNotEmpty() && library.isUnchanged(sig)) return@launch
            indexing.value = true
            try {
                val index = buildDayIndex(library.readAllTimes())
                library.recordSignature(sig)
                if (index != _saved.value.deviceIndex) {
                    update { it.copy(deviceIndex = index) }
                }
            } finally {
                indexing.value = false
            }
        }
    }

    suspend fun photosFor(md: MonthDay): List<YearGroup> {
        val s = _saved.value
        val years = yearsForDay(index(s), md)
        if (years.isEmpty()) return emptyList()
        val byYear = if (s.sources.device) runCatching { library.photosOn(years, md) }.getOrDefault(emptyMap()) else emptyMap()
        return years.map { year -> YearGroup(year, byYear[year].orEmpty().sortedBy { it.time }) }
            .filter { it.photos.isNotEmpty() }
    }

    /**
     * The photo the calendar shows for a day: a favorite from that day if there is one,
     * else the first photo of the newest year. Null for a day with no photos.
     */
    suspend fun coverFor(md: MonthDay): Photo? {
        val s = _saved.value
        val index = index(s)
        // Kept until the index or the favorites change.
        val key = index to s.favorites
        if (coversFor != key) { coversFor = key; covers.clear() }
        covers[md.key]?.let { return it }
        val years = yearsForDay(index, md)
        val newest = years.firstOrNull() ?: return null
        val found = favoriteCover(s.favorites, md, years)
            ?: if (s.sources.device) runCatching { library.photosOn(listOf(newest), md)[newest]?.firstOrNull() }.getOrNull() else null
        if (found != null && coversFor == key) covers[md.key] = found
        return found
    }
    private var coversFor: Any? = null
    private val covers = HashMap<String, Photo>()

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
        say(L.s(if (on) "toast_favorited" else "toast_unfavorited"))
    }

    fun createAlbum(name: String, photos: List<Photo>): Album {
        val album = Album("a${System.currentTimeMillis()}", name, photos, System.currentTimeMillis())
        update { it.copy(albums = listOf(album) + it.albums) }
        say(L.s("toast_album_created"))
        return album
    }

    fun addToAlbum(id: String, photos: List<Photo>) {
        update { s ->
            s.copy(albums = s.albums.map { a ->
                if (a.id != id) a else a.copy(photos = a.photos + photos.filter { p -> a.photos.none { it.key == p.key } })
            })
        }
        say(L.s("toast_added_to", "name" to (_saved.value.albums.firstOrNull { it.id == id }?.name ?: "")))
    }

    fun renameAlbum(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update { s -> s.copy(albums = s.albums.map { if (it.id == id) it.copy(name = trimmed) else it }) }
    }

    fun deleteAlbum(id: String) = update { s -> s.copy(albums = s.albums.filterNot { it.id == id }) }

    /** Copies photos into Pictures/Back Then/<name>. */
    fun saveToGallery(name: String, photos: List<Photo>) = viewModelScope.launch {
        val ok = runCatching { library.saveAlbum(name, photos) }.getOrDefault(false)
        say(L.s(if (ok) "toast_saved_gallery" else "toast_save_failed"))
    }

    fun setHidden(md: MonthDay, hidden: Boolean) {
        update { s -> s.copy(hiddenDates = if (hidden) s.hiddenDates + md.key else s.hiddenDates - md.key) }
        say(L.s(if (hidden) "toast_day_hidden" else "toast_day_shown", "day" to md.shortLabel()))
    }

    fun setLayout(layout: YearLayout) = update { it.copy(layout = layout) }
}
