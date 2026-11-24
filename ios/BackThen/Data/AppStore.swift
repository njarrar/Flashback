import CoreLocation
import Foundation
import Observation
import OSLog
import Photos
import Security
import UIKit
import WidgetKit

struct Sources: Codable, Equatable, Sendable {
    var device = false
}

struct ReminderSetting: Codable, Equatable, Sendable {
    var on = false
    var hour = 9
    var minute = 0
}

/// Places you go back to: shows photos from a city when you're there again.
struct PlaceSetting: Codable, Equatable, Sendable {
    var on = false
    /// The app asked once, whatever the answer.
    var asked = false
}

/// How Today lays out each year: all years in mosaics, one year at a time, or a deck of year cards.
enum YearLayout: String, Codable, Sendable {
    case mosaic, strip, cards
}

/// Everything the app keeps between launches.
struct SavedState: Codable, Sendable {
    var onboarded = false
    var sources = Sources()
    var reminder = ReminderSetting()
    var favorites: [Photo] = []
    var albums: [Album] = []
    /// Cached photo counts per day for the phone's library.
    var deviceIndex: DayIndex = [:]
    /// The library as it was when `deviceIndex` was built.
    var librarySignature: PhotoLibrary.LibrarySignature?
    /// Where Photos' record of changes stood when `deviceIndex` was last brought up to date.
    var changeToken: Data?
    /// Days the user hid, as `MonthDay.key`. They don't come up on their own or in reminders.
    var hiddenDates: Set<String> = []
    var layout = YearLayout.mosaic
    var places = PlaceSetting()
    /// Photo days per city, from photo locations. Kept on the phone like everything else.
    var placeIndex: PlaceIndex = [:]
    /// The city whose card was closed, until the phone is somewhere else.
    var placeDismissed: Int?
    /// Optional user-selected home city override (city id in cities.tsv).
    var customHomeCity: Int? = nil
    /// Checksum of `cities.tsv` when `placeIndex` was built, so TSV updates trigger a rebuild.
    var citiesHash: String? = nil
    /// The time zone `deviceIndex` was counted in. A new zone means a full count.
    var indexZone: String? = nil

    init() {}

    // Reads each field on its own, so a file from an older version keeps what it has.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = SavedState()
        onboarded = (try? c.decodeIfPresent(Bool.self, forKey: .onboarded)) ?? d.onboarded
        sources = (try? c.decodeIfPresent(Sources.self, forKey: .sources)) ?? d.sources
        reminder = (try? c.decodeIfPresent(ReminderSetting.self, forKey: .reminder)) ?? d.reminder
        favorites = (try? c.decodeIfPresent(Lossy<Photo>.self, forKey: .favorites))?.items ?? d.favorites
        albums = (try? c.decodeIfPresent(Lossy<Album>.self, forKey: .albums))?.items ?? d.albums
        deviceIndex = (try? c.decodeIfPresent(DayIndex.self, forKey: .deviceIndex)) ?? d.deviceIndex
        librarySignature = try? c.decodeIfPresent(PhotoLibrary.LibrarySignature.self, forKey: .librarySignature)
        changeToken = try? c.decodeIfPresent(Data.self, forKey: .changeToken)
        hiddenDates = (try? c.decodeIfPresent(Set<String>.self, forKey: .hiddenDates)) ?? d.hiddenDates
        layout = (try? c.decodeIfPresent(YearLayout.self, forKey: .layout)) ?? d.layout
        places = (try? c.decodeIfPresent(PlaceSetting.self, forKey: .places)) ?? d.places
        placeIndex = (try? c.decodeIfPresent(PlaceIndex.self, forKey: .placeIndex)) ?? d.placeIndex
        placeDismissed = try? c.decodeIfPresent(Int.self, forKey: .placeDismissed)
        customHomeCity = try? c.decodeIfPresent(Int.self, forKey: .customHomeCity)
        citiesHash = try? c.decodeIfPresent(String.self, forKey: .citiesHash)
        indexZone = try? c.decodeIfPresent(String.self, forKey: .indexZone)
    }

    /// Photo counts per day in the phone's library, leaving out hidden days.
    var visibleIndex: DayIndex {
        guard sources.device else { return [:] }
        return deviceIndex.filter { !hiddenDates.contains($0.key) }
    }
}

enum AppTab: Hashable {
    case today, favorites, albums
}

