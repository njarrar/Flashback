package com.njarrar.backthen

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.DAYS_IN_MONTH
import com.njarrar.backthen.core.DayIndex
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.PlaceCard
import com.njarrar.backthen.core.Trip
import com.njarrar.backthen.core.YearGroup
import com.njarrar.backthen.core.buildDayIndex
import com.njarrar.backthen.core.favoriteCover
import com.njarrar.backthen.core.makePlaceCard
import com.njarrar.backthen.core.shortLabel
import com.njarrar.backthen.core.yearsForDay
import com.njarrar.backthen.data.DeviceLibrary
import com.njarrar.backthen.data.PlaceLibrary
import com.njarrar.backthen.data.Reminder
import com.njarrar.backthen.data.Reminders
import com.njarrar.backthen.data.SavedState
import com.njarrar.backthen.data.StateFile
import com.njarrar.backthen.data.YearLayout
import com.njarrar.backthen.data.visibleIndex
import com.njarrar.backthen.widget.TodayWidget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class Viewer(val photos: List<Photo>, val start: Int)

/** The reel editor, open with these photos. */
data class ReelOpen(val photos: List<Photo>, val tab: com.njarrar.backthen.ui.ReelTab = com.njarrar.backthen.ui.ReelTab.STAMP)

enum class ToastKind { Success, Info, Error }

data class ToastState(
    val text: String,
    val kind: ToastKind = ToastKind.Success,
    val undoLabel: String? = null,
    val undo: (() -> Unit)? = null,
)

