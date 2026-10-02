#if DEBUG
import Foundation

/// Debug-only screen setups for the README screenshots.
/// Launch with `-scene today|calendar|viewer|favorites|album`.
extension AppStore {
    static var launchScene: String? {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-scene"), i + 1 < args.count else { return nil }
        return args[i + 1]
    }

    func runDemo(_ scene: String) async {
        saved.onboarded = true
        saved.sources.device = true
        await refreshIndex()
        let photos = await photos(for: .today).flatMap(\.photos)
        let times = await PhotoLibrary.allTimes()
        let note = "access=\(PhotoLibrary.hasAccess) library=\(times.count) today=\(photos.count) first=\(times.prefix(5))\n"
        try? note.write(to: URL.documentsDirectory.appending(path: "demo.log"), atomically: true, encoding: .utf8)
        guard !photos.isEmpty else { return }
        saved.favorites = stride(from: 0, to: photos.count, by: 3).map { photos[$0] }
        let album = Album(id: "demo", name: "Fall weekends", photos: Array(photos.prefix(9)), createdAt: Date())
        saved.albums = [album, Album(id: "demo2", name: "Grandma's garden", photos: Array(photos.suffix(5)), createdAt: Date())]
        switch scene {
        case "calendar": demoCalendar = true
        case "viewer": viewer = Viewer(photos: photos, start: min(2, photos.count - 1))
        case "favorites": tab = .favorites
        case "album": openAlbumID = album.id
        default: break
        }
    }
}
#endif