struct Viewer: Identifiable {
    let id = UUID()
    let photos: [Photo]
    let start: Int
}

@MainActor
private final class BackgroundTaskBox: Sendable {
    private var id: UIBackgroundTaskIdentifier = .invalid

    func begin(name: String) {
        id = UIApplication.shared.beginBackgroundTask(withName: name) { [weak self] in
            Task { @MainActor in self?.end() }
        }
    }

    func end() {
        guard id != .invalid else { return }
        let current = id
        id = .invalid
        UIApplication.shared.endBackgroundTask(current)
    }
}

/// App state: what is saved on disk, plus what only lives while the app runs.
@MainActor
@Observable
final class AppStore {
    enum ToastKind: Equatable {
        case success, info, error
    }

    enum ReelSaveResult: Equatable {
        case saved, denied, failed
    }

    private static let logger = Logger(subsystem: "com.njarrar.backthen", category: "AppStore")

    var saved: SavedState {
        didSet {
            syncDerivedState(from: oldValue)
            scheduleSave()
        }
    }
    var day = MonthDay.today
    /// What today was when the app last checked, so it can tell when midnight has passed.
    @ObservationIgnored private var lastToday = MonthDay.today
    @ObservationIgnored private var lastReminderRefreshDay: MonthDay?
    var indexing = false
    var viewer: Viewer?
    /// The reel editor, when open.
    var reel: ReelRequest?
    var tab: AppTab = .today
    /// Album page shown in the Albums tab, if any.
    var openAlbumID: String? {
        didSet { if openAlbumID != nil { tab = .albums } }
    }
    /// Incremented whenever a widget, notification, or "Show this day" deep link fires so TodayView dismisses local sheets/subpages.
    var deepLinkToken: Int = 0
    var toast: String?
    var toastKind: ToastKind = .success
    var toastUndoLabel: String? = nil
    @ObservationIgnored private var toastUndoAction: (() -> Void)? = nil
    /// Live Photos authorization status so views react automatically to Limited / Full / Denied changes.
    var photoAuthStatus: PHAuthorizationStatus = PhotoLibrary.authorizationStatus
    var isLimitedPhotoAccess: Bool { photoAuthStatus == .limited }
    /// Live location authorization status so Settings can recover from "Allow Once" expiry or Settings revocation.
    var locationAuthStatus: CLAuthorizationStatus = Locator.shared.authorizationStatus
    var locationAccessLost: Bool {
        saved.places.on && !(locationAuthStatus == .authorizedWhenInUse || locationAuthStatus == .authorizedAlways)
    }
    /// True when the user previously connected Photos but later revoked access in iOS Settings.
    var photoAccessRevoked: Bool = false
    /// True while Places is scanning photo locations across the library.
    var placesScanning: Bool = false
    /// Counts album creations and additions, for the success haptic.
    var albumSaves = 0
    /// Debug screenshots only: opens the calendar on Today.
    var demoCalendar = false
    /// Debug screenshots only: a sheet or mode Today opens once its photos show.
    var demoScene: String?
    /// Debug screenshots only: sample photos shown in place of the phone's library.
    var samplePhotos: [Photo] = []
    /// Suppresses disk writes while debug demo mode is active.
    @ObservationIgnored var isDemoMode = false

    /// Photo counts per day across every connected source. Hidden days count as empty, so
    /// they don't show as nearby days, get calendar dots, or trigger reminders.
    private(set) var index: DayIndex = [:]
    /// Goes up by one each time `index` changes, so views can reload on it.
    private(set) var indexVersion: Int = 0
    /// Monotonic counter incremented whenever favorites change, catching swaps with equal counts.
    @ObservationIgnored private var favoritesVersion: Int = 0
    /// Keys of favorite photos, kept here so each tile doesn't build the set again.
    private(set) var favoriteKeys: Set<String> = []
    /// The card Today shows in a city you've been to before, away from home.
    private(set) var placeCard: PlaceCard?
    /// The city the phone is in, when places are on.
    @ObservationIgnored private var here: Int?
    @ObservationIgnored private var lastDismissedPlaceID: Int?

    /// Calendar thumbnails by `MonthDay.key`, kept until the index, favorites, or calendar day change.
    @ObservationIgnored private var covers: (version: String, photos: [String: Photo]) = ("", [:])

