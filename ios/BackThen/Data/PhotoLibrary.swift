import ImageIO
import Photos
import UIKit

/// Reads the phone's Photos library through PhotoKit.
enum PhotoLibrary {
    private struct LibrarySignature: Equatable {
        let count: Int
        let firstID: String?
        let lastID: String?
        let lastMod: Date?
    }

    private static let signatureLock = NSLock()
    private static var lastSignature: LibrarySignature?

    static var hasAccess: Bool {
        let s = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        return s == .authorized || s == .limited
    }

    static var canAsk: Bool { PHPhotoLibrary.authorizationStatus(for: .readWrite) == .notDetermined }

    static func requestAccess() async -> Bool {
        let s = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        return s == .authorized || s == .limited
    }

    private static func imageOptions() -> PHFetchOptions {
        let o = PHFetchOptions()
        o.includeAssetSourceTypes = [.typeUserLibrary, .typeCloudShared, .typeiTunesSynced]
        return o
    }

    /// Photo counts per day for the whole library, built off the main thread. Nil when nothing
    /// changed since the last scan (same count, ends and newest edit), unless `force`.
    static func deviceIndex(force: Bool = false) async -> DayIndex? {
        await Task.detached(priority: .utility) {
            let o = imageOptions()
            o.sortDescriptors = [NSSortDescriptor(key: "modificationDate", ascending: true)]
            let result = PHAsset.fetchAssets(with: .image, options: o)
            let sig = LibrarySignature(
                count: result.count,
                firstID: result.firstObject?.localIdentifier,
                lastID: result.lastObject?.localIdentifier,
                lastMod: result.lastObject?.modificationDate
            )
            signatureLock.lock()
            let unchanged = !force && lastSignature == sig
            if !unchanged { lastSignature = sig }
            signatureLock.unlock()
            if unchanged { return nil }
            var times: [Date] = []
            times.reserveCapacity(result.count)
            result.enumerateObjects { asset, _, _ in
                if let d = asset.creationDate { times.append(d) }
            }
            return buildDayIndex(times)
        }.value
    }

    /// Photos taken on one calendar day across multiple years in a single PhotoKit query, grouped by year.
    static func photos(years: [Int], on md: MonthDay) async -> [Int: [Photo]] {
        let ranges = years.compactMap { y -> (Int, Date, Date)? in
            guard let (s, e) = dayRange(year: y, md) else { return nil }
            return (y, s, e)
        }
        guard !ranges.isEmpty else { return [:] }
        return await Task.detached(priority: .userInitiated) {
            let o = imageOptions()
            let subs = ranges.map { _, start, end in
                NSPredicate(format: "creationDate >= %@ AND creationDate < %@", start as NSDate, end as NSDate)
            }
            o.predicate = NSCompoundPredicate(orPredicateWithSubpredicates: subs)
            o.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
            let result = PHAsset.fetchAssets(with: .image, options: o)
            let cal = Calendar.app
            var out: [Int: [Photo]] = [:]
            result.enumerateObjects { a, _, _ in
                let time = a.creationDate ?? Date()
                let y = cal.component(.year, from: time)
                out[y, default: []].append(
                    Photo(key: "d:\(a.localIdentifier)", source: .device, ref: a.localIdentifier,
                          width: a.pixelWidth, height: a.pixelHeight, time: time)
                )
            }
            return out
        }.value
    }

