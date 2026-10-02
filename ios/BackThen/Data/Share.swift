import UIKit

/// Opens the system share sheet with the photos themselves, so any app can take them.
@MainActor
enum Share {
    static func photos(_ photos: [Photo], title: String) async {
        let dir = FileManager.default.temporaryDirectory.appending(path: "share-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var urls: [URL] = []
        for (i, p) in photos.enumerated() {
            switch p.source {
            case .google:
                urls.append(GooglePhotos.fileURL(p.ref))
            case .device:
                guard let data = await PhotoLibrary.data(id: p.ref) else { continue }
                // Name files by date so they make sense where they land.
                let f = DateFormatter()
                f.dateFormat = "yyyy-MM-dd"
                let url = dir.appending(path: "\(f.string(from: p.time))-\(i + 1).jpg")
                let jpeg = UIImage(data: data)?.jpegData(compressionQuality: 0.92) ?? data
                try? jpeg.write(to: url)
                urls.append(url)
            }
        }
        guard !urls.isEmpty, let top = topController() else { return }
        let sheet = UIActivityViewController(activityItems: urls, applicationActivities: nil)
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