    private var toastTask: Task<Void, Never>?
    private var saveDebounceTask: Task<Void, Never>?
    private var saveTask: Task<Void, Never>?
    private var widgetTask: Task<Void, Never>?
    private var reminderTask: Task<Void, Never>?
    private var placesTask: Task<Void, Never>?
    private var libraryChangeTask: Task<Void, Never>?
    @ObservationIgnored private var libraryObserver: PhotoLibraryObserver?
    @ObservationIgnored private var dirtySave = false
    @ObservationIgnored private var lastWidgetInfo: WidgetInfo?
    private var refreshing = false
    private var pendingForceRefresh = false
    @ObservationIgnored private let storageURL: URL
    @ObservationIgnored private let persist: Bool
    nonisolated static let defaultFile = URL.applicationSupportDirectory.appending(path: "state.json")

    init(storageURL: URL = AppStore.defaultFile, persist: Bool = true) {
        self.storageURL = storageURL
        self.persist = persist
        let data = try? Data(contentsOf: storageURL)
        let initial: SavedState
        if let data {
            if let decoded = try? JSONDecoder().decode(SavedState.self, from: data) {
                initial = decoded
            } else {
                let stamp = Int(Date().timeIntervalSince1970)
                let backup = storageURL.deletingLastPathComponent().appending(path: "state.corrupt-\(stamp).json")
                try? FileManager.default.copyItem(at: storageURL, to: backup)
                initial = SavedState()
            }
        } else {
            initial = SavedState()
        }
        saved = initial
        photoAuthStatus = PhotoLibrary.authorizationStatus
        locationAuthStatus = Locator.shared.authorizationStatus
        photoAccessRevoked = persist && initial.sources.device && !PhotoLibrary.hasAccess
        index = photoAccessRevoked ? [:] : initial.visibleIndex
        favoriteKeys = Set(initial.favorites.map(\.key))
        if persist {
            updateWidget(forceReload: false)
            if initial.onboarded && initial.reminder.on && !photoAccessRevoked {
                applyReminder()
            }
            Self.removeGoogleData()
            Task.detached(priority: .utility) {
                Share.pruneStaleShares()
                ReelExport.pruneStaleExports()
            }
            Locator.shared.onAuthorizationChange = { [weak self] status in
                guard let self else { return }
                self.locationAuthStatus = status
                if self.saved.places.on && !(status == .authorizedWhenInUse || status == .authorizedAlways) {
                    self.placeCard = nil
                }
            }
            startLibraryObserver()
        }
    }

    /// Registering a Photos change observer before the user has answered the access prompt
    /// makes iOS show that prompt, so wait until access is granted.
    private func startLibraryObserver() {
        guard persist, libraryObserver == nil, PhotoLibrary.hasAccess else { return }
        libraryObserver = PhotoLibraryObserver { [weak self] in
            Task { @MainActor [weak self] in
                self?.scheduleLibraryChangeRefresh()
            }
        }
    }

