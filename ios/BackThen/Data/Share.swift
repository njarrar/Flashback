import ImageIO
import UIKit

/// Opens the system share sheet with the photos themselves, so any app can take them.
@MainActor
enum Share {
    /// True while photos are being got ready, so a second tap doesn't start again.
    private(set) static var busy = false

    /// `done` runs when the system sheet closes. Returns once the sheet is up, or nothing could be shared.
    static func photos(_ photos: [Photo], title: String, done: @escaping () -> Void = {}) async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        let dir = FileManager.default.temporaryDirectory.appending(path: "share-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let urls = await Task.detached(priority: .userInitiated) {
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.dateFormat = "yyyy-MM-dd"
            var out: [URL] = []
            for (i, p) in photos.enumerated() {
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
            return
        }
        let sheet = UIActivityViewController(activityItems: urls, applicationActivities: nil)
        sheet.setValue(title, forKey: "subject")
        sheet.completionWithItemsHandler = { _, _, _, _ in
            try? FileManager.default.removeItem(at: dir)
            done()
        }
        sheet.popoverPresentationController?.sourceView = top.view
        top.present(sheet, animated: true)
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
        // Keep the date and place; the picture is already turned the right way up.
        var props = (CGImageSourceCopyPropertiesAtIndex(src, 0, nil) as? [CFString: Any]) ?? [:]
        props[kCGImagePropertyOrientation] = 1
        props[kCGImageDestinationLossyCompressionQuality] = 0.92
        CGImageDestinationAddImage(dest, image, props as CFDictionary)
        return CGImageDestinationFinalize(dest) ? url : nil
    }

    /// Opens the system share sheet with one file, such as a reel. Its Save Video puts it in Photos.
    static func file(_ url: URL, title: String) {
        guard let top = topController() else { return }
        let sheet = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        sheet.setValue(title, forKey: "subject")
        sheet.popoverPresentationController?.sourceView = top.view
        top.present(sheet, animated: true)
    }

    private static func topController() -> UIViewController? {
        var vc = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first?.rootViewController
        while let next = vc?.presentedViewController { vc = next }
        return vc
    }
}