/** App state: what is saved on disk, plus what only lives while the app runs. */
class AppViewModel(
    app: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, SavedStateHandle())

    val library = DeviceLibrary(app)
    val places = PlaceLibrary(app)
    private val stateFile = StateFile(app)
    internal val stateReady = CompletableDeferred<Unit>()

    private val _ready = MutableStateFlow(false)
    /** True once [SavedState] has finished loading off the main thread. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _saved = MutableStateFlow(SavedState())
    val saved: StateFlow<SavedState> = _saved.asStateFlow()

    private val _partialAccess = MutableStateFlow(library.hasPartialAccess())
    val partialAccess: StateFlow<Boolean> = _partialAccess.asStateFlow()

    private val _notificationsGranted = MutableStateFlow(hasNotificationAccess())
    val notificationsGranted: StateFlow<Boolean> = _notificationsGranted.asStateFlow()

    private val _hasCities = MutableStateFlow(false)
    val hasCities: StateFlow<Boolean> = _hasCities.asStateFlow()

    // A day restored after the process was killed only counts if it was saved today.
    // The handle also holds the launch intent's extras, which any app can set to any type,
    // so read it as Any and keep only text.
    val day = MutableStateFlow(
        savedText(KEY_DAY)
            ?.takeIf { savedText(KEY_SAVED_ON) == MonthDay.today().key }
            ?.let(MonthDay::fromKey) ?: MonthDay.today()
    )

    private fun savedText(key: String): String? = savedStateHandle.get<Any?>(key) as? String
    /** What today was when the app last checked, so it can tell when midnight has passed. */
    private var lastToday = MonthDay.today()
    val indexing = MutableStateFlow(false)
    private val _placesScanning = MutableStateFlow(false)
    val placesScanning: StateFlow<Boolean> = _placesScanning.asStateFlow()
    /** The card Today shows in a city you've been to before, away from home. */
    val placeCard = MutableStateFlow<PlaceCard?>(null)
    /** The city the phone is in, when places are on (Main-confined). */
    private var here: Int? = null
    private var lastDismissedPlaceCard: PlaceCard? = null
    private var placesJob: Job? = null
    private var checkPlaceJob: Job? = null
    val viewer = MutableStateFlow<Viewer?>(null)
    val reel = MutableStateFlow<ReelOpen?>(null)

    private val _toastState = MutableStateFlow<ToastState?>(null)
    val toastState: StateFlow<ToastState?> = _toastState.asStateFlow()

    /** Fires when a reminder or widget opens a day, buffered until the UI collects it. */
    private val _openToday = Channel<Unit>(Channel.CONFLATED)
    val openToday = _openToday.receiveAsFlow()
    private val _openTodayToken = MutableStateFlow(0)
    /** Increments on every reminder/widget deep link so screens dismiss local sheets/overlays cleanly. */
    val openTodayToken: StateFlow<Int> = _openTodayToken.asStateFlow()

    /** Debug screenshots only: the scene to show, see Demo.kt. */
    val demoScene = MutableStateFlow<String?>(null)
    /** The last day Today showed, so it can come back with photos in place. */
    var lastDay: Pair<MonthDay, List<YearGroup>>? = null
    private var toastJob: Job? = null
    private var refreshJob: Job? = null

    // Application-scoped single-threaded persistence survives Activity finish and guarantees latest-state ordering.
    private val persistScope = (app as? BackThenApplication)?.appScope
        ?: CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val persistTrigger = Channel<Unit>(Channel.CONFLATED)
    private val pendingWidgetRefresh = AtomicBoolean(false)

    // Single immutable cache snapshot so concurrent readers never observe mismatched inputs/index.
    private class CachedIndex(
        val deviceEnabled: Boolean,
        val deviceIndexRef: Map<String, Map<Int, Int>>,
        val hiddenDatesRef: Set<String>,
        val index: DayIndex,
    )
    @Volatile
    private var cachedIndex: CachedIndex? = null

    private class FavKeysCache(val favsRef: List<Photo>, val keys: Set<String>)
    @Volatile
    private var favKeysCache: FavKeysCache? = null

    private class CoverCache(
        val indexRef: DayIndex,
        val favsRef: List<Photo>,
        val map: ConcurrentHashMap<String, Photo> = ConcurrentHashMap(),
        val empty: MutableSet<String> = ConcurrentHashMap.newKeySet(),
    )
    @Volatile
    private var coverCache: CoverCache? = null

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { stateFile.read() }
            _saved.value = loaded
            _ready.value = true
            stateReady.complete(Unit)
            if (loaded.reminder.on && loaded.indexZone != ZoneId.systemDefault().id) {
                applyReminder()
            }
            syncPermissionsNow()
        }
        persistScope.launch {
            for (ignored in persistTrigger) {
                val snapshot = _saved.value
                val refreshWidget = pendingWidgetRefresh.getAndSet(false)
                // The reminder worker writes lastNotifiedEpochDay on its own; keep its value.
                stateFile.update { disk -> snapshot.copy(lastNotifiedEpochDay = disk.lastNotifiedEpochDay ?: snapshot.lastNotifiedEpochDay) }
                if (refreshWidget) TodayWidget.refresh(getApplication())
            }
        }
        viewModelScope.launch {
            day.collect {
                savedStateHandle[KEY_DAY] = it.key
                savedStateHandle[KEY_SAVED_ON] = MonthDay.today().key
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            _hasCities.value = places.checkCitiesAvailable()
        }
    }

    override fun onCleared() {
        // Close the trigger channel so the appScope persistence coroutine finishes its final flush and releases this ViewModel.
        persistTrigger.close()
        super.onCleared()
    }

    private fun hasNotificationAccess(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Re-checks live OS permissions on resume so revocations in Settings never lock out recovery UI. */
    fun syncPermissions() {
        viewModelScope.launch {
            stateReady.await()
            syncPermissionsNow()
        }
    }

    private fun syncPermissionsNow() {
        val hasPhoto = library.hasAccess()
        _partialAccess.value = library.hasPartialAccess()
        _notificationsGranted.value = hasNotificationAccess()
        val cur = _saved.value
        if (cur.sources.device && !hasPhoto) {
            update(refreshWidget = true) { it.copy(sources = it.sources.copy(device = false)) }
        } else if (!cur.sources.device && cur.onboarded && hasPhoto) {
            update(refreshWidget = true) { it.copy(sources = it.sources.copy(device = true)) }
            refreshIndex(force = true)
        } else if (cur.onboarded && cur.sources.device && hasPhoto) {
            refreshIndex()
        }
        if (cur.places.on && !places.hasAccess()) {
            update { it.copy(places = it.places.copy(on = false)) }
            placesJob?.cancel()
            checkPlaceJob?.cancel()
            _placesScanning.value = false
            placeCard.value = null
        }
    }

    internal fun updateSaved(change: (SavedState) -> SavedState) = update(change = change)

    private fun update(refreshWidget: Boolean = false, change: (SavedState) -> SavedState) {
        _saved.update(change)
        if (refreshWidget) pendingWidgetRefresh.set(true)
        persistTrigger.trySend(Unit)
    }

    /** After midnight, Today moves on to the new day, unless the user had picked another day. */
    fun catchUpToToday() {
        val now = MonthDay.today()
        if (now == lastToday) return
        if (day.value == lastToday) day.value = now
        lastToday = now
    }

    /** A tap on the daily reminder or widget: show that day on Today, over any open photo or reel. */
    fun openDay(md: MonthDay) {
        // Advance lastToday before updating day so onResume()'s catchUpToToday() never overwrites yesterday's notification deep link.
        lastToday = MonthDay.today()
        day.value = md
        viewer.value = null
        reel.value = null
        _openToday.trySend(Unit)
        _openTodayToken.update { it + 1 }
    }

    /** Photo counts per day in the phone's library. Hidden days count as empty. */
    fun index(s: SavedState = _saved.value): DayIndex {
        val cur = cachedIndex
        if (cur != null &&
            cur.deviceEnabled == s.sources.device &&
            cur.deviceIndexRef === s.deviceIndex &&
            cur.hiddenDatesRef === s.hiddenDates
        ) return cur.index
        val built = visibleIndex(s)
        cachedIndex = CachedIndex(s.sources.device, s.deviceIndex, s.hiddenDates, built)
        return built
    }

    /** Rebuilds the phone's day index. Skips the full scan when the library and timezone match, unless [force]. */
    fun refreshIndex(force: Boolean = false) {
        if (!library.hasAccess()) return
        if (refreshJob?.isActive == true) {
            if (!force) return
            refreshJob?.cancel()
        }
        refreshJob = viewModelScope.launch {
            val sig = runCatching { library.signature() }.getOrNull()
            val saved = _saved.value
            val currentZone = ZoneId.systemDefault().id
            val zoneChanged = saved.indexZone != currentZone
            if (zoneChanged && saved.reminder.on) {
                applyReminder()
            }
            if (!force && !zoneChanged && saved.deviceIndex.isNotEmpty() && sig != null && sig == saved.librarySignature) {
                if (saved.places.on) {
                    val cities = places.cities()
                    val citiesChanged = cities != null && saved.citiesHash != cities.hash
                    if (saved.placeIndex.isEmpty() || citiesChanged) refreshPlaces() else checkPlace()
                }
                return@launch
            }
            indexing.value = true
            try {
                val index = buildDayIndex(library.readAllTimes())
                val cur = _saved.value
                if (index != cur.deviceIndex || sig != cur.librarySignature || currentZone != cur.indexZone) {
                    update(refreshWidget = true) {
                        it.copy(deviceIndex = index, librarySignature = sig, indexZone = currentZone)
                    }
                }
            } finally {
                // A forced refresh cancels this job after the new one has set indexing; leave it on then.
                if (refreshJob == null || refreshJob === coroutineContext[Job]) indexing.value = false
            }
            if (_saved.value.places.on) refreshPlaces()
        }
    }

    /** Turns places on or off, after the permission prompt. On needs the location permissions. */
    fun setPlaces(on: Boolean) {
        val allowed = places.hasAccess()
        val enabled = on && allowed
        update { it.copy(places = it.places.copy(asked = true, on = enabled)) }
        if (on && !allowed) showToast(L.s("toast_location_off"), ToastKind.Info)
        if (enabled) {
            refreshPlaces()
        } else {
            placesJob?.cancel()
            checkPlaceJob?.cancel()
            _placesScanning.value = false
            placeCard.value = null
        }
    }

    fun placesAsked() = update { it.copy(places = it.places.copy(asked = true)) }

    /** Overrides the inferred home city (or passes null to return to automatic home detection). */
    fun setHomeCity(cityId: Int?) {
        update { it.copy(customHomeCity = cityId, placeDismissed = null) }
        if (_saved.value.places.on) checkPlace()
    }

    /** Finds the city of every photo with a location, then checks where the phone is. */
    fun refreshPlaces() {
        if (!_saved.value.places.on || !library.hasAccess() || !places.hasAccess() || placesJob?.isActive == true) return
        placesJob = viewModelScope.launch {
            if (!places.checkCitiesAvailable()) return@launch
            val cities = places.cities() ?: return@launch
            _placesScanning.value = true
            try {
                val partial = library.hasPartialAccess()
                val built = runCatching { places.index(cities, partialAccess = partial) }.getOrNull()
                val cur = _saved.value
                val hashChanged = cur.citiesHash != null && cur.citiesHash != cities.hash
                if (built != null && (built != cur.placeIndex || cur.citiesHash != cities.hash)) {
                    update {
                        it.copy(
                            placeIndex = built,
                            citiesHash = cities.hash,
                            placeDismissed = if (hashChanged) null else it.placeDismissed,
                            customHomeCity = if (hashChanged) null else it.customHomeCity,
                        )
                    }
                }
            } finally {
                _placesScanning.value = false
            }
            findPlace()
        }
    }

    fun checkPlace() {
        checkPlaceJob?.cancel()
        checkPlaceJob = viewModelScope.launch { findPlace() }
    }

    /** Looks up the city the phone is in, and whether Today shows a card for it. */
    private suspend fun findPlace() {
        val cities = places.cities()
        val spot = if (_saved.value.places.on && cities != null) runCatching { places.currentSpot() }.getOrNull() else null
        if (cities == null || spot == null) { placeCard.value = null; return }
        val city = cities.city(spot.first, spot.second)
        here = city
        val cur = _saved.value
        val closed = cur.placeDismissed
        if (closed != null && closed != city) update { it.copy(placeDismissed = null) }
        placeCard.value = if (city != null && city == _saved.value.placeDismissed) {
            null
        } else {
            makePlaceCard(_saved.value.placeIndex, city, cities, customHomeCity = _saved.value.customHomeCity)
        }
    }

    fun dismissPlaceCard() {
        val card = placeCard.value
        lastDismissedPlaceCard = card
        update { it.copy(placeDismissed = here ?: card?.city?.id) }
        placeCard.value = null
        val text = card?.city?.name?.let { L.s("toast_day_hidden", "day" to it) } ?: L.s("close")
        showToast(text, ToastKind.Info, undoLabel = L.s("undo"), undo = { undoDismissPlaceCard() })
    }

    fun undoDismissPlaceCard() {
        val card = lastDismissedPlaceCard
        lastDismissedPlaceCard = null
        update { it.copy(placeDismissed = null) }
        if (card != null) {
            placeCard.value = card
        } else if (_saved.value.places.on) {
            checkPlace()
        }
    }

    suspend fun tripPhotos(city: Int, trip: Trip): List<Photo> = runCatching { places.photos(city, trip) }.getOrDefault(emptyList())

    suspend fun photosFor(md: MonthDay): List<YearGroup> {
        val s = _saved.value
        val years = yearsForDay(index(s), md)
        if (years.isEmpty()) return emptyList()
        val byYear = if (s.sources.device) runCatching { library.photosOn(years, md) }.getOrDefault(emptyMap()) else emptyMap()
        return years.map { year -> YearGroup(year, byYear[year].orEmpty().sortedBy { it.time }) }
            .filter { it.photos.isNotEmpty() }
    }

    private fun currentCoverCache(idx: DayIndex, favs: List<Photo>): CoverCache {
        val cur = coverCache
        if (cur != null && cur.indexRef === idx && cur.favsRef === favs) return cur
        return CoverCache(idx, favs).also { coverCache = it }
    }

    /**
     * Loads calendar covers for every day in [month] (0..11) using at most one batched MediaStore query,
     * backed by a thread-safe [ConcurrentHashMap] cache.
     */
    suspend fun coversForMonth(month: Int): Map<String, Photo?> {
        if (month !in 0..11) return emptyMap()
        val s = _saved.value
        val idx = index(s)
        val cache = currentCoverCache(idx, s.favorites)
        val out = LinkedHashMap<String, Photo?>(DAYS_IN_MONTH[month])
        val missing = ArrayList<Pair<MonthDay, Int>>()
        for (d in 1..DAYS_IN_MONTH[month]) {
            val md = MonthDay(month, d)
            val cached = cache.map[md.key]
            if (cached != null) {
                out[md.key] = cached
                continue
            }
            if (md.key in cache.empty) {
                out[md.key] = null
                continue
            }
            val years = yearsForDay(idx, md)
            val newest = years.firstOrNull()
            if (newest == null) {
                cache.empty += md.key
                out[md.key] = null
                continue
            }
            val fav = favoriteCover(s.favorites, md, years)
            if (fav != null) {
                cache.map[md.key] = fav
                out[md.key] = fav
            } else if (s.sources.device) {
                missing += md to newest
            } else {
                cache.empty += md.key
                out[md.key] = null
            }
        }
        if (missing.isNotEmpty()) {
            val fetched = runCatching { library.firstPhotosFor(missing) }.getOrDefault(emptyMap())
            for ((md, _) in missing) {
                val found = fetched[md.key]
                if (found != null) cache.map[md.key] = found else cache.empty += md.key
                out[md.key] = found
            }
        }
        return out
    }

    /** Opens the reel editor with these photos, two to [ReelPlan.MAX_PHOTOS]. Returns false when there are too few. */
    fun startReel(photos: List<Photo>): Boolean {
        if (photos.size < 2) { showToast(L.s("reel_need_two"), ToastKind.Info); return false }
        if (photos.size > com.njarrar.backthen.core.ReelPlan.MAX_PHOTOS) {
            showToast(L.p("reel_too_many", com.njarrar.backthen.core.ReelPlan.MAX_PHOTOS), ToastKind.Info)
        }
        reel.value = ReelOpen(photos)
        return true
    }

    fun showToast(
        text: String,
        kind: ToastKind = ToastKind.Success,
        undoLabel: String? = null,
        undo: (() -> Unit)? = null,
    ) {
        toastJob?.cancel()
        _toastState.value = ToastState(text, kind, undoLabel, undo)
        toastJob = viewModelScope.launch {
            delay(if (undo != null) 4500L else 2200L)
            _toastState.value = null
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _toastState.value = null
    }

    fun connectDevice() {
        _partialAccess.value = library.hasPartialAccess()
        update(refreshWidget = true) { it.copy(sources = it.sources.copy(device = true)) }
        refreshIndex(force = true)
    }

    fun finishOnboarding(reminderOn: Boolean) {
        _notificationsGranted.value = hasNotificationAccess()
        update(refreshWidget = true) { it.copy(onboarded = true, reminder = it.reminder.copy(on = reminderOn)) }
        applyReminder()
    }

    fun setReminder(change: (Reminder) -> Reminder) {
        _notificationsGranted.value = hasNotificationAccess()
        update { it.copy(reminder = change(it.reminder)) }
        applyReminder()
    }

    private fun applyReminder() {
        val r = _saved.value.reminder
        if (r.on) Reminders.schedule(getApplication(), r.hour, r.minute) else Reminders.cancel(getApplication())
    }

    fun isFavorite(key: String): Boolean {
        val favs = _saved.value.favorites
        val cur = favKeysCache
        val keys = if (cur != null && cur.favsRef === favs) {
            cur.keys
        } else {
            favs.mapTo(HashSet(favs.size)) { it.key }.also { favKeysCache = FavKeysCache(favs, it) }
        }
        return key in keys
    }

    fun toggleFavorite(photo: Photo) {
        val on = !isFavorite(photo.key)
        update { s -> s.copy(favorites = if (on) listOf(photo) + s.favorites else s.favorites.filterNot { it.key == photo.key }) }
        showToast(L.s(if (on) "toast_favorited" else "toast_unfavorited"), if (on) ToastKind.Success else ToastKind.Info)
    }

    fun createAlbum(name: String, photos: List<Photo>): Album {
        val cleanName = name.trim().ifBlank { L.s("album") }
        val unique = photos.distinctBy { it.key }
        val album = Album(UUID.randomUUID().toString(), cleanName, unique, System.currentTimeMillis())
        update { it.copy(albums = listOf(album) + it.albums) }
        showToast(L.s("toast_album_created"), ToastKind.Success)
        return album
    }

    fun addToAlbum(albumId: String, photos: List<Photo>): Int {
        val target = _saved.value.albums.firstOrNull { it.id == albumId } ?: return 0
        val unique = photos.distinctBy { it.key }
        val existingKeys = target.photos.mapTo(HashSet(target.photos.size)) { it.key }
        val fresh = unique.filter { it.key !in existingKeys }
        if (fresh.isEmpty()) {
            showToast(L.s("already_in_album", "name" to target.name), ToastKind.Info)
            return 0
        }
        var found = false
        var addedCount = 0
        var resolvedName = target.name
        update { s ->
            found = false
            addedCount = 0
            s.copy(albums = s.albums.map { a ->
                if (a.id != albumId) a else {
                    found = true
                    resolvedName = a.name
                    val currentKeys = a.photos.mapTo(HashSet(a.photos.size)) { it.key }
                    val delta = unique.filter { it.key !in currentKeys }
                    addedCount = delta.size
                    if (delta.isEmpty()) a else a.copy(photos = a.photos + delta)
                }
            })
        }
        if (!found) return 0
        if (addedCount == 0) {
            showToast(L.s("already_in_album", "name" to resolvedName), ToastKind.Info)
        } else {
            showToast(L.s("toast_added_to", "name" to resolvedName), ToastKind.Success)
        }
        return addedCount
    }

    fun removeFromAlbum(albumId: String, photoKey: String) {
        update { s ->
            s.copy(albums = s.albums.map { a ->
                if (a.id != albumId) a else a.copy(photos = a.photos.filterNot { it.key == photoKey })
            })
        }
    }

    fun renameAlbum(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update { s -> s.copy(albums = s.albums.map { if (it.id == id) it.copy(name = trimmed) else it }) }
    }

    fun deleteAlbum(id: String) {
        val name = _saved.value.albums.firstOrNull { it.id == id }?.name ?: return
        update { s -> s.copy(albums = s.albums.filterNot { it.id == id }) }
        showToast(L.s("album_deleted", "name" to name), ToastKind.Info)
    }

    /** Copies photos into Pictures/Flashback/<name>. */
    fun saveToGallery(name: String, photos: List<Photo>) = viewModelScope.launch {
        val ok = runCatching { library.saveAlbum(name, photos) }.getOrDefault(false)
        showToast(L.s(if (ok) "toast_saved_gallery" else "toast_save_failed"), if (ok) ToastKind.Success else ToastKind.Error)
    }

    fun setHidden(md: MonthDay, hide: Boolean) {
        update(refreshWidget = true) { s ->
            s.copy(hiddenDates = if (hide) s.hiddenDates + md.key else s.hiddenDates - md.key)
        }
        val msg = L.s(if (hide) "toast_day_hidden" else "toast_day_shown", "day" to md.shortLabel())
        if (hide) {
            showToast(msg, ToastKind.Info, undoLabel = L.s("undo"), undo = { setHidden(md, false) })
        } else {
            showToast(msg, ToastKind.Success)
        }
    }

    fun setLayout(layout: YearLayout) = update { it.copy(layout = layout) }

    private companion object {
        // Not "day" or "month": the reminder tap puts those in the intent as numbers, and
        // intent extras land in the same handle. 1.24 to 1.28 crashed on launch from it.
        const val KEY_DAY = "shownDay"
        const val KEY_SAVED_ON = "shownDaySavedOn"
    }
}
