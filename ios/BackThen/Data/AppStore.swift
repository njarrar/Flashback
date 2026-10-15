import Foundation
import Observation

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
            updateDerived(from: oldValue)
            save()
        }
    }
    /// Photo counts per day across every connected source. Hidden days count as empty, so
    /// they don't show as nearby days, get calendar dots, or trigger reminders.
    /// Worked out once per change, not on every screen draw.
    private(set) var index: DayIndex = [:]
    /// Goes up each time `index` changes, so Today knows to load the day again.
    private(set) var indexVersion = 0
    private(set) var favoriteKeys: Set<String> = []
    private var googleIndex: DayIndex = [:]
    var day = MonthDay.today
    var indexing = false
    var viewer: Viewer?
    var tab: AppTab = .today
    /// Album page shown in the Albums tab, if any.
    var openAlbumID: String? {
        didSet { if openAlbumID != nil { tab = .albums } }
    }
    var toast: String?
    /// Debug screenshots only: opens the calendar on Today.
    var demoCalendar = false
    /// Google import progress: nil when idle, (0, 0) while waiting for picks.
    var googleImport: (done: Int, total: Int)?

    private var toastTask: Task<Void, Never>?
    private var importTask: Task<Void, Never>?
    private var saveTask: Task<Void, Never>?
    private var refreshing = false
    nonisolated private static let file = URL.applicationSupportDirectory.appending(path: "state.json")

    init() {
        let data = try? Data(contentsOf: Self.file)
        saved = data.flatMap { try? JSONDecoder().decode(SavedState.self, from: $0) } ?? SavedState()
        favoriteKeys = Set(saved.favorites.map(\.key))
        googleIndex = buildDayIndex(saved.googlePhotos.map(\.time))
        rebuildIndex()
    }

    private func updateDerived(from old: SavedState) {
        if saved.favorites != old.favorites { favoriteKeys = Set(saved.favorites.map(\.key)) }
        let googleChanged = saved.googlePhotos != old.googlePhotos
        if googleChanged { googleIndex = buildDayIndex(saved.googlePhotos.map(\.time)) }
        if googleChanged || saved.sources != old.sources || saved.deviceIndex != old.deviceIndex
            || saved.hiddenDates != old.hiddenDates {
            rebuildIndex()
        }
    }

    private func rebuildIndex() {
        index = mergeIndexes(
            saved.sources.device ? saved.deviceIndex : [:],
            saved.sources.google ? googleIndex : [:]
        ).filter { !saved.hiddenDates.contains($0.key) }
        indexVersion &+= 1
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

    /// Rebuilds the phone's day index. The spinner shows only on first run or when the user
    /// pulls to refresh; otherwise the index updates quietly. `force` reads the library even
    /// if it looks unchanged.
    func refreshIndex(force: Bool = false) async {
        guard PhotoLibrary.hasAccess, !refreshing else { return }
        refreshing = true
        let busy = force || saved.deviceIndex.isEmpty
        if busy { indexing = true }
        defer {
            refreshing = false
            if busy { indexing = false }
        }
        if let fresh = await PhotoLibrary.deviceIndex(force: force), fresh != saved.deviceIndex {
            saved.deviceIndex = fresh
            applyReminder()
        }
    }

    /// Photos for one day, grouped by year. One library query covers every year.
    func photos(for md: MonthDay) async -> [YearGroup] {
        let s = saved
        let years = yearsForDay(index, md)
        guard !years.isEmpty else { return [] }
        let device = s.sources.device ? await PhotoLibrary.photos(years: years, on: md) : [:]
        var google: [Int: [Photo]] = [:]
        if s.sources.google {
            for p in s.googlePhotos where MonthDay.of(p.time) == md { google[yearOf(p.time), default: []].append(p) }
        }
        var groups: [YearGroup] = []
        for year in years {
            let photos = dropDuplicates(device: device[year] ?? [], google: google[year] ?? []).sorted { $0.time < $1.time }
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
        if reminder { say("Daily reminder set for \(formatTime(hour: saved.reminder.hour, minute: saved.reminder.minute))") }
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
            say("Removed from Favorites")
        } else {
            saved.favorites.insert(photo, at: 0)
            say("Added to Favorites")
        }
    }

    @discardableResult
    func createAlbum(_ name: String, photos: [Photo]) -> Album {
        let album = Album(id: UUID().uuidString, name: name, photos: photos, createdAt: Date())
        saved.albums.insert(album, at: 0)
        say("Album created")
        return album
    }

    func add(_ photos: [Photo], to albumID: String) {
        guard let i = saved.albums.firstIndex(where: { $0.id == albumID }) else { return }
        let known = Set(saved.albums[i].photos.map(\.key))
        saved.albums[i].photos += photos.filter { !known.contains($0.key) }
        say("Added to \(saved.albums[i].name)")
    }

    func deleteAlbum(_ id: String) {
        saved.albums.removeAll { $0.id == id }
    }

    func isHidden(_ md: MonthDay) -> Bool { saved.hiddenDates.contains(md.key) }

    func setHidden(_ md: MonthDay, _ hidden: Bool) {
        if hidden { saved.hiddenDates.insert(md.key) } else { saved.hiddenDates.remove(md.key) }
        say(hidden ? "\(md.shortLabel) hidden" : "\(md.shortLabel) shown again")
        applyReminder()
    }

    /// Saves photos into a Photos album with this name.
    func saveToPhotos(name: String, photos: [Photo]) async {
        do {
            if !PhotoLibrary.hasAccess, !(await PhotoLibrary.requestAccess()) { return say("Allow photo access to save") }
            try await PhotoLibrary.saveAlbum(name: name, photos: photos)
            say("Saved to Photos")
        } catch {
            say("Could not save the album")
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
                }) else { return say("No photos picked") }
                let known = Set(saved.googlePhotos.map(\.key))
                saved.googlePhotos += photos.filter { !known.contains($0.key) }
                saved.sources.google = true
                say(photos.isEmpty ? "Google Photos connected" : "Added \(photos.count) from Google Photos")
                applyReminder()
            } catch is CancellationError {
            } catch {
                say("Could not reach Google Photos")
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
        say("Google Photos disconnected")
    }
}
