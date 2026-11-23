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
        guard let nextDay = Calendar.app.date(byAdding: .day, value: 1, to: trip.end) else { return [] }
        let end = Calendar.app.startOfDay(for: nextDay)
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
    private var accessWaiters: [UUID: CheckedContinuation<Bool, Never>] = [:]
    private var spotWaiters: [UUID: CheckedContinuation<(lat: Double, lon: Double)?, Never>] = [:]
    private var accessTimeoutTask: Task<Void, Never>?
    private var spotTimeoutTask: Task<Void, Never>?

    override private init() {
        super.init()
        manager.delegate = self
        // City level is all the app needs.
        manager.desiredAccuracy = kCLLocationAccuracyReduced
    }

    var onAuthorizationChange: (@MainActor (CLAuthorizationStatus) -> Void)?

    var authorizationStatus: CLAuthorizationStatus { manager.authorizationStatus }

    var isDenied: Bool {
        let s = manager.authorizationStatus
        return s == .denied || s == .restricted
    }

    var allowed: Bool {
        let s = manager.authorizationStatus
        return s == .authorizedWhenInUse || s == .authorizedAlways
    }

    func requestAccess(timeout: Duration = .seconds(30)) async -> Bool {
        guard manager.authorizationStatus == .notDetermined else { return allowed }
        guard !Task.isCancelled else { return false }
        let token = UUID()
        return await withTaskCancellationHandler {
            await withCheckedContinuation { c in
                if Task.isCancelled {
                    c.resume(returning: false)
                    return
                }
                accessWaiters[token] = c
                if accessWaiters.count == 1 {
                    manager.requestWhenInUseAuthorization()
                    accessTimeoutTask?.cancel()
                    // Checks now and then in case the answer came without a callback.
                    // While the prompt is still up, keep waiting: people can take their time.
                    accessTimeoutTask = Task { @MainActor [weak self] in
                        while !Task.isCancelled {
                            try? await Task.sleep(for: timeout)
                            guard !Task.isCancelled, let self else { return }
                            if self.manager.authorizationStatus != .notDetermined {
                                self.finishAccess(self.allowed)
                                return
                            }
                        }
                    }
                }
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                self?.cancelAccessWaiter(token)
            }
        }
    }

    /// Where the phone is now, roughly. Nil without permission or when it can't tell.
    func current(timeout: Duration = .seconds(10)) async -> (lat: Double, lon: Double)? {
        guard allowed, !Task.isCancelled else { return nil }
        let token = UUID()
        return await withTaskCancellationHandler {
            await withCheckedContinuation { c in
                if Task.isCancelled {
                    c.resume(returning: nil)
                    return
                }
                spotWaiters[token] = c
                if spotWaiters.count == 1 {
                    manager.requestLocation()
                    spotTimeoutTask?.cancel()
                    spotTimeoutTask = Task { @MainActor [weak self] in
                        try? await Task.sleep(for: timeout)
                        guard !Task.isCancelled else { return }
                        self?.finishSpot(nil)
                    }
                }
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                self?.cancelSpotWaiter(token)
            }
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            onAuthorizationChange?(status)
            guard status != .notDetermined else { return }
            let ok = status == .authorizedWhenInUse || status == .authorizedAlways
            finishAccess(ok)
            if !ok { finishSpot(nil) }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let valid = locations.last {
            $0.horizontalAccuracy >= 0 && CLLocationCoordinate2DIsValid($0.coordinate)
        }
        let spot = valid.map { (lat: $0.coordinate.latitude, lon: $0.coordinate.longitude) }
        Task { @MainActor in finishSpot(spot) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in finishSpot(nil) }
    }

    private func cancelAccessWaiter(_ token: UUID) {
        if let c = accessWaiters.removeValue(forKey: token) {
            c.resume(returning: false)
            if accessWaiters.isEmpty {
                accessTimeoutTask?.cancel()
                accessTimeoutTask = nil
            }
        }
    }

    private func cancelSpotWaiter(_ token: UUID) {
        if let c = spotWaiters.removeValue(forKey: token) {
            c.resume(returning: nil)
            if spotWaiters.isEmpty {
                spotTimeoutTask?.cancel()
                spotTimeoutTask = nil
            }
        }
    }

    private func finishAccess(_ ok: Bool) {
        accessTimeoutTask?.cancel()
        accessTimeoutTask = nil
        let waiting = accessWaiters.values
        accessWaiters = [:]
        waiting.forEach { $0.resume(returning: ok) }
    }

    private func finishSpot(_ spot: (lat: Double, lon: Double)?) {
        spotTimeoutTask?.cancel()
        spotTimeoutTask = nil
        let waiting = spotWaiters.values
        spotWaiters = [:]
        waiting.forEach { $0.resume(returning: spot) }
    }
}
