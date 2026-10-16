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

    func testDayLevelsFollowPhotoCount() {
        XCTAssertEqual(photoCount(index, MonthDay(month: 9, day: 2), today: today), 3)
        // 9-2 has 3 photos and 8-20 has 1; 9-9 comes after today, and empty days are left out.
        XCTAssertEqual(dayLevels(index, today: today), ["9-2": 3, "8-20": 1])
        // Eight days with 1 to 8 photos: the shade grows with the count.
        var spread: DayIndex = [:]
        for k in 1...8 { spread["0-\(k)"] = [2020: k] }
        let levels = dayLevels(spread, today: today)
        XCTAssertEqual((1...8).map { levels["0-\($0)"] }, [1, 2, 3, 3, 4, 4, 4, 5])
        // One huge day doesn't wash out the rest: days with 1 photo stay at the lightest shade.
        var outlier: DayIndex = ["0-10": [2020: 1000]]
        for k in 1...9 { outlier["0-\(k)"] = [2020: 1] }
        let o = dayLevels(outlier, today: today)
        XCTAssertEqual(Set(o.filter { $0.key != "0-10" }.values), [1])
        XCTAssertEqual(o["0-10"], 5)
        // On a log scale: 1, 4, 10, 40 and 100 photos each get their own shade when 100 is the top.
        XCTAssertEqual([1, 4, 10, 40, 100, 5000].map { levelFor($0, top: 100) }, [1, 2, 3, 4, 5, 5])
    }

    func testLaterDaysComeAfter() {
        XCTAssertTrue(MonthDay(month: 9, day: 3).isAfter(MonthDay(month: 9, day: 2)))
        XCTAssertTrue(MonthDay(month: 10, day: 1).isAfter(MonthDay(month: 9, day: 31)))
        XCTAssertFalse(MonthDay(month: 9, day: 2).isAfter(MonthDay(month: 9, day: 2)))
        XCTAssertFalse(MonthDay(month: 0, day: 31).isAfter(MonthDay(month: 9, day: 2)))
    }

    func testMerges() {
        XCTAssertEqual(mergeIndexes(["1-1": [2020: 1]], ["1-1": [2020: 2, 2021: 1]]), ["1-1": [2020: 3, 2021: 1]])
    }

    func testDropsGoogleCopies() {
        func p(_ key: String, _ t: TimeInterval, _ w: Int = 4, _ h: Int = 3) -> Photo {
            Photo(key: key, source: key.hasPrefix("d") ? .device : .google, ref: key, width: w, height: h, time: Date(timeIntervalSince1970: t))
        }
        let out = dropDuplicates(device: [p("d:1", 1)], google: [p("g:1", 1.5), p("g:2", 9), p("g:3", 1.2, 3, 4)])
        XCTAssertEqual(out.map(\.key), ["d:1", "g:2", "g:3"])
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
    }

    func testTokenOnlyGoesToGoogle() {
        XCTAssertTrue(GooglePhotos.isGoogleHTTPS(URL(string: "https://photospicker.googleapis.com/v1/sessions")!))
        XCTAssertTrue(GooglePhotos.isGoogleHTTPS(URL(string: "https://lh3.googleusercontent.com/abc=w2048-h2048")!))
        XCTAssertFalse(GooglePhotos.isGoogleHTTPS(URL(string: "http://lh3.googleusercontent.com/abc")!))
        XCTAssertFalse(GooglePhotos.isGoogleHTTPS(URL(string: "https://googleusercontent.com.evil.com/x")!))
    }
}
