import Foundation

// Places: which city each photo was taken in, from a list of cities built into the app
// (places/cities.tsv). Nothing about where you are or were leaves the phone.

/// Photo days per city: city row in cities.tsv -> "yyyy-MM-dd" -> photo count.
typealias PlaceIndex = [Int: [String: Int]]

struct City: Hashable {
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
    private let grid: [Int: [Int]]

    /// Photos farther than this from every city have no city.
    static let reachKm = 40.0

    init(tsv: String) {
        var cities: [City] = []
        var grid: [Int: [Int]] = [:]
        for line in tsv.split(separator: "\n") {
            let f = line.split(separator: "\t", omittingEmptySubsequences: false)
            guard f.count >= 5, let lat = Double(f[2]), let lon = Double(f[3]), let pop = Int(f[4]) else { continue }
            let city = City(id: cities.count, name: String(f[0]), country: String(f[1]), lat: lat, lon: lon, population: pop)
            grid[Self.cell(lat, lon), default: []].append(city.id)
            cities.append(city)
        }
        self.cities = cities
        self.grid = grid
    }

    static let shared: CityList? = {
        guard let url = Bundle.main.url(forResource: "cities", withExtension: "tsv"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return nil }
        return CityList(tsv: text)
    }()

    private static func cell(_ lat: Double, _ lon: Double) -> Int {
        Int((lat + 90).rounded(.down)) * 361 + Int((lon + 180).rounded(.down))
    }

    /// The city a spot belongs to: among cities within reach, big and near wins, so a photo
    /// in a suburb counts for the big city next door, as people say it.
    func city(lat: Double, lon: Double) -> Int? {
        if lat == 0 && lon == 0 { return nil }
        var best: (id: Int, score: Double)?
        for dLat in -1...1 {
            for dLon in -1...1 {
                for id in grid[Self.cell(lat + Double(dLat), lon + Double(dLon))] ?? [] {
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

/// Close enough for distances under a few hundred kilometers.
func distanceKm(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
    let x = (lon2 - lon1) * cos((lat1 + lat2) / 2 * .pi / 180) * 111.32
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
struct Trip: Equatable, Hashable {
    /// Start of the first and last day.
    let start: Date
    let end: Date
    let days: Int
    let photos: Int
}

/// A city's visits, newest first.
func trips(_ days: [String: Int], calendar: Calendar = .app) -> [Trip] {
    let sorted = days.compactMap { k, n in dateOf(dayKey: k, calendar: calendar).map { ($0, n) } }.sorted { $0.0 < $1.0 }
    var out: [Trip] = []
    var start: Date?, last: Date?, count = 0, photos = 0
    for (day, n) in sorted {
        if let l = last, let gap = calendar.dateComponents([.day], from: l, to: day).day, gap <= 3 {
            count += 1
            photos += n
        } else {
            if let s = start, let l = last { out.append(Trip(start: s, end: l, days: count, photos: photos)) }
            start = day
            count = 1
            photos = n
        }
        last = day
    }
    if let s = start, let l = last { out.append(Trip(start: s, end: l, days: count, photos: photos)) }
    return out.reversed()
}

/// Home: the city with the most photo days in the two years before today, or ever.
func homeCity(_ index: PlaceIndex, today: Date = Date(), calendar: Calendar = .app) -> Int? {
    let since = dayKey(calendar.date(byAdding: .year, value: -2, to: today) ?? today, calendar: calendar)
    let recent = index.mapValues { days in days.keys.filter { $0 >= since }.count }
    let pick = recent.values.contains { $0 > 0 } ? recent : index.mapValues(\.count)
    return pick.max { a, b in a.value < b.value || (a.value == b.value && a.key > b.key) }?.key
}

/// Visits to a city that ended at least a week ago, newest first. A trip still going on
/// isn't "before".
func pastTrips(_ days: [String: Int], today: Date = Date(), calendar: Calendar = .app) -> [Trip] {
    guard let cutoff = calendar.date(byAdding: .day, value: -7, to: calendar.startOfDay(for: today)) else { return [] }
    return trips(days, calendar: calendar).filter { $0.end < cutoff }
}

/// What Today shows when you're in a city you've been to before, away from home.
struct PlaceCard: Equatable {
    let city: City
    let trips: [Trip]
    var photos: Int { trips.reduce(0) { $0 + $1.photos } }
}

func makePlaceCard(_ index: PlaceIndex, here: Int?, cities: CityList, today: Date = Date(), calendar: Calendar = .app) -> PlaceCard? {
    guard let here, here != homeCity(index, today: today, calendar: calendar), let days = index[here] else { return nil }
    let past = pastTrips(days, today: today, calendar: calendar)
    guard !past.isEmpty, here < cities.cities.count else { return nil }
    return PlaceCard(city: cities.cities[here], trips: past)
}
