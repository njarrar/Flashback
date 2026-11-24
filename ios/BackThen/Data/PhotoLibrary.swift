import ImageIO
import Photos
import PhotosUI
import UIKit

/// Reads the phone's Photos library through PhotoKit.
enum PhotoLibrary {
    enum SaveError: Error { case noAssets }

    /// A quick fingerprint of the library. Saved with the day index, so a launch can skip
    /// the full scan when nothing changed.
    struct LibrarySignature: Codable, Equatable, Sendable {
        let count: Int
        let firstID: String?
        let lastID: String?
        let lastMod: Date?
        let timeZoneID: String?

        init(
            count: Int,
            firstID: String?,
            lastID: String?,
            lastMod: Date?,
            timeZoneID: String? = TimeZone.autoupdatingCurrent.identifier
        ) {
            self.count = count
            self.firstID = firstID
            self.lastID = lastID
            self.lastMod = lastMod
            self.timeZoneID = timeZoneID
        }
    }

    static var authorizationStatus: PHAuthorizationStatus {
        PHPhotoLibrary.authorizationStatus(for: .readWrite)
    }

    static var hasAccess: Bool {
        let s = authorizationStatus
        return s == .authorized || s == .limited
    }

    static var isLimited: Bool { authorizationStatus == .limited }

    static var isDenied: Bool {
        let s = authorizationStatus
        return s == .denied || s == .restricted
    }

    static var canAsk: Bool { authorizationStatus == .notDetermined }

    static func requestAccess() async -> Bool {
        let s = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        return s == .authorized || s == .limited
    }

    @MainActor
    static func presentLimitedPicker(completion: (@MainActor () -> Void)? = nil) {
        guard let top = topViewController() else { return }
        PHPhotoLibrary.shared().presentLimitedLibraryPicker(from: top) { _ in
            Task { @MainActor in completion?() }
        }
    }

