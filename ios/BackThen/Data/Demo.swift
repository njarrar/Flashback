#if DEBUG
import Foundation
import AVFoundation
import ImageIO
import UIKit

/// Debug-only screen setups for the README screenshots.
/// Launch with `-scene today|calendar|viewer|favorites|album`, or for the text check also
/// reminder, settings, select, share, addalbum, cards, strip, empty, reel or reelmore. reelexport
/// makes a reel without showing it and writes frames and a note to Documents, for the text check.
///
/// The simulator can't be granted photo access without a tap, so the screenshot
/// workflow drops sample JPEGs in `SamplePhotos.folder`, and the demo shows them
/// in place of the phone's library.
extension AppStore {
    static var launchScene: String? {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-scene"), i + 1 < args.count else { return nil }
        return args[i + 1]
    }

    func runDemo(_ scene: String) async {
        samplePhotos = Self.loadSamples()
        saved.sources.device = !samplePhotos.isEmpty
        saved.deviceIndex = buildDayIndex(samplePhotos.map(\.time))
        saved.layout = scene == "cards" ? .cards : scene == "strip" ? .strip : .mosaic
        // The reminder step of the welcome, with the photos found.
        saved.onboarded = scene != "reminder"
        if !saved.onboarded { return }
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
        // The sample photos skip tomorrow, so it shows the empty day.
        case "empty": day = .of(Date().addingTimeInterval(86_400))
        case "settings", "select", "share", "addalbum", "reel", "reelmore": demoScene = scene
        case "reelexport": await checkReelExport(photos)
        default: break
        }
    }

    private static func loadSamples() -> [Photo] {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: SamplePhotos.folder.path)) ?? []
        let exifDate = DateFormatter()
        exifDate.locale = Locale(identifier: "en_US_POSIX")
        exifDate.dateFormat = "yyyy:MM:dd HH:mm:ss"
        return names.filter { $0.hasSuffix(".jpg") }.sorted().compactMap { name in
            guard let src = CGImageSourceCreateWithURL(SamplePhotos.fileURL(name) as CFURL, nil),
                  let props = CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any],
                  let exif = props[kCGImagePropertyExifDictionary] as? [CFString: Any],
                  let stamp = exif[kCGImagePropertyExifDateTimeOriginal] as? String,
                  let time = exifDate.date(from: stamp) else { return nil }
            return Photo(key: "s:\(name)", source: .sample, ref: name,
                         width: props[kCGImagePropertyPixelWidth] as? Int ?? 0,
                         height: props[kCGImagePropertyPixelHeight] as? Int ?? 0, time: time)
        }
    }

    /// Makes a film-look reel of the sample photos, then writes four frames from it and a line
    /// with its size and length to Documents, so CI can check real video comes out.
    private func checkReelExport(_ photos: [Photo]) async {
        let docs = URL.documentsDirectory
        var note: String
        do {
            let ordered = reelOrder(Array(photos.prefix(4)), newestFirst: true)
            var small: [UIImage?] = []
            for p in ordered { small.append(await loadImage(p, size: CGSize(width: 800, height: 800), fit: true)) }
            var o = ReelOptions()
            o.look = .film
            let r = ReelRenderer(photos: ordered, options: o, small: small, reduceMotion: false)
            let url = try await ReelExport.make(renderer: r, name: "reel-check", load: { i in
                await loadImage(ordered[i], size: CGSize(width: ReelExport.photoSide, height: ReelExport.photoSide), fit: true)
            }, progress: { _ in })
            let asset = AVURLAsset(url: url)
            let seconds = try await asset.load(.duration).seconds
            let size = try await asset.loadTracks(withMediaType: .video).first?.load(.naturalSize) ?? .zero
            let gen = AVAssetImageGenerator(asset: asset)
            gen.requestedTimeToleranceBefore = .zero
            gen.requestedTimeToleranceAfter = .zero
            let plan = r.plan
            let times = [1.0, plan.items[1].start + plan.overlap / 2, plan.items[1].start + 1.5, plan.total - 0.5]
            for (n, s) in times.enumerated() {
                let (cg, _) = try await gen.image(at: CMTime(seconds: s, preferredTimescale: 600))
                try UIImage(cgImage: cg).pngData()?.write(to: docs.appending(path: "reel-check-\(n).png"))
            }
            let bytes = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int) ?? 0
            note = "ok \(Int(size.width))x\(Int(size.height)) \(seconds)s \(bytes) bytes, plan \(plan.total)s"
        } catch {
            note = "failed \(error)"
        }
        try? ("Reel check: " + note).write(to: docs.appending(path: "reel-check.txt"), atomically: true, encoding: .utf8)
    }
}
#endif