    /// Saves photos into a named album in Photos, making it if needed.
    static func saveAlbum(name: String, photos: [Photo]) async throws {
        let ids = photos.filter { $0.source == .device }.map(\.ref)
        let files = photos.filter { $0.source == .google }.map { GooglePhotos.fileURL($0.ref) }
        let existing: PHAssetCollection? = {
            let o = PHFetchOptions()
            o.predicate = NSPredicate(format: "title = %@", name)
            return PHAssetCollection.fetchAssetCollections(with: .album, subtype: .albumRegular, options: o).firstObject
        }()
        try await PHPhotoLibrary.shared().performChanges {
            let request = existing.flatMap { PHAssetCollectionChangeRequest(for: $0) }
                ?? PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: name)
            let assets = PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil)
            request.addAssets(assets)
            let placeholders = files.compactMap {
                PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: $0)?.placeholderForCreatedAsset
            }
            request.addAssets(placeholders as NSArray)
        }
    }

    static let imageManager = PHCachingImageManager()

    /// Loads a device photo at roughly the given pixel size. High-quality mode calls back once.
    static func image(id: String, size: CGSize) async -> UIImage? {
        let bucketed = ImageCache.bucket(size)
        let key = ImageCache.key(id, bucketed)
        if let hit = ImageCache.get(key) { return hit }
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject else { return nil }
        let o = PHImageRequestOptions()
        o.isNetworkAccessAllowed = true
        o.deliveryMode = .highQualityFormat
        o.resizeMode = .fast
        // Scrolling a tile away cancels its task; pass that on to PhotoKit so it stops the work.
        let box = RequestBox()
        let image: UIImage? = await withTaskCancellationHandler {
            await withCheckedContinuation { cont in
                box.start(cont) {
                    imageManager.requestImage(for: asset, targetSize: bucketed, contentMode: .aspectFill, options: o) { image, _ in
                        box.finish(image)
                    }
                }
            }
        } onCancel: {
            box.cancel()
        }
        if let image { ImageCache.set(image, key) }
        return image
    }

    /// Full image data for sharing.
    static func data(id: String) async -> Data? {
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject else { return nil }
        let o = PHImageRequestOptions()
        o.isNetworkAccessAllowed = true
        o.version = .current
        return await withCheckedContinuation { cont in
            PHImageManager.default().requestImageDataAndOrientation(for: asset, options: o) { data, _, _, _ in
                cont.resume(returning: data)
            }
        }
    }
}

/// One PhotoKit image request and the task waiting on it. Resumes the task exactly once,
/// whether the image arrives or the task is cancelled first.
private final class RequestBox: @unchecked Sendable {
    private let lock = NSLock()
    private var cont: CheckedContinuation<UIImage?, Never>?
    private var id: PHImageRequestID?
    private var done = false

    func start(_ cont: CheckedContinuation<UIImage?, Never>, request: () -> PHImageRequestID) {
        lock.lock()
        if done { lock.unlock(); cont.resume(returning: nil); return }
        self.cont = cont
        lock.unlock()
        let id = request()
        lock.lock()
        let cancelled = done
        if !cancelled { self.id = id }
        lock.unlock()
        if cancelled { PhotoLibrary.imageManager.cancelImageRequest(id) }
    }

    func finish(_ image: UIImage?) {
        lock.lock()
        let c = cont
        cont = nil
        done = true
        lock.unlock()
        c?.resume(returning: image)
    }

    func cancel() {
        lock.lock()
        let c = cont
        let id = self.id
        cont = nil
        done = true
        lock.unlock()
        if let id { PhotoLibrary.imageManager.cancelImageRequest(id) }
        c?.resume(returning: nil)
    }
}

/// Decoded thumbnails kept in memory, so scrolling back up doesn't load them again.
/// Capped at about 64 MB; iOS also empties it when memory runs low.
enum ImageCache {
    private static let cache: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.countLimit = 200
        c.totalCostLimit = 64 * 1024 * 1024
        return c
    }()

    static func bucket(_ size: CGSize) -> CGSize {
        let step: CGFloat = 64
        return CGSize(
            width: max(step, ceil(size.width / step) * step),
            height: max(step, ceil(size.height / step) * step)
        )
    }

    static func key(_ id: String, _ size: CGSize) -> NSString {
        let b = bucket(size)
        return "\(id)-\(Int(b.width))x\(Int(b.height))" as NSString
    }

    static func get(_ key: NSString) -> UIImage? { cache.object(forKey: key) }

    /// Full-screen images are skipped, so a few of them can't push out every thumbnail.
    static func set(_ image: UIImage, _ key: NSString) {
        let px = image.size.width * image.scale * image.size.height * image.scale
        guard px <= 1200 * 1200 else { return }
        cache.setObject(image, forKey: key, cost: Int(px * 4))
    }

    /// Downsamples a local Google Photos JPEG directly via ImageIO without decoding the full 2048px bitmap first.
    static func downsampledImage(at url: URL, size: CGSize) -> UIImage? {
        guard let src = CGImageSourceCreateWithURL(url as CFURL, [kCGImageSourceShouldCache: false] as CFDictionary) else {
            return UIImage(contentsOfFile: url.path)
        }
        let maxPixel = max(Int(max(size.width, size.height)), 128)
        let opts: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
        ]
        guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary) else {
            return UIImage(contentsOfFile: url.path)
        }
        return UIImage(cgImage: cg)
    }
}
