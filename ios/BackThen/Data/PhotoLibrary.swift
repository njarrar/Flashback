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

    /// Capture times of every photo the app can see.
    static func allTimes() async -> [Date] {
        await Task.detached(priority: .utility) {
            let result = PHAsset.fetchAssets(with: .image, options: imageOptions())
            var times: [Date] = []
            times.reserveCapacity(result.count)
            result.enumerateObjects { asset, _, _ in
                if let d = asset.creationDate { times.append(d) }
            }
            return times
        }.value
    }

    /// Photos taken on one calendar day of one year, oldest first.
    static func photos(year: Int, on md: MonthDay) async -> [Photo] {
        guard let (start, end) = dayRange(year: year, md) else { return [] }
        return await Task.detached(priority: .userInitiated) {
            let o = imageOptions()
            o.predicate = NSPredicate(format: "creationDate >= %@ AND creationDate < %@", start as NSDate, end as NSDate)
            o.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
            let result = PHAsset.fetchAssets(with: .image, options: o)
            var out: [Photo] = []
            result.enumerateObjects { a, _, _ in
                out.append(Photo(key: "d:\(a.localIdentifier)", source: .device, ref: a.localIdentifier,
                                 width: a.pixelWidth, height: a.pixelHeight, time: a.creationDate ?? start))
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
        let key = ImageCache.key(id, size)
        if let hit = ImageCache.get(key) { return hit }
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject else { return nil }
        let o = PHImageRequestOptions()
        o.isNetworkAccessAllowed = true
        o.deliveryMode = .highQualityFormat
        o.resizeMode = .fast
        let image = await withCheckedContinuation { cont in
            imageManager.requestImage(for: asset, targetSize: size, contentMode: .aspectFill, options: o) { image, _ in
                cont.resume(returning: image)
            }
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

/// Decoded thumbnails kept in memory, so scrolling back up doesn't load them again.
/// Capped at about 50 MB; iOS also empties it when memory runs low.
enum ImageCache {
    private static let cache: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.countLimit = 150
        c.totalCostLimit = 50 * 1024 * 1024
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
