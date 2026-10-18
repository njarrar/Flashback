import XCTest
@testable import Back_Then

final class CoreTests: XCTestCase {
    private var cal: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "America/New_York")!
        return c
    }()

    /// `m` is 0-based, like MonthDay.
    private func at(_ y: Int, _ m: Int, _ d: Int, _ h: Int = 12) -> Date {
        cal.date(from: DateComponents(year: y, month: m + 1, day: d, hour: h))!
    }

    private lazy var today = at(2026, 9, 2)
    private lazy var index = buildDayIndex(
        [at(2014, 9, 2), at(2019, 9, 2), at(2019, 9, 2, 15), at(2026, 9, 2, 8), at(2025, 9, 9), at(2026, 8, 20)],
        calendar: cal
    )

    func testShiftWrapsAcrossMonthsAndYears() {
        XCTAssertEqual(MonthDay(month: 0, day: 1).shifted(by: -1), MonthDay(month: 11, day: 31))
        XCTAssertEqual(MonthDay(month: 11, day: 31).shifted(by: 1), MonthDay(month: 0, day: 1))
        XCTAssertEqual(MonthDay(month: 1, day: 28).shifted(by: 1), MonthDay(month: 1, day: 29))
        XCTAssertEqual(MonthDay(month: 1, day: 29).shifted(by: 1), MonthDay(month: 2, day: 1))
    }

    func testMonthDayKeysRoundTrip() {
        XCTAssertEqual(MonthDay(key: MonthDay(month: 9, day: 2).key), MonthDay(month: 9, day: 2))
        XCTAssertEqual(MonthDay(key: "1-29"), MonthDay(month: 1, day: 29))
        XCTAssertNil(MonthDay(key: "1-30"))
        XCTAssertNil(MonthDay(key: "x"))
    }

    func testLabels() throws {
        let saved = (L.lang, L.locale)
        defer { L.use(saved.0, locale: saved.1) }
        L.use(try XCTUnwrap(L.load("en")), locale: Locale(identifier: "en_US"))
        XCTAssertEqual(agoLabel(2025, thisYear: 2026), "1 year ago")
        XCTAssertEqual(agoLabel(2016, thisYear: 2026), "10 years ago")
        XCTAssertEqual(L.p("photos", 1), "1 photo")
        XCTAssertEqual(L.p("photos", 3), "3 photos")
        XCTAssertTrue(formatTime(hour: 18, minute: 30).contains("30"))
    }

    func testIndexCountsPerDayAndYear() {
        XCTAssertEqual(index["9-2"], [2014: 1, 2019: 2, 2026: 1])
    }

    func testYearsSkipTodayInThisYear() {
        XCTAssertEqual(yearsForDay(index, MonthDay(month: 9, day: 2), today: today, calendar: cal), [2019, 2014])
        XCTAssertEqual(yearsForDay(index, MonthDay(month: 8, day: 20), today: today, calendar: cal), [2026])
    }

    func testNearbyDaysFound() {
        let near = nearbyDays(index, MonthDay(month: 9, day: 5), today: today)
        XCTAssertEqual(near.before, MonthDay(month: 9, day: 2))
        XCTAssertEqual(near.after, MonthDay(month: 9, day: 9))
    }

    func testPhotoCountAndFavoriteCover() {
        XCTAssertEqual(photoCount(index, MonthDay(month: 9, day: 2), today: today), 3)
        let cal = Calendar.app
        let fav = Photo(key: "f", source: .sample, ref: "f", width: 1, height: 1,
                        time: cal.date(from: DateComponents(year: 2020, month: 10, day: 2, hour: 12))!)
        let md = MonthDay(month: 9, day: 2)
        XCTAssertEqual(favoriteCover([fav], md, years: [2021, 2020])?.key, "f")
        // Not a year the day counts, or another day: no favorite.
        XCTAssertNil(favoriteCover([fav], md, years: [2021]))
        XCTAssertNil(favoriteCover([fav], MonthDay(month: 9, day: 3), years: [2020]))
    }

    func testLaterDaysComeAfter() {
        XCTAssertTrue(MonthDay(month: 9, day: 3).isAfter(MonthDay(month: 9, day: 2)))
        XCTAssertTrue(MonthDay(month: 10, day: 1).isAfter(MonthDay(month: 9, day: 31)))
        XCTAssertFalse(MonthDay(month: 9, day: 2).isAfter(MonthDay(month: 9, day: 2)))
        XCTAssertFalse(MonthDay(month: 0, day: 31).isAfter(MonthDay(month: 9, day: 2)))
    }

    func testMosaicPacksWithoutGaps() {
        for n in [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 25] {
            let layout = layoutMosaic(n)
            XCTAssertEqual(layout.tiles.count, n)
            var grid = Array(repeating: Array(repeating: 0, count: mosaicColumns), count: layout.rows)
            for t in layout.tiles {
                XCTAssertLessThanOrEqual(t.col + t.cols, mosaicColumns)
                for r in t.row..<(t.row + t.rows) { for c in t.col..<(t.col + t.cols) { grid[r][c] += 1 } }
            }
            XCTAssertTrue(grid.allSatisfy { $0.allSatisfy { $0 == 1 } }, "gap or overlap for \(n)")
        }
    }

    func testMasonryBalancesColumns() {
        // A tall photo first, so the next three fit beside it.
        XCTAssertEqual(masonryColumns([0.6, 1.5, 1.5, 1]), [[0], [1, 2, 3]])
        XCTAssertEqual(masonryColumns([]), [[], []])
        XCTAssertEqual(tileAspect(width: 0, height: 0), 1)
        XCTAssertEqual(tileAspect(width: 4000, height: 1000), 1.8)
    }

    func testHiddenDaysLeaveTheIndex() {
        var s = SavedState()
        s.sources.device = true
        s.deviceIndex = ["9-2": [2019: 3], "9-3": [2020: 1]]
        s.hiddenDates = ["9-2"]
        XCTAssertEqual(Set(s.visibleIndex.keys), ["9-3"])
    }

    func testOldSaveFilesStillLoad() throws {
        let old = #"{"onboarded":true,"sources":{"device":true,"google":false},"favorites":[]}"#
        let s = try JSONDecoder().decode(SavedState.self, from: Data(old.utf8))
        XCTAssertTrue(s.onboarded)
        XCTAssertTrue(s.sources.device)
        XCTAssertEqual(s.layout, .mosaic)
        XCTAssertTrue(s.hiddenDates.isEmpty)
        XCTAssertNil(s.librarySignature)
    }

    func testLibrarySignatureIsSavedWithTheIndex() throws {
        var s = SavedState()
        s.deviceIndex = ["9-2": [2019: 3]]
        s.librarySignature = .init(count: 42, firstID: "a", lastID: "b", lastMod: Date(timeIntervalSince1970: 1_700_000_000))
        let back = try JSONDecoder().decode(SavedState.self, from: JSONEncoder().encode(s))
        XCTAssertEqual(back.librarySignature, s.librarySignature)
    }

    func testOldGooglePhotosAreDropped() throws {
        let photo = { (key: String, source: String) in
            #"{"key":"\#(key)","source":"\#(source)","ref":"x","width":4,"height":3,"time":0}"#
        }
        let old = #"{"favorites":[\#(photo("d:1", "device")),\#(photo("g:1", "google"))],"#
            + #""albums":[{"id":"a","name":"Trip","createdAt":0,"photos":[\#(photo("g:2", "google")),\#(photo("d:2", "device"))]}]}"#
        let s = try JSONDecoder().decode(SavedState.self, from: Data(old.utf8))
        XCTAssertEqual(s.favorites.map(\.key), ["d:1"])
        XCTAssertEqual(s.albums.first?.photos.map(\.key), ["d:2"])
    }
}
