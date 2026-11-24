import Foundation

// Places: which city each photo was taken in, from a list of cities built into the app
// (places/cities.tsv). Nothing about where you are or were leaves the phone.

/// Photo days per city: city row in cities.tsv -> "yyyy-MM-dd" -> photo count.
typealias PlaceIndex = [Int: [String: Int]]

struct City: Hashable, Sendable {
    let id: Int
    let name: String
    let country: String
    let lat: Double
    let lon: Double
    let population: Int
}

/// The cities in places/cities.tsv (50,000 people or more), with a grid of one-degree
/// cells so finding the city for a spot looks at a few dozen cities, not thousands.
final class CityList: @unchecked Sendable {
    let cities: [City]
    /// Deterministic checksum of the TSV content, stored with `placeIndex` so TSV updates trigger a clean rebuild.
    let citiesHash: String
    private let grid: [Int: [Int]]

    /// Photos farther than this from every city have no city.
    static let reachKm = 40.0

    init(tsv: String) {
        self.citiesHash = Self.checksum(tsv)
        var cities: [City] = []
        var grid: [Int: [Int]] = [:]
        for line in tsv.split(separator: "\n") {
            let f = line.split(separator: "\t", omittingEmptySubsequences: false)
            guard f.count >= 5,
                  let lat = Double(f[2]), let lon = Double(f[3]), let pop = Int(f[4]),
                  lat.isFinite, lon.isFinite, (-90...90).contains(lat), (-180...180).contains(lon) else { continue }
            let city = City(id: cities.count, name: String(f[0]), country: String(f[1]), lat: lat, lon: lon, population: pop)
            grid[Self.cell(lat, lon), default: []].append(city.id)
            cities.append(city)
        }
        self.cities = cities
        self.grid = grid
    }

    static func checksum(_ text: String) -> String {
        var hash: UInt64 = 14_695_981_039_346_656_037
        for byte in text.utf8 {
            hash ^= UInt64(byte)
            hash &*= 1_099_511_628_211
        }
        return String(format: "%016llx", hash)
    }

    /// The app has its city list. Only looks for the file, so views can ask it while drawing.
    static var available: Bool { Bundle.main.url(forResource: "cities", withExtension: "tsv") != nil }

    /// The city list, read once, off the main thread: it has about 12,000 lines.
    static func load() async -> CityList? {
        await Task.detached(priority: .utility) { CityList.shared }.value
    }

    static let shared: CityList? = {
        guard let url = Bundle.main.url(forResource: "cities", withExtension: "tsv"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return nil }
        return CityList(tsv: text)
    }()

    private static func latIndex(_ lat: Double) -> Int {
        min(max(Int((lat + 90).rounded(.down)), 0), 180)
    }

    private static func lonIndex(_ lon: Double) -> Int {
        ((Int((lon + 180).rounded(.down)) % 360) + 360) % 360
    }

    private static func cellKey(_ latIdx: Int, _ lonIdx: Int) -> Int {
        latIdx * 360 + (((lonIdx % 360) + 360) % 360)
    }

    private static func cell(_ lat: Double, _ lon: Double) -> Int {
        cellKey(latIndex(lat), lonIndex(lon))
    }

    /// The city a spot belongs to: among cities within reach, big and near wins, so a photo
    /// in a suburb counts for the big city next door, as people say it.
    func city(lat: Double, lon: Double) -> Int? {
        guard lat.isFinite, lon.isFinite, (-90...90).contains(lat), (-180...180).contains(lon) else { return nil }
        if lat == 0 && lon == 0 { return nil }
        let baseLat = Self.latIndex(lat)
        let baseLon = Self.lonIndex(lon)
        let cosLat = cos(min(89.9, abs(lat) + 1.0) * .pi / 180)
        let kmPerLonDeg = 111.32 * max(cosLat, 0.0001)
        let lonRadius = min(180, max(1, Int(ceil(Self.reachKm / kmPerLonDeg))))
        let lonOffsets: [Int] = lonRadius >= 180 ? Array(0..<360) : Array(-lonRadius...lonRadius)
        var best: (id: Int, score: Double)?
        for dLat in -1...1 {
            let rLat = baseLat + dLat
            guard (0...180).contains(rLat) else { continue }
            for dLon in lonOffsets {
                let rLon = lonRadius >= 180 ? dLon : baseLon + dLon
                for id in grid[Self.cellKey(rLat, rLon)] ?? [] {
                    let c = cities[id]
                    let d = distanceKm(lat, lon, c.lat, c.lon)
                    guard d <= Self.reachKm else { continue }
                    let score = Double(c.population) / ((d + 5) * (d + 5))
                    if best == nil || score > best!.score { best = (id, score) }
                }
            }
        }
        return best?.id
    }
}

/// A photo's time and where it was taken.
struct PhotoSpot: Sendable {
    let time: Date
    let lat: Double
    let lon: Double
}

