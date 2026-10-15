import UIKit

/// Opens the system share sheet with the photos themselves, so any app can take them.
@MainActor
enum Share {
    /// `done` runs when the system sheet closes.
    static func photos(_ photos: [Photo], title: String, done: @escaping () -> Void = {}) async {
        // Reads and writes the files off the main thread, so the app doesn't freeze on big shares.
        let urls: [URL] = await Task.detached(priority: .userInitiated) {
            let fm = FileManager.default
            let tmp = fm.temporaryDirectory
            // Clear out files from earlier shares; those sheets are closed by now.
            for old in (try? fm.contentsOfDirectory(at: tmp, includingPropertiesForKeys: nil)) ?? []
            where old.lastPathComponent.hasPrefix("share-") {
                try? fm.removeItem(at: old)
            }
            let dir = tmp.appending(path: "share-\(UUID().uuidString)")
            try? fm.createDirectory(at: dir, withIntermediateDirectories: true)
            let f = DateFormatter()
            f.locale = Locale(identifier: "en_US_POSIX")
            f.dateFormat = "yyyy-MM-dd"
            var out: [URL] = []
            for (i, p) in photos.enumerated() {
                switch p.source {
                case .google:
                    out.append(GooglePhotos.fileURL(p.ref))
                case .device:
                    guard let data = await PhotoLibrary.data(id: p.ref) else { continue }
                    // Name files by date so they make sense where they land.
                    let url = dir.appending(path: "\(f.string(from: p.time))-\(i + 1).jpg")
                    let jpeg = UIImage(data: data)?.jpegData(compressionQuality: 0.92) ?? data
                    try? jpeg.write(to: url, options: .atomic)
                    out.append(url)
                }
            }
            return out
        }.value
        guard !urls.isEmpty, let top = topController() else { return }
        let sheet = UIActivityViewController(activityItems: urls, applicationActivities: nil)
        sheet.setValue(title, forKey: "subject")
        sheet.completionWithItemsHandler = { _, _, _, _ in done() }
        sheet.popoverPresentationController?.sourceView = top.view
        top.present(sheet, animated: true)
    }

    private static func topController() -> UIViewController? {
        var vc = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first?.rootViewController
        while let next = vc?.presentedViewController { vc = next }
        return vc
    }
}