    private func scheduleLibraryChangeRefresh() {
        guard persist, !isDemoMode, samplePhotos.isEmpty, saved.onboarded, saved.sources.device else { return }
        guard UIApplication.shared.applicationState == .active else { return }
        libraryChangeTask?.cancel()
        libraryChangeTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(500))
            guard !Task.isCancelled, let self else { return }
            await self.refreshIndex(force: PhotoLibrary.isLimited)
        }
    }

    /// Presents Apple's Limited Photos picker and refreshes the index as soon as the user taps Done.
    func presentLimitedPicker() {
        PhotoLibrary.presentLimitedPicker { [weak self] in
            guard let self else { return }
            Task { await self.refreshIndex(force: true) }
        }
    }

    /// Unified deep-link entry for widget taps, reminder notifications, and "Show this day".
    /// Updates `lastToday` first so a subsequent `catchUpToToday()` on scene activation cannot overwrite `md`.
    func openDeepLink(day md: MonthDay) {
        guard saved.onboarded else { return }
        lastToday = MonthDay.today
        viewer = nil
        reel = nil
        openAlbumID = nil
        day = md
        tab = .today
        deepLinkToken &+= 1
    }

    /// Version 1.8 and earlier could import from Google Photos. Clears what that left behind once.
    nonisolated private static func removeGoogleData() {
        let key = "removedLegacyGoogleData"
        let legacyDir = URL.applicationSupportDirectory.appending(path: "google-photos")
        let hasDir = FileManager.default.fileExists(atPath: legacyDir.path)
        guard !UserDefaults.standard.bool(forKey: key) || hasDir else { return }
        Task.detached(priority: .utility) {
            if FileManager.default.fileExists(atPath: legacyDir.path) {
                try? FileManager.default.removeItem(at: legacyDir)
            }
            let token: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: "BackThen",
                kSecAttrAccount as String: "google-refresh-token",
            ]
            SecItemDelete(token as CFDictionary)
            UserDefaults.standard.set(true, forKey: key)
        }
    }

    private func syncDerivedState(from old: SavedState) {
        if old.favorites != saved.favorites {
            favoriteKeys = Set(saved.favorites.map(\.key))
            favoritesVersion &+= 1
        }
        if old.sources != saved.sources || old.hiddenDates != saved.hiddenDates {
            updateWidget(forceReload: false)
        }
        if old.sources != saved.sources
            || old.hiddenDates != saved.hiddenDates
            || old.deviceIndex != saved.deviceIndex
        {
            index = photoAccessRevoked ? [:] : saved.visibleIndex
            indexVersion &+= 1
        }
    }

    /// Tells the home screen widget which days are hidden and whether it may show the phone's photos.
    /// Serialized so an older write can never overwrite a newer one, and reloads timelines only on change.
    private func updateWidget(forceReload: Bool = false) {
        guard persist, !isDemoMode else { return }
        let info = WidgetInfo(device: saved.sources.device && !photoAccessRevoked, hidden: saved.hiddenDates)
        if !forceReload, info == lastWidgetInfo { return }
        let previous = widgetTask
        lastWidgetInfo = info
        widgetTask = Task.detached(priority: .utility) {
            await previous?.value
            let existing = WidgetInfo.read()
            if forceReload || existing != info {
                info.write()
                WidgetCenter.shared.reloadAllTimelines()
            }
        }
    }

    /// Coalesces rapid `saved` mutations into a single disk write. Call `flushSave()` before suspension.
    private func scheduleSave() {
        guard persist, !isDemoMode else { return }
        dirtySave = true
        saveDebounceTask?.cancel()
        saveDebounceTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(180))
            guard !Task.isCancelled, let self else { return }
            self.writeSaveNow()
        }
    }

    /// Immediately persists any pending coalesced changes (invoked when leaving foreground).
    func flushSave() {
        guard persist, !isDemoMode, dirtySave else { return }
        saveDebounceTask?.cancel()
        saveDebounceTask = nil
        writeSaveNow()
    }

    /// Each write waits for the one before it, so an older snapshot never lands after a newer one.
    private func writeSaveNow() {
        guard persist, !isDemoMode else { return }
        dirtySave = false
        let snapshot = saved
        let file = storageURL
        let previous = saveTask
        let bg = BackgroundTaskBox()
        bg.begin(name: "SaveAppState")
        saveTask = Task.detached(priority: .utility) {
            await previous?.value
            do {
                try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
                let data = try JSONEncoder().encode(snapshot)
                try data.write(to: file, options: .atomic)
                var excluded = file
                var values = URLResourceValues()
                values.isExcludedFromBackup = true
                try? excluded.setResourceValues(values)
            } catch {
                AppStore.logger.error("Failed to save state.json: \(error.localizedDescription, privacy: .public)")
            }
            await bg.end()
        }
    }

    /// Reconciles saved source state if Photos or Location access was revoked or re-granted in iOS Settings.
    func reconcilePhotoAccess() {
        guard persist, !isDemoMode, samplePhotos.isEmpty else { return }
        let currentPhotoAuth = PhotoLibrary.authorizationStatus
        if currentPhotoAuth != photoAuthStatus {
            photoAuthStatus = currentPhotoAuth
        }
        let currentLocAuth = Locator.shared.authorizationStatus
        if currentLocAuth != locationAuthStatus {
            locationAuthStatus = currentLocAuth
            if saved.places.on && !Locator.shared.allowed {
                placeCard = nil
            }
        }
        let revoked = saved.sources.device && !PhotoLibrary.hasAccess
        guard revoked != photoAccessRevoked else { return }
        photoAccessRevoked = revoked
        if revoked {
            index = [:]
            indexVersion &+= 1
            placeCard = nil
            Reminders.cancel()
            updateWidget(forceReload: true)
        } else {
            index = saved.visibleIndex
            indexVersion &+= 1
            updateWidget(forceReload: true)
            applyReminder()
        }
    }

    /// After midnight, Today moves on to the new day, unless the user had picked another day.
    func catchUpToToday() {
        reconcilePhotoAccess()
        let now = MonthDay.today
        if now != lastToday {
            if day == lastToday { day = now }
            lastToday = now
            indexVersion &+= 1
            if !photoAccessRevoked { applyReminder() }
        } else if lastReminderRefreshDay != now && saved.onboarded && saved.reminder.on && !photoAccessRevoked {
            applyReminder()
        }
    }

    /// Rebuilds the phone's day index. Skips the full scan when the library looks the same,
    /// unless `force` (pull to refresh). The spinner shows only when the user is waiting on it.
    func refreshIndex(force: Bool = false) async {
        reconcilePhotoAccess()
        guard PhotoLibrary.hasAccess else { return }
        startLibraryObserver()
        if refreshing {
            if force { pendingForceRefresh = true }
            return
        }
        refreshing = true
        defer {
            refreshing = false
            indexing = false
        }
        var shouldForce = force
        while true {
            await runRefreshPass(force: shouldForce)
            if pendingForceRefresh {
                pendingForceRefresh = false
                shouldForce = true
                continue
            }
            break
        }
    }

    private func runRefreshPass(force: Bool) async {
        let limited = PhotoLibrary.isLimited
        if saved.deviceIndex.isEmpty || force { indexing = true }
        // Usually only a few photos were added since last time: count just those.
        // Skip the persistent change-token fast path in Limited mode: scope edits in Settings
        // do not emit PHAsset change rows, so limited libraries always rescan directly.
        if !force, !limited, !saved.deviceIndex.isEmpty, saved.indexZone == TimeZone.autoupdatingCurrent.identifier,
           let token = saved.changeToken,
           let added = await PhotoLibrary.newPhotos(since: token) {
            // Load CityList BEFORE snapshotting `saved` so concurrent @MainActor edits are never clobbered.
            let cities: CityList? = saved.places.on ? await CityList.load() : nil
            var next = saved
            next.changeToken = added.token
            let citiesStale = cities != nil && next.citiesHash != cities?.citiesHash
            if !added.times.isEmpty {
                next.deviceIndex = addingToDayIndex(next.deviceIndex, added.times)
                // Counted without a scan, so the next full check scans.
                next.librarySignature = nil
                if next.places.on, !next.placeIndex.isEmpty, !citiesStale, let cities {
                    next.placeIndex = addingToPlaceIndex(next.placeIndex, added.spots, cities: cities)
                }
            }
            saved = next
            if !added.times.isEmpty {
                applyReminder()
                updateWidget(forceReload: true)
            }
            if saved.places.on {
                startPlacesTask(rebuild: saved.placeIndex.isEmpty || citiesStale)
            }
            return
        }
        // In Limited mode pass known: nil so same-count middle swaps are never missed.
        let known = (force || limited || saved.deviceIndex.isEmpty) ? nil : saved.librarySignature
        let fresh = await PhotoLibrary.deviceIndex(known: known)
        // Taken after the scan: a photo added during it is missed until the next full scan,
        // never counted twice.
        let token = await PhotoLibrary.changeToken()
        // Prune deleted device photos from Favorites and Albums when full library access is active.
        var existingIDs: Set<String>? = nil
        if fresh != nil && PhotoLibrary.authorizationStatus == .authorized {
            let referenced = Set(
                saved.favorites.filter { $0.source == .device }.map(\.ref)
                + saved.albums.flatMap { $0.photos.filter { $0.source == .device }.map(\.ref) }
            )
            if !referenced.isEmpty {
                existingIDs = await PhotoLibrary.existingIdentifiers(from: Array(referenced))
            }
        }
        var next = saved
        var changed = false
        if token != next.changeToken {
            next.changeToken = token
            changed = true
        }
        let zone = TimeZone.autoupdatingCurrent.identifier
        if next.indexZone != zone {
            next.indexZone = zone
            changed = true
        }
        if let fresh, next.deviceIndex != fresh.index || next.librarySignature != fresh.signature {
            next.deviceIndex = fresh.index
            next.librarySignature = fresh.signature
            changed = true
        }
        if let existingIDs {
            let prunedFavs = next.favorites.filter { $0.source != .device || existingIDs.contains($0.ref) }
            if prunedFavs != next.favorites {
                next.favorites = prunedFavs
                changed = true
            }
            let prunedAlbums = next.albums.map { album in
                Album(
                    id: album.id,
                    name: album.name,
                    photos: album.photos.filter { $0.source != .device || existingIDs.contains($0.ref) },
                    createdAt: album.createdAt
                )
            }
            if prunedAlbums != next.albums {
                next.albums = prunedAlbums
                changed = true
            }
        }
        if changed { saved = next }
        if fresh != nil {
            applyReminder()
            updateWidget(forceReload: true)
        }
        if saved.places.on {
            let loadedHash = (await CityList.load())?.citiesHash
            let hashMismatch = loadedHash != nil && saved.citiesHash != loadedHash
            let rebuild = fresh != nil || saved.placeIndex.isEmpty || hashMismatch
            startPlacesTask(rebuild: rebuild)
        }
    }

    // MARK: Places

    private func startPlacesTask(rebuild: Bool) {
        placesTask?.cancel()
        placesTask = Task { [weak self] in
            guard let self else { return }
            if rebuild {
                await self.refreshPlaces()
            } else {
                await self.checkPlace()
            }
        }
    }

    /// Turns places on (asking for the phone's rough location) or off.
    func setPlaces(_ on: Bool) async {
        placesTask?.cancel()
        placesTask = nil
        if !on {
            var next = saved
            next.places.asked = true
            next.places.on = false
            saved = next
            placesScanning = false
            placeCard = nil
            return
        }
        saved.places.asked = true
        let granted = await Locator.shared.requestAccess()
        locationAuthStatus = Locator.shared.authorizationStatus
        guard granted else {
            saved.places.on = false
            return showToast(L.s("toast_location_off"), kind: .info)
        }
        saved.places.on = true
        await refreshPlaces()
    }

    /// Sets or clears the user's custom home city override.
    func setHomeCity(_ id: Int?) {
        saved.customHomeCity = id
        if saved.places.on {
            startPlacesTask(rebuild: false)
        }
    }

    /// Finds the city of every photo with a location, then checks where the phone is.
    func refreshPlaces() async {
        guard saved.places.on, PhotoLibrary.hasAccess, !Task.isCancelled else { return }
        placesScanning = true
        defer { placesScanning = false }
        guard let cities = await CityList.load(), saved.places.on, !Task.isCancelled else { return }
        let built = await PlaceLibrary.index(cities)
        guard saved.places.on, !Task.isCancelled else { return }
        if built != saved.placeIndex || saved.citiesHash != cities.citiesHash {
            var next = saved
            next.placeIndex = built
            if saved.citiesHash != nil && saved.citiesHash != cities.citiesHash {
                next.placeDismissed = nil
                next.customHomeCity = nil
            }
            next.citiesHash = cities.citiesHash
            saved = next
        }
        await checkPlace()
    }

    /// Looks up the city the phone is in, and whether Today shows a card for it.
    func checkPlace() async {
        locationAuthStatus = Locator.shared.authorizationStatus
        guard saved.places.on, !Task.isCancelled else {
            placeCard = nil
            return
        }
        guard Locator.shared.allowed else {
            placeCard = nil
            return
        }
        guard let cities = await CityList.load(), saved.places.on, !Task.isCancelled else {
            if !saved.places.on { placeCard = nil }
            return
        }
        if saved.citiesHash != cities.citiesHash && !saved.placeIndex.isEmpty {
            await refreshPlaces()
            return
        }
        guard let spot = await Locator.shared.current(), saved.places.on, !Task.isCancelled else {
            placeCard = nil
            return
        }
        here = cities.city(lat: spot.lat, lon: spot.lon)
        if let closed = saved.placeDismissed, closed != here { saved.placeDismissed = nil }
        placeCard = here == saved.placeDismissed
            ? nil
            : makePlaceCard(saved.placeIndex, here: here, cities: cities, customHome: saved.customHomeCity)
    }

    func dismissPlaceCard() {
        let dismissedCity = placeCard?.city.name
        let dismissedID = here ?? placeCard?.city.id
        lastDismissedPlaceID = dismissedID
        saved.placeDismissed = dismissedID
        placeCard = nil
        let msg = L.s("toast_day_hidden", ["day": dismissedCity ?? L.s("places_title")])
        showToast(msg, kind: .info, undoLabel: L.s("undo")) { [weak self] in
            self?.undoDismissPlaceCard()
        }
    }

    func undoDismissPlaceCard() {
        guard let restored = lastDismissedPlaceID else { return }
        lastDismissedPlaceID = nil
        if saved.placeDismissed == restored {
            saved.placeDismissed = nil
        }
        if saved.places.on {
            let targetID = here ?? restored
            Task { [weak self] in
                guard let self, let cities = await CityList.load(), self.saved.places.on else { return }
                self.placeCard = makePlaceCard(self.saved.placeIndex, here: targetID, cities: cities, customHome: self.saved.customHomeCity)
            }
        }
    }

    func photos(for md: MonthDay) async -> [YearGroup] {
        let years = yearsForDay(index, md)
        guard !years.isEmpty else { return [] }
        let byYear: [Int: [Photo]] = if !samplePhotos.isEmpty {
            Dictionary(grouping: samplePhotos.filter { MonthDay.of($0.time) == md }) { yearOf($0.time) }
        } else if saved.sources.device && !photoAccessRevoked {
            await PhotoLibrary.photos(years: years, on: md)
        } else {
            [:]
        }
        var groups: [YearGroup] = []
        groups.reserveCapacity(years.count)
        for year in years {
            let photos = (byYear[year] ?? []).sorted { $0.time < $1.time }
            if !photos.isEmpty { groups.append(YearGroup(year: year, photos: photos)) }
        }
        return groups
    }

    /// The photo the calendar shows for a day: a favorite from that day if there is one,
    /// else the first photo of the newest year. Nil for a day with no photos.
    func cover(for md: MonthDay) async -> Photo? {
        let version = "\(indexVersion)-\(favoritesVersion)-\(lastToday.key)"
        if covers.version != version { covers = (version, [:]) }
        if let hit = covers.photos[md.key] { return hit }
        let years = yearsForDay(index, md)
        guard let newest = years.first else { return nil }
        var found = favoriteCover(saved.favorites, md, years: years)
        if let fav = found, fav.source == .device, samplePhotos.isEmpty {
            let existing = await PhotoLibrary.existingIdentifiers(from: [fav.ref])
            if !existing.contains(fav.ref) { found = nil }
        }
        if found == nil {
            if !samplePhotos.isEmpty {
                found = samplePhotos.filter { MonthDay.of($0.time) == md && yearOf($0.time) == newest }.min { $0.time < $1.time }
            } else if saved.sources.device && !photoAccessRevoked {
                found = await PhotoLibrary.photos(years: [newest], on: md)[newest]?.first
            }
        }
        if let found, covers.version == version { covers.photos[md.key] = found }
        return found
    }

    func showToast(_ message: String, kind: ToastKind = .success, undoLabel: String? = nil, undo: (() -> Void)? = nil) {
        toastTask?.cancel()
        toast = message
        toastKind = kind
        toastUndoLabel = undo != nil ? undoLabel : nil
        toastUndoAction = undo
        let duration: Duration = undo != nil ? .seconds(4.0) : .seconds(2.2)
        toastTask = Task {
            try? await Task.sleep(for: duration)
            if !Task.isCancelled {
                toast = nil
                toastUndoLabel = nil
                toastUndoAction = nil
            }
        }
    }

    func triggerToastUndo() {
        let action = toastUndoAction
        toastTask?.cancel()
        toast = nil
        toastUndoLabel = nil
        toastUndoAction = nil
        action?()
    }

    func say(_ message: String) {
        showToast(message)
    }

    func connectDevice() async -> Bool {
        guard await PhotoLibrary.requestAccess() else {
            photoAuthStatus = PhotoLibrary.authorizationStatus
            return false
        }
        photoAuthStatus = PhotoLibrary.authorizationStatus
        photoAccessRevoked = false
        saved.sources.device = true
        await refreshIndex()
        return true
    }

    func finishOnboarding(reminder: Bool) {
        var next = saved
        next.onboarded = true
        next.reminder.on = reminder
        saved = next
        applyReminder()
        if reminder { showToast(L.s("toast_reminder_set", ["time": formatTime(hour: saved.reminder.hour, minute: saved.reminder.minute)]), kind: .success) }
    }

    func setReminder(on: Bool? = nil, hour: Int? = nil, minute: Int? = nil) {
        var next = saved
        if let on { next.reminder.on = on }
        if let hour { next.reminder.hour = hour }
        if let minute { next.reminder.minute = minute }
        saved = next
        applyReminder()
    }

    func applyReminder() {
        guard persist, !isDemoMode, saved.onboarded else { return }
        let r = saved.reminder
        lastReminderRefreshDay = MonthDay.today
        // One at a time, each after the last, so a quick run of changes ends on the latest one.
        let previous = reminderTask
        let index = self.index
        reminderTask = Task {
            await previous?.value
            if r.on {
                await Reminders.schedule(index: index, hour: r.hour, minute: r.minute)
            } else {
                Reminders.cancel()
            }
        }
    }

    func isFavorite(_ key: String) -> Bool { favoriteKeys.contains(key) }

    func toggleFavorite(_ photo: Photo) {
        if isFavorite(photo.key) {
            saved.favorites.removeAll { $0.key == photo.key }
            showToast(L.s("toast_unfavorited"), kind: .info)
        } else {
            saved.favorites.insert(photo, at: 0)
            showToast(L.s("toast_favorited"), kind: .success)
        }
    }

    @discardableResult
    func createAlbum(_ name: String, photos: [Photo]) -> Album {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalName = trimmed.isEmpty ? L.s("album") : trimmed
        var seen = Set<String>()
        let unique = photos.filter { seen.insert($0.key).inserted }
        let album = Album(id: UUID().uuidString, name: finalName, photos: unique, createdAt: Date())
        saved.albums.insert(album, at: 0)
        albumSaves += 1
        showToast(L.s("toast_album_created"), kind: .success)
        return album
    }

    @discardableResult
    func add(_ photos: [Photo], to albumID: String) -> Int {
        guard let i = saved.albums.firstIndex(where: { $0.id == albumID }) else { return 0 }
        var known = Set(saved.albums[i].photos.map(\.key))
        let added = photos.filter { known.insert($0.key).inserted }
        let name = saved.albums[i].name
        guard !added.isEmpty else {
            showToast(L.s("already_in_album", ["name": name]), kind: .info)
            return 0
        }
        saved.albums[i].photos += added
        albumSaves += 1
        showToast(L.s("toast_added_to", ["name": name]), kind: .success)
        return added.count
    }

    func removeFromAlbum(_ albumID: String, photoKey: String) {
        guard let i = saved.albums.firstIndex(where: { $0.id == albumID }) else { return }
        saved.albums[i].photos.removeAll { $0.key == photoKey }
    }

    func renameAlbum(id: String, name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let i = saved.albums.firstIndex(where: { $0.id == id }) else { return }
        saved.albums[i].name = trimmed
    }

    func deleteAlbum(_ id: String) {
        let deleted = saved.albums.first { $0.id == id }
        saved.albums.removeAll { $0.id == id }
        if openAlbumID == id { openAlbumID = nil }
        if let deleted {
            showToast(L.s("album_deleted", ["name": deleted.name]), kind: .info)
        }
    }

    func isHidden(_ md: MonthDay) -> Bool { saved.hiddenDates.contains(md.key) }

    func setHidden(_ md: MonthDay, _ hide: Bool) {
        if hide {
            saved.hiddenDates.insert(md.key)
            showToast(
                L.s("toast_day_hidden", ["day": md.shortLabel]),
                kind: .info,
                undoLabel: L.s("undo"),
                undo: { [weak self] in self?.setHidden(md, false) }
            )
        } else {
            saved.hiddenDates.remove(md.key)
            showToast(L.s("toast_day_shown", ["day": md.shortLabel]), kind: .success)
        }
        applyReminder()
    }

    /// Saves photos into a Photos album with this name.
    func saveToPhotos(name: String, photos: [Photo]) async {
        do {
            if !PhotoLibrary.hasAccess, !(await PhotoLibrary.requestAccess()) {
                return showToast(L.s("toast_allow_save"), kind: .info)
            }
            try await PhotoLibrary.saveAlbum(name: name, photos: photos)
            showToast(L.s("toast_saved_photos"), kind: .success)
        } catch {
            showToast(L.s("toast_save_failed"), kind: .error)
        }
    }

    /// Saves an exported reel MP4 into Apple Photos. Returns outcome so `ReelView` renders feedback on its own overlay.
    func saveReelToPhotos(_ url: URL) async -> ReelSaveResult {
        if !PhotoLibrary.hasAccess, !(await PhotoLibrary.requestAccess()) {
            photoAuthStatus = PhotoLibrary.authorizationStatus
            return .denied
        }
        photoAuthStatus = PhotoLibrary.authorizationStatus
        return await PhotoLibrary.saveVideo(url) ? .saved : .failed
    }
}
