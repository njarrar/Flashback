import ImageIO
import os
import Photos
import UIKit

/// Reads the phone's Photos library through PhotoKit.
enum PhotoLibrary {
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

    /// Photo count plus the first and last photo: if none of these moved, the index is still right.
    private struct Signature: Equatable {
        let count: Int
        let first: String?
        let last: String?
        let lastChanged: Date?
    }

    private static let lastSignature = OSAllocatedUnfairLock<Signature?>(initialState: nil)

    /// Counts photos per day straight from PhotoKit, without building a list of dates first.
    /// Returns nil when the library looks the same as last time, unless `force` is set.
    static func deviceIndex(force: Bool = false) async -> DayIndex? {
        await Task.detached(priority: .utility) {
            let result = PHAsset.fetchAssets(with: .image, options: imageOptions())
            let sig = Signature(count: result.count, first: result.firstObject?.localIdentifier,
                                last: result.lastObject?.localIdentifier,
                                lastChanged: result.lastObject?.modificationDate ?? result.lastObject?.creationDate)
            let same = lastSignature.withLock { last -> Bool in
                if !force, last == sig { return true }
                last = sig
                return false
            }
            if same { return nil }
            let cal = Calendar.current
            var index: DayIndex = [:]
            result.enumerateObjects { asset, _, _ in
                guard let d = asset.creationDate else { return }
                let c = cal.dateComponents([.year, .month, .day], from: d)
                guard let year = c.year, let month = c.month, let day = c.day else { return }
                index[MonthDay(month: month - 1, day: day).key, default: [:]][year, default: 0] += 1
            }
            return index
        }.value
    }

    /// Photos taken on one calendar day in each of `years`, oldest first, from one query.
    static func photos(years: [Int], on md: MonthDay) async -> [Int: [Photo]] {
        await Task.detached(priority: .userInitiated) {
            let cal = Calendar.current
            let days: [NSPredicate] = years.compactMap { year in
                guard let (start, end) = dayRange(year: year, md, calendar: cal) else { return nil }
                return NSPredicate(format: "creationDate >= %@ AND creationDate < %@", start as NSDate, end as NSDate)
            }
            guard !days.isEmpty else { return [:] }
            let o = imageOptions()
            o.predicate = NSCompoundPredicate(orPredicateWithSubpredicates: days)
            o.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
            var byYear: [Int: [Photo]] = [:]
            PHAsset.fetchAssets(with: .image, options: o).enumerateObjects { a, _, _ in
                guard let time = a.creationDate else { return }
                byYear[cal.component(.year, from: time), default: []].append(
                    Photo(key: "d:\(a.localIdentifier)", source: .device, ref: a.localIdentifier,
                          width: a.pixelWidth, height: a.pixelHeight, time: time))
            }
            return byYear
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

    /// Loads a device photo at roughly the given pixel size. `fit` keeps the whole photo inside
    /// the size (for the viewer); otherwise it fills it (for grid tiles). If the view goes away
    /// first, the PhotoKit request is cancelled.
    static func image(id: String, size: CGSize, fit: Bool = false) async -> UIImage? {
        let key = ImageCache.key("d:\(id)", size)
        if let hit = ImageCache.get(key) { return hit }
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject else { return nil }
        let o = PHImageRequestOptions()
        o.isNetworkAccessAllowed = true
        o.deliveryMode = .highQualityFormat
        o.resizeMode = .fast
        let request = ImageRequest()
        let image = await withTaskCancellationHandler {
            await withCheckedContinuation { cont in
                guard request.start(cont) else { return }
                let reqID = imageManager.requestImage(for: asset, targetSize: size, contentMode: fit ? .aspectFit : .aspectFill,
                                                   options: o) { image, info in
                    if (info?[PHImageResultIsDegradedKey] as? Bool) == true { return }
                    request.finish(image)
                }
                if request.started(reqID) { imageManager.cancelImageRequest(reqID) }
            }
        } onCancel: {
            if let id = request.cancel() { imageManager.cancelImageRequest(id) }
        }
        if let image { ImageCache.set(image, key) }
        return image
    }

    /// Reads a saved Google photo at about the given size. ImageIO decodes a small copy
    /// straight from the file, so the full photo never sits in memory.
    static func googleImage(ref: String, key: String, size: CGSize) async -> UIImage? {
        let cacheKey = ImageCache.key(key, size)
        if let hit = ImageCache.get(cacheKey) { return hit }
        let url = GooglePhotos.fileURL(ref)
        let image = await Task.detached(priority: .userInitiated) { () -> UIImage? in
            guard !Task.isCancelled, let src = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
            let opts: [CFString: Any] = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceShouldCacheImmediately: true,
                kCGImageSourceThumbnailMaxPixelSize: max(128, Int(max(size.width, size.height).rounded())),
            ]
            return CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary).map { UIImage(cgImage: $0) }
        }.value
        if let image { ImageCache.set(image, cacheKey) }
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

/// Decoded thumbnails kept in memory, so scrolling back up doesn't load them again.
/// Capped at about 64 MB; iOS also empties it when memory runs low.
enum ImageCache {
    private static let cache: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.countLimit = 300
        c.totalCostLimit = 64 * 1024 * 1024
        return c
    }()

    static func key(_ id: String, _ size: CGSize) -> NSString { "\(id)-\(Int(size.width))x\(Int(size.height))" as NSString }

    static func get(_ key: NSString) -> UIImage? { cache.object(forKey: key) }

    /// Full-screen images are skipped, so a few of them can't push out every thumbnail.
    static func set(_ image: UIImage, _ key: NSString) {
        let px = image.size.width * image.scale * image.size.height * image.scale
        guard px <= 1200 * 1200 else { return }
        cache.setObject(image, forKey: key, cost: Int(px * 4))
    }
}

/// Ties one PhotoKit image request to its waiting task. Resumes the task exactly once,
/// whether the photo arrives or the task is cancelled first.
private final class ImageRequest: @unchecked Sendable {
    private struct State {
        var id: PHImageRequestID?
        var cont: CheckedContinuation<UIImage?, Never>?
        var cancelled = false
    }
    private let state = OSAllocatedUnfairLock(uncheckedState: State())

    /// Returns false, and resumes the task at once, if it was already cancelled.
    func start(_ cont: CheckedContinuation<UIImage?, Never>) -> Bool {
        let cancelled = state.withLockUnchecked { s -> Bool in
            if !s.cancelled { s.cont = cont }
            return s.cancelled
        }
        if cancelled { cont.resume(returning: nil) }
        return !cancelled
    }

    /// Records the request id. Returns true if the task was cancelled meanwhile.
    func started(_ id: PHImageRequestID) -> Bool {
        state.withLockUnchecked { s in
            s.id = id
            return s.cancelled
        }
    }

    func finish(_ image: UIImage?) {
        let cont = state.withLockUnchecked { s -> CheckedContinuation<UIImage?, Never>? in
            defer { s.cont = nil }
            return s.cont
        }
        cont?.resume(returning: image)
    }

    /// Marks the request cancelled, resumes the task with nil, and returns the id to cancel.
    func cancel() -> PHImageRequestID? {
        let (id, cont) = state.withLockUnchecked { s -> (PHImageRequestID?, CheckedContinuation<UIImage?, Never>?) in
            s.cancelled = true
            defer { s.cont = nil }
            return (s.id, s.cont)
        }
        cont?.resume(returning: nil)
        return id
    }
}