    @MainActor
    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let active = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        var vc = (active?.keyWindow ?? active?.windows.first(where: \.isKeyWindow) ?? active?.windows.first)?.rootViewController
        while let next = vc?.presentedViewController, !next.isBeingDismissed { vc = next }
        return vc
    }

    static func saveVideo(_ url: URL) async -> Bool {
        guard FileManager.default.fileExists(atPath: url.path) else { return false }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: url)
            }
            return true
        } catch {
            return false
        }
    }

    static func imageOptions() -> PHFetchOptions {
        let o = PHFetchOptions()
        o.includeAssetSourceTypes = [.typeUserLibrary, .typeCloudShared, .typeiTunesSynced]
        return o
    }

    /// Batch-checks which local asset identifiers still exist in the library.
    static func existingIdentifiers(from ids: [String]) async -> Set<String> {
        guard !ids.isEmpty else { return [] }
        return await Task.detached(priority: .utility) {
            var present = Set<String>()
            present.reserveCapacity(ids.count)
            PHAsset.fetchAssets(withLocalIdentifiers: ids, options: imageOptions()).enumerateObjects { a, _, _ in
                present.insert(a.localIdentifier)
                assetCache.setObject(a, forKey: a.localIdentifier as NSString)
            }
            return present
        }.value
    }

    /// Small in-memory asset cache populated by day/trip queries to avoid per-tile PhotoKit IPC lookups.
    private static let assetCache: NSCache<NSString, PHAsset> = {
        let c = NSCache<NSString, PHAsset>()
        c.countLimit = 400
        return c
    }()

    static func clearAssetCache() {
        assetCache.removeAllObjects()
    }

    /// Photo counts per day for the whole library, built off the main thread. Nil when the
    /// library still matches `known` (same count, ends, newest edit, and time zone).
    static func deviceIndex(known: LibrarySignature?) async -> (index: DayIndex, signature: LibrarySignature)? {
        await Task.detached(priority: .utility) {
            let o = imageOptions()
            o.sortDescriptors = [NSSortDescriptor(key: "modificationDate", ascending: true)]
            let result = PHAsset.fetchAssets(with: .image, options: o)
            let sig = LibrarySignature(
                count: result.count,
                firstID: result.firstObject?.localIdentifier,
                lastID: result.lastObject?.localIdentifier,
                lastMod: result.lastObject?.modificationDate,
                timeZoneID: TimeZone.autoupdatingCurrent.identifier
            )
            if sig == known { return nil }
            var times: [Date] = []
            times.reserveCapacity(result.count)
            result.enumerateObjects { asset, _, _ in
                if let d = asset.creationDate { times.append(d) }
            }
            return (buildDayIndex(times), sig)
        }.value
    }

    /// New photos since the last check, read from Photos' own record of changes.
    struct NewPhotos: Sendable {
        let times: [Date]
        let spots: [PhotoSpot]
        /// Where the record stands now, to start from next time.
        let token: Data
    }

    /// Where Photos' record of changes stands now, saved after a full scan.
    static func changeToken() async -> Data? {
        await Task.detached(priority: .utility) {
            try? NSKeyedArchiver.archivedData(withRootObject: PHPhotoLibrary.shared().currentChangeToken, requiringSecureCoding: true)
        }.value
    }

    /// The photos added since `since`, so a launch after taking a few photos counts just those.
    /// Nil when anything was deleted or changed, or the record can't be read: then the caller
    /// scans the whole library as before.
    static func newPhotos(since: Data) async -> NewPhotos? {
        await Task.detached(priority: .utility) { () -> NewPhotos? in
            let library = PHPhotoLibrary.shared()
            guard let token = try? NSKeyedUnarchiver.unarchivedObject(ofClass: PHPersistentChangeToken.self, from: since),
                  let changes = try? library.fetchPersistentChanges(since: token) else { return nil }
            var latestToken = token
            var inserted = Set<String>()
            var touched = Set<String>()
            var readable = true
            for change in changes {
                latestToken = change.changeToken
                guard let details = try? change.changeDetails(for: PHObjectType.asset) else {
                    readable = false
                    break
                }
                inserted.formUnion(details.insertedLocalIdentifiers)
                touched.formUnion(details.updatedLocalIdentifiers)
                touched.formUnion(details.deletedLocalIdentifiers)
            }
            // A change to a photo just added is fine: it is read as it is now.
            guard readable, touched.subtracting(inserted).isEmpty,
                  let data = try? NSKeyedArchiver.archivedData(withRootObject: latestToken, requiringSecureCoding: true) else { return nil }
            var times: [Date] = []
            var spots: [PhotoSpot] = []
            if !inserted.isEmpty {
                let kinds: PHAssetSourceType = [.typeUserLibrary, .typeCloudShared, .typeiTunesSynced]
                PHAsset.fetchAssets(withLocalIdentifiers: Array(inserted), options: imageOptions()).enumerateObjects { a, _, _ in
                    guard a.mediaType == .image, kinds.contains(a.sourceType), let time = a.creationDate else { return }
                    times.append(time)
                    if let at = a.location?.coordinate { spots.append(PhotoSpot(time: time, lat: at.latitude, lon: at.longitude)) }
                }
            }
            return NewPhotos(times: times, spots: spots, token: data)
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
                assetCache.setObject(a, forKey: a.localIdentifier as NSString)
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
        let files = photos.filter { $0.source == .sample }
            .map { SamplePhotos.fileURL($0.ref) }
            .filter { FileManager.default.fileExists(atPath: $0.path) }
        try await Task.detached(priority: .userInitiated) {
            let assets = ids.isEmpty ? nil : PHAsset.fetchAssets(withLocalIdentifiers: ids, options: imageOptions())
            let assetCount = assets?.count ?? 0
            guard assetCount > 0 || !files.isEmpty else { throw SaveError.noAssets }
            let o = PHFetchOptions()
            o.predicate = NSPredicate(format: "title = %@", name)
            let existing = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .albumRegular, options: o).firstObject
            try await PHPhotoLibrary.shared().performChanges {
                let request = existing.flatMap { PHAssetCollectionChangeRequest(for: $0) }
                    ?? PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: name)
                if let assets, assets.count > 0 {
                    request.addAssets(assets)
                }
                if !files.isEmpty {
                    let placeholders = files.compactMap {
                        PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: $0)?.placeholderForCreatedAsset
                    }
                    if !placeholders.isEmpty {
                        request.addAssets(placeholders as NSArray)
                    }
                }
            }
        }.value
    }

    static let imageManager = PHCachingImageManager()

    /// Loads a device photo at roughly the given pixel size. High-quality mode calls back once.
    /// `fit` keeps the whole photo inside the size (the viewer, reels); otherwise it fills it
    /// (tiles), which for a panorama means a picture many times wider than the size.
    /// With `preview`, a quick low-quality copy goes there first while the sharp one loads, so
    /// a tile shows a picture at once instead of gray.
    static func image(id: String, size: CGSize, fit: Bool = false, preview: (@Sendable (UIImage) -> Void)? = nil) async -> UIImage? {
        let bucketed = ImageCache.bucket(size)
        let key = ImageCache.key(id, bucketed, fit: fit)
        if let hit = ImageCache.get(key) { return hit }
        let asset: PHAsset
        if let cached = assetCache.object(forKey: id as NSString) {
            asset = cached
        } else if let fetched = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: imageOptions()).firstObject {
            assetCache.setObject(fetched, forKey: id as NSString)
            asset = fetched
        } else {
            return nil
        }
        let o = PHImageRequestOptions()
        o.isNetworkAccessAllowed = true
        o.deliveryMode = preview == nil ? .highQualityFormat : .opportunistic
        o.resizeMode = .fast
        // Scrolling a tile away cancels its task; pass that on to PhotoKit so it stops the work.
        let box = RequestBox<UIImage> { PhotoLibrary.imageManager.cancelImageRequest($0) }
        let image: UIImage? = await withTaskCancellationHandler {
            await withCheckedContinuation { cont in
                box.start(cont) {
                    imageManager.requestImage(for: asset, targetSize: bucketed, contentMode: fit ? .aspectFit : .aspectFill, options: o) { image, info in
                        // The quick copy comes first; the request ends with the sharp one.
                        if (info?[PHImageResultIsDegradedKey] as? Bool) == true {
                            if let image { preview?(image) }
                            return
                        }
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

    /// Full image data for sharing, with its type ("public.jpeg", "public.heic", ...).
    static func data(id: String) async -> (Data, String?)? {
        let asset: PHAsset
        if let cached = assetCache.object(forKey: id as NSString) {
            asset = cached
        } else if let fetched = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: imageOptions()).firstObject {
            assetCache.setObject(fetched, forKey: id as NSString)
            asset = fetched
        } else {
            return nil
        }
        let o = PHImageRequestOptions()
        o.isNetworkAccessAllowed = true
        o.version = .current
        let box = RequestBox<(Data, String?)> { PHImageManager.default().cancelImageRequest($0) }
        return await withTaskCancellationHandler {
            await withCheckedContinuation { cont in
                box.start(cont) {
                    PHImageManager.default().requestImageDataAndOrientation(for: asset, options: o) { data, type, _, _ in
                        box.finish(data.map { ($0, type) })
                    }
                }
            }
        } onCancel: {
            box.cancel()
        }
    }
}

/// One PhotoKit request and the task waiting on it. Resumes the task exactly once,
/// whether the result arrives synchronously, asynchronously, or the task is cancelled first.
private final class RequestBox<T>: @unchecked Sendable {
    private let lock = NSLock()
    private let cancelRequest: @Sendable (PHImageRequestID) -> Void
    private var cont: CheckedContinuation<T?, Never>?
    private var id: PHImageRequestID?
    private var done = false
    private var cancelled = false

    init(cancelRequest: @escaping @Sendable (PHImageRequestID) -> Void) {
        self.cancelRequest = cancelRequest
    }

    func start(_ cont: CheckedContinuation<T?, Never>, request: () -> PHImageRequestID) {
        lock.lock()
        if done {
            lock.unlock()
            cont.resume(returning: nil)
            return
        }
        self.cont = cont
        lock.unlock()
        let reqID = request()
        lock.lock()
        let shouldCancel = cancelled
        if !done { self.id = reqID }
        lock.unlock()
        if shouldCancel { cancelRequest(reqID) }
    }

    func finish(_ value: T?) {
        lock.lock()
        let c = cont
        cont = nil
        done = true
        lock.unlock()
        c?.resume(returning: value)
    }

    func cancel() {
        lock.lock()
        let c = cont
        let reqID = id
        cont = nil
        done = true
        cancelled = true
        lock.unlock()
        if let reqID { cancelRequest(reqID) }
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
        let w = size.width.isFinite && size.width > 0 ? min(size.width, 4096) : step
        let h = size.height.isFinite && size.height > 0 ? min(size.height, 4096) : step
        return CGSize(
            width: max(step, ceil(w / step) * step),
            height: max(step, ceil(h / step) * step)
        )
    }

    static func key(_ id: String, _ size: CGSize, fit: Bool = false) -> NSString {
        let b = bucket(size)
        return "\(id)-\(Int(b.width))x\(Int(b.height))\(fit ? "-fit" : "")" as NSString
    }

    static func get(_ key: NSString) -> UIImage? { cache.object(forKey: key) }

    /// Full-screen viewer images are skipped, while 3x YearCards and hero mosaic tiles stay cached.
    static func set(_ image: UIImage, _ key: NSString) {
        let px = image.size.width * image.scale * image.size.height * image.scale
        guard px.isFinite, px > 0, px <= 2_400_000 else { return }
        cache.setObject(image, forKey: key, cost: Int(px * 4))
    }

    /// Downsamples a JPEG in app storage directly via ImageIO without decoding the full 2048px bitmap first.
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

/// Bridges PhotoKit change notifications (including Limited Photos edits) back to `@MainActor`.
final class PhotoLibraryObserver: NSObject, PHPhotoLibraryChangeObserver, @unchecked Sendable {
    private let onChange: @Sendable () -> Void

    init(onChange: @escaping @Sendable () -> Void) {
        self.onChange = onChange
        super.init()
        PHPhotoLibrary.shared().register(self)
    }

    deinit {
        PHPhotoLibrary.shared().unregisterChangeObserver(self)
    }

    func photoLibraryDidChange(_ changeInstance: PHChange) {
        onChange()
    }
}
