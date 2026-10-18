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
}
