import Foundation
import Observation
import WidgetKit

struct Sources: Codable, Equatable {
    var device = false
    var google = false
}

struct ReminderSetting: Codable, Equatable {
    var on = false
    var hour = 9
    var minute = 0
}

/// How Today lays out each year: all years in mosaics, or one year at a time.
enum YearLayout: String, Codable {
    case mosaic, strip
}

/// Everything the app keeps between launches.
struct SavedState: Codable {
    var onboarded = false
    var sources = Sources()
    var reminder = ReminderSetting()
    var favorites: [Photo] = []
    var albums: [Album] = []
    var googlePhotos: [Photo] = []
    /// Cached photo counts per day for the phone's library.
    var deviceIndex: DayIndex = [:]
    /// Days the user hid, as `MonthDay.key`. They don't come up on their own or in reminders.
    var hiddenDates: Set<String> = []
    var layout = YearLayout.mosaic

    init() {}

    // Reads each field on its own, so a file from an older version keeps what it has.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = SavedState()
        onboarded = (try? c.decodeIfPresent(Bool.self, forKey: .onboarded)) ?? d.onboarded
        sources = (try? c.decodeIfPresent(Sources.self, forKey: .sources)) ?? d.sources
        reminder = (try? c.decodeIfPresent(ReminderSetting.self, forKey: .reminder)) ?? d.reminder
        favorites = (try? c.decodeIfPresent([Photo].self, forKey: .favorites)) ?? d.favorites
        albums = (try? c.decodeIfPresent([Album].self, forKey: .albums)) ?? d.albums
        googlePhotos = (try? c.decodeIfPresent([Photo].self, forKey: .googlePhotos)) ?? d.googlePhotos
        deviceIndex = (try? c.decodeIfPresent(DayIndex.self, forKey: .deviceIndex)) ?? d.deviceIndex
        hiddenDates = (try? c.decodeIfPresent(Set<String>.self, forKey: .hiddenDates)) ?? d.hiddenDates
        layout = (try? c.decodeIfPresent(YearLayout.self, forKey: .layout)) ?? d.layout
    }

    /// Photo counts per day across the connected sources, leaving out hidden days.
    var visibleIndex: DayIndex {
        mergeIndexes(
            sources.device ? deviceIndex : [:],
            sources.google ? buildDayIndex(googlePhotos.map(\.time)) : [:]
        ).filter { !hiddenDates.contains($0.key) }
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

/// App state: what is saved on disk, plus what only lives while the app runs.
@MainActor
@Observable
final class AppStore {
    var saved: SavedState {
        didSet {
            syncDerivedState(from: oldValue)
            save()
        }
    }
    var day = MonthDay.today
    var indexing = false
    var viewer: Viewer?
    var tab: AppTab = .today
    /// Album page shown in the Albums tab, if any.
    var openAlbumID: String? {
        didSet { if openAlbumID != nil { tab = .albums } }
    }
    var toast: String?
    /// Counts album creations and additions, for the success haptic.
    var albumSaves = 0
    /// Debug screenshots only: opens the calendar on Today.
    var demoCalendar = false
    /// Google import progress: nil when idle, (0, 0) while waiting for picks.
    var googleImport: (done: Int, total: Int)?

    /// Photo counts per day across every connected source. Hidden days count as empty, so
    /// they don't show as nearby days, get calendar dots, or trigger reminders.
    private(set) var index: DayIndex = [:]
    /// Goes up by one each time `index` changes, so views can reload on it.
    private(set) var indexVersion: Int = 0
    /// Keys of favorite photos, kept here so each tile doesn't build the set again.
    private(set) var favoriteKeys: Set<String> = []

    /// How full each day is, 0 to 4, for the calendar. Worked out once per index change.
    var dayFullness: [String: Int] {
        if let c = fullnessCache, c.version == indexVersion { return c.levels }
        let levels = dayLevels(index)
        fullnessCache = (indexVersion, levels)
        return levels
    }
    @ObservationIgnored private var fullnessCache: (version: Int, levels: [String: Int])?

    private var toastTask: Task<Void, Never>?
    private var importTask: Task<Void, Never>?
    private var saveTask: Task<Void, Never>?
    private var refreshing = false
    nonisolated private static let file = URL.applicationSupportDirectory.appending(path: "state.json")

    init() {
        let data = try? Data(contentsOf: Self.file)
        let initial = data.flatMap { try? JSONDecoder().decode(SavedState.self, from: $0) } ?? SavedState()
        saved = initial
        index = initial.visibleIndex
        favoriteKeys = Set(initial.favorites.map(\.key))
        updateWidget()
    }

    private func syncDerivedState(from old: SavedState) {
        if old.favorites != saved.favorites {
            favoriteKeys = Set(saved.favorites.map(\.key))
        }
        if old.sources != saved.sources || old.hiddenDates != saved.hiddenDates {
            updateWidget()
        }
        if old.sources != saved.sources
            || old.hiddenDates != saved.hiddenDates
            || old.googlePhotos != saved.googlePhotos
            || old.deviceIndex != saved.deviceIndex
        {
            index = saved.visibleIndex
            indexVersion &+= 1
        }
    }

    /// Tells the home screen widget which days are hidden and whether it may show the phone's photos.
    private func updateWidget() {
        let info = WidgetInfo(device: saved.sources.device, hidden: saved.hiddenDates)
        Task.detached(priority: .utility) {
            info.write()
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    /// Each write waits for the one before it, so an older snapshot never lands after a newer one.
    private func save() {
        let snapshot = saved
        let file = Self.file
        let previous = saveTask
        saveTask = Task.detached(priority: .utility) {
            await previous?.value
            try? FileManager.default.createDirectory(at: URL.applicationSupportDirectory, withIntermediateDirectories: true)
            if let data = try? JSONEncoder().encode(snapshot) { try? data.write(to: file, options: .atomic) }
        }
    }

    /// Rebuilds the phone's day index. Skips the full scan when the library looks the same,
    /// unless `force` (pull to refresh). The spinner shows only when the user is waiting on it.
    func refreshIndex(force: Bool = false) async {
        guard PhotoLibrary.hasAccess, !refreshing else { return }
        refreshing = true
        if saved.deviceIndex.isEmpty || force { indexing = true }
        defer { refreshing = false; indexing = false }
        if let updated = await PhotoLibrary.deviceIndex(force: force || saved.deviceIndex.isEmpty) {
            if saved.deviceIndex != updated {
                saved.deviceIndex = updated
            }
            applyReminder()
        }
    }

    func photos(for md: MonthDay) async -> [YearGroup] {
        let s = saved
        let years = yearsForDay(index, md)
        guard !years.isEmpty else { return [] }
        let deviceByYear = s.sources.device ? await PhotoLibrary.photos(years: years, on: md) : [:]
        var googleByYear: [Int: [Photo]] = [:]
        if s.sources.google {
            for p in s.googlePhotos where MonthDay.of(p.time) == md {
                googleByYear[yearOf(p.time), default: []].append(p)
            }
        }
        var groups: [YearGroup] = []
        groups.reserveCapacity(years.count)
        for year in years {
            let device = deviceByYear[year] ?? []
            let google = googleByYear[year] ?? []
            let photos = dropDuplicates(device: device, google: google).sorted { $0.time < $1.time }
            if !photos.isEmpty { groups.append(YearGroup(year: year, photos: photos)) }
        }
        return groups
    }

    func say(_ message: String) {
        toastTask?.cancel()
        toast = message
        toastTask = Task {
            try? await Task.sleep(for: .seconds(2.2))
            if !Task.isCancelled { toast = nil }
        }
    }

    func connectDevice() async -> Bool {
        guard await PhotoLibrary.requestAccess() else { return false }
        saved.sources.device = true
        await refreshIndex()
        return true
    }

    func finishOnboarding(reminder: Bool) {
        saved.onboarded = true
        saved.reminder.on = reminder
        applyReminder()
        if reminder { say(L.s("toast_reminder_set", ["time": formatTime(hour: saved.reminder.hour, minute: saved.reminder.minute)])) }
    }

    func setReminder(on: Bool? = nil, hour: Int? = nil, minute: Int? = nil) {
        if let on { saved.reminder.on = on }
        if let hour { saved.reminder.hour = hour }
        if let minute { saved.reminder.minute = minute }
        applyReminder()
    }

    func applyReminder() {
        let r = saved.reminder
        guard saved.onboarded else { return }
        if r.on {
            let index = self.index
            Task { await Reminders.schedule(index: index, hour: r.hour, minute: r.minute) }
        } else {
            Reminders.cancel()
        }
    }

    func isFavorite(_ key: String) -> Bool { favoriteKeys.contains(key) }

    func toggleFavorite(_ photo: Photo) {
        if isFavorite(photo.key) {
            saved.favorites.removeAll { $0.key == photo.key }
            say(L.s("toast_unfavorited"))
        } else {
            saved.favorites.insert(photo, at: 0)
            say(L.s("toast_favorited"))
        }
    }

    @discardableResult
    func createAlbum(_ name: String, photos: [Photo]) -> Album {
        let album = Album(id: UUID().uuidString, name: name, photos: photos, createdAt: Date())
        saved.albums.insert(album, at: 0)
        albumSaves += 1
        say(L.s("toast_album_created"))
        return album
    }

    func add(_ photos: [Photo], to albumID: String) {
        guard let i = saved.albums.firstIndex(where: { $0.id == albumID }) else { return }
        let known = Set(saved.albums[i].photos.map(\.key))
        saved.albums[i].photos += photos.filter { !known.contains($0.key) }
        albumSaves += 1
        say(L.s("toast_added_to", ["name": saved.albums[i].name]))
    }

    func renameAlbum(id: String, name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, let i = saved.albums.firstIndex(where: { $0.id == id }) else { return }
        saved.albums[i].name = trimmed
    }

    func deleteAlbum(_ id: String) {
        saved.albums.removeAll { $0.id == id }
    }

    func isHidden(_ md: MonthDay) -> Bool { saved.hiddenDates.contains(md.key) }

    func setHidden(_ md: MonthDay, _ hidden: Bool) {
        if hidden { saved.hiddenDates.insert(md.key) } else { saved.hiddenDates.remove(md.key) }
        say(L.s(hidden ? "toast_day_hidden" : "toast_day_shown", ["day": md.shortLabel]))
        applyReminder()
    }

    /// Saves photos into a Photos album with this name.
    func saveToPhotos(name: String, photos: [Photo]) async {
        do {
            if !PhotoLibrary.hasAccess, !(await PhotoLibrary.requestAccess()) { return say(L.s("toast_allow_save")) }
            try await PhotoLibrary.saveAlbum(name: name, photos: photos)
            say(L.s("toast_saved_photos"))
        } catch {
            say(L.s("toast_save_failed"))
        }
    }

    func importFromGoogle() {
        importTask?.cancel()
        importTask = Task {
            googleImport = (0, 0)
            defer { googleImport = nil }
            do {
                guard let token = try await GoogleAuth.shared.accessToken() else { return }
                guard let photos = try await GooglePhotos.pick(token: token, onProgress: { [weak self] done, total in
                    self?.googleImport = (done, total)
                }) else { return say(L.s("toast_no_picks")) }
                let known = Set(saved.googlePhotos.map(\.key))
                saved.googlePhotos += photos.filter { !known.contains($0.key) }
                saved.sources.google = true
                say(photos.isEmpty ? L.s("toast_google_connected") : L.s("toast_google_added", ["photos": L.p("photos", photos.count)]))
                applyReminder()
            } catch is CancellationError {
            } catch {
                say(L.s("toast_google_failed"))
            }
        }
    }

    func cancelGoogleImport() {
        importTask?.cancel()
        googleImport = nil
    }

    func disconnectGoogle() {
        GoogleAuth.shared.signOut()
        GooglePhotos.deleteAll()
        saved.googlePhotos = []
        saved.sources.google = false
        saved.favorites.removeAll { $0.source == .google }
        for i in saved.albums.indices { saved.albums[i].photos.removeAll { $0.source == .google } }
        applyReminder()
        say(L.s("toast_google_disconnected"))
    }
}
