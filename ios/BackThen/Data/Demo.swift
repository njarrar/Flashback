#if DEBUG
import Foundation
import ImageIO

/// Debug-only screen setups for the README screenshots.
/// Launch with `-scene today|calendar|viewer|favorites|album`.
///
/// The simulator can't be granted photo access without a tap, so the screenshot
/// workflow drops sample JPEGs where the app keeps Google Photos picks, and the
/// demo loads them as if they had been picked.
extension AppStore {
    static var launchScene: String? {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-scene"), i + 1 < args.count else { return nil }
        return args[i + 1]
    }

    func runDemo(_ scene: String) async {
        saved.onboarded = true
        saved.googlePhotos = Self.samplePhotos()
        saved.sources.google = !saved.googlePhotos.isEmpty
        let photos = await photos(for: .today).flatMap(\.photos)
        guard !photos.isEmpty else { return }
        saved.favorites = stride(from: 0, to: photos.count, by: 3).map { photos[$0] }
        let album = Album(id: "demo", name: L.s("demo_album_fall"), photos: Array(photos.prefix(9)), createdAt: Date())
        saved.albums = [album, Album(id: "demo2", name: L.s("demo_album_garden"), photos: Array(photos.suffix(5)), createdAt: Date())]
        switch scene {
        case "calendar": demoCalendar = true
        case "viewer": viewer = Viewer(photos: photos, start: min(2, photos.count - 1))
        case "favorites": tab = .favorites
        case "album": openAlbumID = album.id
        default: break
        }
    }

    private static func samplePhotos() -> [Photo] {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: GooglePhotos.folder.path)) ?? []
        let exifDate = DateFormatter()
        exifDate.locale = Locale(identifier: "en_US_POSIX")
        exifDate.dateFormat = "yyyy:MM:dd HH:mm:ss"
        return names.filter { $0.hasSuffix(".jpg") }.sorted().compactMap { name in
            guard let src = CGImageSourceCreateWithURL(GooglePhotos.fileURL(name) as CFURL, nil),
                  let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any],
                  let exif = props[kCGImagePropertyExifDictionary] as? [CFString: Any],
                  let stamp = exif[kCGImagePropertyExifDateTimeOriginal] as? String,
                  let time = exifDate.date(from: stamp) else { return nil }
            return Photo(key: "g:demo-\(name)", source: .google, ref: name,
                         width: props[kCGImagePropertyPixelWidth] as? Int ?? 0,
                         height: props[kCGImagePropertyPixelHeight] as? Int ?? 0, time: time)
        }
    }
}
#endif
