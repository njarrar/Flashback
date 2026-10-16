import UIKit

/// Opens the system share sheet with the photos themselves, so any app can take them.
@MainActor
enum Share {
    /// `done` runs when the system sheet closes.
    static func photos(_ photos: [Photo], title: String, done: @escaping () -> Void = {}) async {
        let dir = FileManager.default.temporaryDirectory.appending(path: "share-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let urls = await Task.detached(priority: .userInitiated) {
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.dateFormat = "yyyy-MM-dd"
            var out: [URL] = []
            for (i, p) in photos.enumerated() {
                switch p.source {
                case .google:
                    let url = GooglePhotos.fileURL(p.ref)
                    if FileManager.default.fileExists(atPath: url.path) { out.append(url) }
                case .device:
                    guard let data = await PhotoLibrary.data(id: p.ref) else { continue }
                    // Name files by date so they make sense where they land.
                    let url = dir.appending(path: "\(f.string(from: p.time))-\(i + 1).jpg")
                    // A JPEG goes out as is, keeping its date and place; anything else becomes one.
                    let payload = data.starts(with: [0xFF, 0xD8, 0xFF]) ? data : (UIImage(data: data)?.jpegData(compressionQuality: 0.92) ?? data)
                    if (try? payload.write(to: url, options: .atomic)) != nil {
                        out.append(url)
                    }
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

    private static func topController() -> UIViewController? {
        var vc = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first?.rootViewController
        while let next = vc?.presentedViewController { vc = next }
        return vc
    }
}
