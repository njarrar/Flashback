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
    var saved: SavedState { didSet { save() } }
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

    private var toastTask: Task<Void, Never>?
    private var importTask: Task<Void, Never>?
    nonisolated private static let file = URL.applicationSupportDirectory.appending(path: "state.json")

    init() {
        let data = try? Data(contentsOf: Self.file)
        saved = data.flatMap { try? JSONDecoder().decode(SavedState.self, from: $0) } ?? SavedState()
    }

    private func save() {
        let snapshot = saved
        let file = Self.file
        Task.detached(priority: .utility) {
            try? FileManager.default.createDirectory(at: URL.applicationSupportDirectory, withIntermediateDirectories: true)
            if let data = try? JSONEncoder().encode(snapshot) { try? data.write(to: file, options: .atomic) }
        }
    }

    /// Photo counts per day across every connected source.
    var index: DayIndex {
        mergeIndexes(
            saved.sources.device ? saved.deviceIndex : [:],
            saved.sources.google ? buildDayIndex(saved.googlePhotos.map(\.time)) : [:]
        )
    }

    var favoriteKeys: Set<String> { Set(saved.favorites.map(\.key)) }

    func refreshIndex() async {
        guard PhotoLibrary.hasAccess else { return }
        indexing = true
        let times = await PhotoLibrary.allTimes()
        saved.deviceIndex = buildDayIndex(times)
        indexing = false
        applyReminder()
    }

    func photos(for md: MonthDay) async -> [YearGroup] {
        let s = saved
        var groups: [YearGroup] = []
        for year in yearsForDay(index, md) {
            let device = s.sources.device ? await PhotoLibrary.photos(year: year, on: md) : []
            let google = s.sources.google ? s.googlePhotos.filter {
                yearOf($0.time) == year && MonthDay.of($0.time) == md
            } : []
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

    func isFavorite(_ key: String) -> Bool { saved.favorites.contains { $0.key == key } }

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

    func saveToPhotos(_ album: Album) async {
        do {
            if !PhotoLibrary.hasAccess, !(await PhotoLibrary.requestAccess()) { return say(L.s("toast_allow_save")) }
            try await PhotoLibrary.saveAlbum(name: album.name, photos: album.photos)
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
        say(L.s("toast_google_disconnected"))
    }
}
