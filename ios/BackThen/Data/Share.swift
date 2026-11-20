import ImageIO
import UIKit

/// Opens the system share sheet with the photos themselves, so any app can take them.
@MainActor
enum Share {
    /// True while photos are being got ready, so a second tap doesn't start again.
    private(set) static var busy = false

    /// Removes leaked `share-*` temp folders older than `age` (e.g. if the app terminated mid-share).
    nonisolated static func pruneStaleShares(olderThan age: TimeInterval = 3600) {
        let fm = FileManager.default
        let tmp = fm.temporaryDirectory
        let cutoff = Date().addingTimeInterval(-age)
        let entries = (try? fm.contentsOfDirectory(
            at: tmp,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles]
        )) ?? []
        for entry in entries where entry.lastPathComponent.hasPrefix("share-") {
            let mod = (try? entry.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            if mod < cutoff {
                try? fm.removeItem(at: entry)
            }
        }
    }

    /// `done` runs when the system sheet closes. Returns `true` when the sheet was presented,
    /// or `false` when nothing could be shared.
    @discardableResult
    static func photos(_ items: [Photo], title: String, done: (() -> Void)? = nil) async -> Bool {
        guard !busy else { return true }
        busy = true
        defer { busy = false }
        let dir = FileManager.default.temporaryDirectory.appending(path: "share-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let urls = await Task.detached(priority: .userInitiated) {
            Share.pruneStaleShares()
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.calendar = .app
            f.dateFormat = "yyyy-MM-dd"
            var out: [URL] = []
            for (i, p) in items.enumerated() {
                switch p.source {
                case .sample:
                    let url = SamplePhotos.fileURL(p.ref)
                    if FileManager.default.fileExists(atPath: url.path) { out.append(url) }
                case .device:
                    guard let got = await PhotoLibrary.data(id: p.ref) else { continue }
                    // Name files by date so they make sense where they land.
                    let name = dir.appending(path: "\(f.string(from: p.time))-\(i + 1)")
                    if let url = Share.shareFile(got.0, type: got.1, name: name) { out.append(url) }
                }
            }
            return out
        }.value
        guard !urls.isEmpty, let top = topController() else {
            try? FileManager.default.removeItem(at: dir)
            return false
        }
        let activityItems = urls.map { ShareItemSource(url: $0, title: title) }
        let sheet = UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
        sheet.completionWithItemsHandler = { _, _, _, _ in
            try? FileManager.default.removeItem(at: dir)
            done?()
        }
        configurePopover(sheet, in: top)
        top.present(sheet, animated: true)
        return true
    }

    /// Writes one photo for sharing. JPEG, HEIC and PNG go out as they are, keeping their date
    /// and place. Anything else, such as a ProRAW file, becomes a JPEG up to 4096 px, made
    /// without opening the full picture.
    nonisolated static func shareFile(_ data: Data, type: String?, name: URL) -> URL? {
        let kept: [String: String] = [
            "public.jpeg": "jpg", "public.heic": "heic", "public.heif": "heic", "public.png": "png",
        ]
        if let type, let ext = kept[type] {
            let url = name.appendingPathExtension(ext)
            return (try? data.write(to: url, options: .atomic)) != nil ? url : nil
        }
        let url = name.appendingPathExtension("jpg")
        guard let src = CGImageSourceCreateWithData(data as CFData, nil),
              let dest = CGImageDestinationCreateWithURL(url as CFURL, "public.jpeg" as CFString, 1, nil) else { return nil }
        let opts: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: 4096,
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary) else { return nil }
        // Keep the date and place; reset orientation and strip stale un-downsampled dimensions.
        var props = (CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any]) ?? [:]
        props[kCGImagePropertyOrientation] = 1
        props.removeValue(forKey: kCGImagePropertyPixelWidth)
        props.removeValue(forKey: kCGImagePropertyPixelHeight)
        if var tiff = props[kCGImagePropertyTIFFDictionary] as? [CFString: Any] {
            tiff[kCGImagePropertyTIFFOrientation] = 1
            props[kCGImagePropertyTIFFDictionary] = tiff
        }
        if var exif = props[kCGImagePropertyExifDictionary] as? [CFString: Any] {
            exif.removeValue(forKey: kCGImagePropertyExifPixelXDimension)
            exif.removeValue(forKey: kCGImagePropertyExifPixelYDimension)
            props[kCGImagePropertyExifDictionary] = exif
        }
        props[kCGImageDestinationLossyCompressionQuality] = 0.92
        CGImageDestinationAddImage(dest, image, props as CFDictionary)
        return CGImageDestinationFinalize(dest) ? url : nil
    }

    /// Opens the system share sheet with one file, such as a reel. Its Save Video puts it in Photos.
    static func file(_ url: URL, title: String) {
        guard let top = topController() else { return }
        let sheet = UIActivityViewController(activityItems: [ShareItemSource(url: url, title: title)], applicationActivities: nil)
        configurePopover(sheet, in: top)
        top.present(sheet, animated: true)
    }

    private static func configurePopover(_ sheet: UIActivityViewController, in controller: UIViewController) {
        guard let pop = sheet.popoverPresentationController else { return }
        pop.sourceView = controller.view
        pop.sourceRect = CGRect(x: controller.view.bounds.midX, y: controller.view.bounds.midY, width: 0, height: 0)
        pop.permittedArrowDirections = []
    }

    private static func topController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let active = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        var vc = (active?.keyWindow ?? active?.windows.first(where: \.isKeyWindow) ?? active?.windows.first)?.rootViewController
        while let next = vc?.presentedViewController { vc = next }
        return vc
    }
}

/// Provides a share URL along with its email/share-sheet subject without private KVC keys.
private final class ShareItemSource: NSObject, UIActivityItemSource {
    let url: URL
    let title: String

    init(url: URL, title: String) {
        self.url = url
        self.title = title
    }

    func activityViewControllerPlaceholderItem(_ activityViewController: UIActivityViewController) -> Any {
        url
    }

    func activityViewController(_ activityViewController: UIActivityViewController, itemForActivityType activityType: UIActivity.ActivityType?) -> Any? {
        url
    }

    func activityViewController(_ activityViewController: UIActivityViewController, subjectForActivityType activityType: UIActivity.ActivityType?) -> String {
        title
    }
}
