import CoreLocation
import Photos

/// Reads where photos were taken, and roughly where the phone is now.
enum PlaceLibrary {
    /// Photo days per city for the whole library, built off the main thread.
    static func index(_ cities: CityList) async -> PlaceIndex {
        await Task.detached(priority: .utility) {
            let o = PHFetchOptions()
            o.includeAssetSourceTypes = [.typeUserLibrary, .typeCloudShared, .typeiTunesSynced]
            let result = PHAsset.fetchAssets(with: .image, options: o)
            var out: PlaceIndex = [:]
            result.enumerateObjects { a, _, _ in
                guard let at = a.location?.coordinate, let time = a.creationDate,
                      let city = cities.city(lat: at.latitude, lon: at.longitude) else { return }
                out[city, default: [:]][dayKey(time), default: 0] += 1
            }
            return out
        }.value
    }

    /// The photos from one visit to a city, oldest first.
    static func photos(city: Int, trip: Trip, cities: CityList) async -> [Photo] {
        guard let end = Calendar.app.date(byAdding: .day, value: 1, to: trip.end) else { return [] }
        return await Task.detached(priority: .userInitiated) {
            let o = PHFetchOptions()
            o.includeAssetSourceTypes = [.typeUserLibrary, .typeCloudShared, .typeiTunesSynced]
            o.predicate = NSPredicate(format: "creationDate >= %@ AND creationDate < %@", trip.start as NSDate, end as NSDate)
            o.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: true)]
            var out: [Photo] = []
            PHAsset.fetchAssets(with: .image, options: o).enumerateObjects { a, _, _ in
                guard let at = a.location?.coordinate, cities.city(lat: at.latitude, lon: at.longitude) == city else { return }
                out.append(Photo(key: "d:\(a.localIdentifier)", source: .device, ref: a.localIdentifier,
                                 width: a.pixelWidth, height: a.pixelHeight, time: a.creationDate ?? trip.start))
            }
            return out
        }.value
    }
}

/// Asks for the phone's rough location, only while the app is open.
@MainActor
final class Locator: NSObject, CLLocationManagerDelegate {
    static let shared = Locator()
    private let manager = CLLocationManager()
    private var accessWaiters: [CheckedContinuation<Bool, Never>] = []
    private var spotWaiters: [CheckedContinuation<(lat: Double, lon: Double)?, Never>] = []

    override private init() {
        super.init()
        manager.delegate = self
        // City level is all the app needs.
        manager.desiredAccuracy = kCLLocationAccuracyReduced
    }

    var allowed: Bool {
        let s = manager.authorizationStatus
        return s == .authorizedWhenInUse || s == .authorizedAlways
    }

    func requestAccess() async -> Bool {
        guard manager.authorizationStatus == .notDetermined else { return allowed }
        return await withCheckedContinuation { c in
            accessWaiters.append(c)
            manager.requestWhenInUseAuthorization()
        }
    }

    /// Where the phone is now, roughly. Nil without permission or when it can't tell.
    func current() async -> (lat: Double, lon: Double)? {
        guard allowed else { return nil }
        return await withCheckedContinuation { c in
            spotWaiters.append(c)
            if spotWaiters.count == 1 { manager.requestLocation() }
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        guard status != .notDetermined else { return }
        Task { @MainActor in
            let ok = status == .authorizedWhenInUse || status == .authorizedAlways
            let waiting = accessWaiters
            accessWaiters = []
            waiting.forEach { $0.resume(returning: ok) }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let spot = locations.last.map { (lat: $0.coordinate.latitude, lon: $0.coordinate.longitude) }
        Task { @MainActor in finish(spot) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in finish(nil) }
    }

    private func finish(_ spot: (lat: Double, lon: Double)?) {
        let waiting = spotWaiters
        spotWaiters = []
        waiting.forEach { $0.resume(returning: spot) }
    }
}