/// The index with these photos counted too: for new photos, without reading the whole library.
func addingToPlaceIndex(_ start: PlaceIndex, _ spots: [PhotoSpot], cities: CityList, calendar: Calendar = .app) -> PlaceIndex {
    var out = start
    for s in spots {
        guard let city = cities.city(lat: s.lat, lon: s.lon) else { continue }
        out[city, default: [:]][dayKey(s.time, calendar: calendar), default: 0] += 1
    }
    return out
}

/// Close enough for distances under a few hundred kilometers, wrapping across the ±180° antimeridian.
func distanceKm(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
    var dLon = (lon2 - lon1).truncatingRemainder(dividingBy: 360)
    if dLon > 180 { dLon -= 360 }
    if dLon < -180 { dLon += 360 }
    let x = dLon * cos((lat1 + lat2) / 2 * .pi / 180) * 111.32
    let y = (lat2 - lat1) * 110.57
    return (x * x + y * y).squareRoot()
}

/// "2019-05-12" for a photo's local day.
func dayKey(_ date: Date, calendar: Calendar = .app) -> String {
    let c = calendar.dateComponents([.year, .month, .day], from: date)
    return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
}

func dateOf(dayKey key: String, calendar: Calendar = .app) -> Date? {
    let p = key.split(separator: "-").compactMap { Int($0) }
    guard p.count == 3 else { return nil }
    return calendar.date(from: DateComponents(year: p[0], month: p[1], day: p[2]))
}

/// One visit to a city: days with photos there, with no more than two days between them.
struct Trip: Equatable, Hashable, Sendable {
    /// Start of the first and last day.
    let start: Date
    let end: Date
    let days: Int
    let photos: Int
}

private let utcGregorianCalendar: Calendar = {
    var c = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(secondsFromGMT: 0) ?? .gmt
    return c
}()

private func utcDayStamp(_ key: String) -> Date? {
    let p = key.split(separator: "-").compactMap { Int($0) }
    guard p.count == 3 else { return nil }
    return utcGregorianCalendar.date(from: DateComponents(year: p[0], month: p[1], day: p[2]))
}

/// A city's visits, newest first.
func trips(_ days: [String: Int], calendar: Calendar = .app) -> [Trip] {
    let sorted = days.compactMap { k, n -> (local: Date, utc: Date, count: Int)? in
        guard let local = dateOf(dayKey: k, calendar: calendar),
              let utc = utcDayStamp(k) else { return nil }
        return (local, utc, n)
    }.sorted { $0.utc < $1.utc }
    var out: [Trip] = []
    var start: Date?, last: Date?, lastUTC: Date?, count = 0, photos = 0
    for (day, utc, n) in sorted {
        // Compute day gaps in UTC Gregorian so midnight DST transitions never skew the 3-day window.
        if let lUTC = lastUTC, let gap = utcGregorianCalendar.dateComponents([.day], from: lUTC, to: utc).day, gap <= 3 {
            count += 1
            photos += n
        } else {
            if let s = start, let l = last { out.append(Trip(start: s, end: l, days: count, photos: photos)) }
            start = day
            count = 1
            photos = n
        }
        last = day
        lastUTC = utc
    }
    if let s = start, let l = last { out.append(Trip(start: s, end: l, days: count, photos: photos)) }
    return out.reversed()
}

/// Home: the city with the most photo days in the two years before today, or ever.
func homeCity(_ index: PlaceIndex, today: Date = Date(), calendar: Calendar = .app) -> Int? {
    let nonEmpty = index.filter { !$0.value.isEmpty }
    guard !nonEmpty.isEmpty else { return nil }
    let since = dayKey(calendar.date(byAdding: .year, value: -2, to: today) ?? today, calendar: calendar)
    let recent = nonEmpty.mapValues { days in days.keys.filter { $0 >= since }.count }
    let pick = recent.values.contains { $0 > 0 } ? recent : nonEmpty.mapValues(\.count)
    return pick.max { a, b in a.value < b.value || (a.value == b.value && a.key > b.key) }?.key
}

/// Visits to a city that ended at least a week ago, newest first. A trip still going on
/// isn't "before".
func pastTrips(_ days: [String: Int], today: Date = Date(), calendar: Calendar = .app) -> [Trip] {
    guard let cutoff = calendar.date(byAdding: .day, value: -7, to: calendar.startOfDay(for: today)) else { return [] }
    return trips(days, calendar: calendar).filter { $0.end < cutoff }
}

/// What Today shows when you're in a city you've been to before, away from home.
struct PlaceCard: Equatable, Sendable {
    let city: City
    let trips: [Trip]
    var photos: Int { trips.reduce(0) { $0 + $1.photos } }
}

func makePlaceCard(
    _ index: PlaceIndex,
    here: Int?,
    cities: CityList,
    customHome: Int? = nil,
    today: Date = Date(),
    calendar: Calendar = .app
) -> PlaceCard? {
    let home = customHome ?? homeCity(index, today: today, calendar: calendar)
    guard let here, cities.cities.indices.contains(here), here != home, let days = index[here] else { return nil }
    let past = pastTrips(days, today: today, calendar: calendar)
    guard !past.isEmpty else { return nil }
    return PlaceCard(city: cities.cities[here], trips: past)
}
