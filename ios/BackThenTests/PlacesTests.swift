import XCTest
@testable import Back_Then

final class PlacesTests: XCTestCase {
    private let cal: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Europe/London")!
        return c
    }()

    private let cities = CityList(tsv: """
    London\tGB\t51.508\t-0.126\t8961989
    Croydon\tGB\t51.376\t-0.098\t173314
    Paris\tFR\t48.853\t2.349\t2138551
    """)

    func testBigCityNextDoorWins() {
        XCTAssertEqual(cities.cities.count, 3)
        // In Croydon, 15 km from central London: still London, as people say it.
        XCTAssertEqual(cities.city(lat: 51.37, lon: -0.10), 0)
        XCTAssertEqual(cities.city(lat: 48.86, lon: 2.35), 2)
        // Far from every city, or no location at all.
        XCTAssertNil(cities.city(lat: 45.0, lon: -30.0))
        XCTAssertNil(cities.city(lat: 0, lon: 0))
    }

    func testDaysGroupIntoTripsNewestFirst() {
        let t = trips(["2019-05-10": 3, "2019-05-12": 2, "2019-05-20": 1, "2023-06-01": 4], calendar: cal)
        XCTAssertEqual(t.map(\.days), [1, 1, 2])
        XCTAssertEqual(t.map(\.photos), [4, 1, 5])
        XCTAssertEqual(dayKey(t[2].start, calendar: cal), "2019-05-10")
        XCTAssertEqual(dayKey(t[2].end, calendar: cal), "2019-05-12")
    }

    func testCardOnlyAwayFromHomeAndForPastTrips() {
        let today = cal.date(from: DateComponents(year: 2026, month: 10, day: 18))!
        let index: PlaceIndex = [
            0: ["2026-09-01": 5, "2026-09-10": 2, "2026-10-01": 3],
            2: ["2019-05-10": 3, "2023-06-01": 4, "2026-10-17": 6],
        ]
        XCTAssertEqual(homeCity(index, today: today, calendar: cal), 0)
        XCTAssertNil(makePlaceCard(index, here: 0, cities: cities, today: today, calendar: cal))
        XCTAssertNil(makePlaceCard(index, here: 1, cities: cities, today: today, calendar: cal))
        let card = makePlaceCard(index, here: 2, cities: cities, today: today, calendar: cal)
        XCTAssertEqual(card?.city.name, "Paris")
        // Yesterday's photos are this trip, not one before.
        XCTAssertEqual(card?.trips.count, 2)
        XCTAssertEqual(card?.photos, 7)
    }

    func testNewPhotosAddToThePlaceIndex() {
        let day = cal.date(from: DateComponents(year: 2023, month: 6, day: 1, hour: 12))!
        let start: PlaceIndex = [2: ["2023-06-01": 1]]
        let grown = addingToPlaceIndex(start, [
            PhotoSpot(time: day, lat: 48.86, lon: 2.35),
            PhotoSpot(time: day, lat: 51.50, lon: -0.12),
            // Far from every city: not counted.
            PhotoSpot(time: day, lat: 45.0, lon: -30.0),
        ], cities: cities, calendar: cal)
        XCTAssertEqual(grown, [2: ["2023-06-01": 2], 0: ["2023-06-01": 1]])
    }

    func testAntimeridianDistanceAndLookup() {
        let pacific = CityList(tsv: """
        Taveuni\tFJ\t-16.800\t179.900\t50000
        """)
        XCTAssertLessThan(distanceKm(-16.800, 179.900, -16.800, -179.900), 25)
        XCTAssertEqual(pacific.city(lat: -16.800, lon: -179.900), 0)
    }

    func testHighLatitudeCityLookup() {
        let arctic = CityList(tsv: """
        Norilsk\tRU\t69.353\t88.203\t175365
        """)
        // ~35 km east of Norilsk is nearly 1 degree of longitude at 69.35° N.
        XCTAssertEqual(arctic.city(lat: 69.353, lon: 89.100), 0)
    }

    func testInvalidCoordinatesReturnNil() {
        XCTAssertNil(cities.city(lat: .nan, lon: 0))
        XCTAssertNil(cities.city(lat: 51.5, lon: .infinity))
        XCTAssertNil(cities.city(lat: 95.0, lon: 0))
        XCTAssertNil(cities.city(lat: 51.5, lon: -181.0))
    }

    func testCustomHomeOverrideAndNegativeHere() {
        let today = cal.date(from: DateComponents(year: 2026, month: 10, day: 18))!
        let index: PlaceIndex = [
            0: ["2024-05-01": 5, "2026-09-01": 5, "2026-09-10": 2, "2026-10-01": 3],
            2: ["2019-05-10": 3, "2023-06-01": 4],
        ]
        // Overriding home to Paris (id 2) hides Paris and lets London (id 0) appear as a place card.
        XCTAssertNil(makePlaceCard(index, here: 2, cities: cities, customHome: 2, today: today, calendar: cal))
        XCTAssertEqual(makePlaceCard(index, here: 0, cities: cities, customHome: 2, today: today, calendar: cal)?.city.name, "London")
        XCTAssertNil(makePlaceCard(index, here: -1, cities: cities, today: today, calendar: cal))
    }

    func testCitiesHashDetectsTSVChanges() {
        let a = CityList(tsv: "London\tGB\t51.508\t-0.126\t8961989\n")
        let b = CityList(tsv: "London\tGB\t51.508\t-0.126\t8961989\n")
        let c = CityList(tsv: "Paris\tFR\t48.853\t2.349\t2138551\n")
        XCTAssertEqual(a.citiesHash, b.citiesHash)
        XCTAssertNotEqual(a.citiesHash, c.citiesHash)
    }
}
