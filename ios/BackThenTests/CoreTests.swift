import XCTest
import ImageIO
import UIKit
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
        XCTAssertEqual(agoLabel(2026, thisYear: 2026), "Earlier this year")
        XCTAssertEqual(agoLabel(2028, thisYear: 2026), "Earlier this year")
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
        let near = nearbyDays(index, MonthDay(month: 9, day: 5), today: today, calendar: cal)
        XCTAssertEqual(near.before, MonthDay(month: 9, day: 2))
        XCTAssertEqual(near.after, MonthDay(month: 9, day: 9))
    }

    func testSwipeSkipsEmptyDaysAndWraps() {
        let back = swipeTarget(index, from: MonthDay(month: 9, day: 2), step: -1, today: today, calendar: cal)
        XCTAssertEqual(back?.day, MonthDay(month: 8, day: 20))
        XCTAssertEqual(back?.skipped, 11)
        let forward = swipeTarget(index, from: MonthDay(month: 8, day: 20), step: 1, today: today, calendar: cal)
        XCTAssertEqual(forward?.day, MonthDay(month: 9, day: 2))
        // Days after today with past-year photos are reachable, matching the unlocked calendar.
        let futurePastYear = swipeTarget(index, from: MonthDay(month: 9, day: 2), step: 1, today: today, calendar: cal)
        XCTAssertEqual(futurePastYear?.day, MonthDay(month: 9, day: 9))
        XCTAssertEqual(futurePastYear?.skipped, 6)
        // Wraps across Dec 31 <-> Jan 1.
        let yearEndIndex = buildDayIndex([at(2024, 11, 30), at(2025, 0, 2)], calendar: cal)
        let wrapForward = swipeTarget(yearEndIndex, from: MonthDay(month: 11, day: 30), step: 1, today: today, calendar: cal)
        XCTAssertEqual(wrapForward?.day, MonthDay(month: 0, day: 2))
        XCTAssertEqual(wrapForward?.skipped, 2)
        let wrapBack = swipeTarget(yearEndIndex, from: MonthDay(month: 0, day: 2), step: -1, today: today, calendar: cal)
        XCTAssertEqual(wrapBack?.day, MonthDay(month: 11, day: 30))
        XCTAssertEqual(wrapBack?.skipped, 2)
        // Non-leap year (2026) does not count nonexistent Feb 29 as a skipped empty day.
        let febIndex = buildDayIndex([at(2025, 1, 28), at(2025, 2, 1)], calendar: cal)
        let acrossFeb = swipeTarget(febIndex, from: MonthDay(month: 2, day: 1), step: -1, today: today, calendar: cal)
        XCTAssertEqual(acrossFeb?.day, MonthDay(month: 1, day: 28))
        XCTAssertEqual(acrossFeb?.skipped, 0)
        // Single-day index returns nil instead of wrapping onto itself.
        let single = buildDayIndex([at(2024, 9, 2)], calendar: cal)
        XCTAssertNil(swipeTarget(single, from: MonthDay(month: 9, day: 2), step: 1, today: today, calendar: cal))
        XCTAssertNil(swipeTarget(single, from: MonthDay(month: 9, day: 2), step: -1, today: today, calendar: cal))
    }

    func testPhotoCountAndFavoriteCover() {
        XCTAssertEqual(photoCount(index, MonthDay(month: 9, day: 2), today: today, calendar: cal), 3)
        let fav = Photo(key: "f", source: .sample, ref: "f", width: 1, height: 1,
                        time: at(2020, 9, 2, 12))
        let md = MonthDay(month: 9, day: 2)
        XCTAssertEqual(favoriteCover([fav], md, years: [2021, 2020], calendar: cal)?.key, "f")
        // Not a year the day counts, or another day: no favorite.
        XCTAssertNil(favoriteCover([fav], md, years: [2021], calendar: cal))
        XCTAssertNil(favoriteCover([fav], MonthDay(month: 9, day: 3), years: [2020], calendar: cal))
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

    func testTopBarProgressMovesInSteps() {
        // At rest the title's bottom sits above the bar's: nothing has scrolled.
        XCTAssertEqual(TodayView.progress(bar: 100, title: 100), 0)
        // Fully under the bar.
        XCTAssertEqual(TodayView.progress(bar: 100, title: 20), 1)
        // Half way. A small move inside one step gives the same value, so no redraw.
        XCTAssertEqual(TodayView.progress(bar: 100, title: 80), 0.5)
        XCTAssertEqual(TodayView.progress(bar: 100, title: 79.9), 0.5)
        XCTAssertEqual(TodayView.progress(bar: 100, title: 80.2), 0.5)
    }

    func testShareKeepsJPEGAndTurnsOtherTypesIntoJPEG() throws {
        let dir = FileManager.default.temporaryDirectory.appending(path: "share-test-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let picture = UIGraphicsImageRenderer(size: CGSize(width: 40, height: 30)).image { ctx in
            UIColor.red.setFill(); ctx.fill(CGRect(x: 0, y: 0, width: 40, height: 30))
        }
        let jpeg = try XCTUnwrap(picture.jpegData(compressionQuality: 0.9))
        let kept = try XCTUnwrap(Share.shareFile(jpeg, type: "public.jpeg", name: dir.appending(path: "a")))
        XCTAssertEqual(kept.pathExtension, "jpg")
        XCTAssertEqual(try Data(contentsOf: kept), jpeg)

        // A TIFF stands in for a RAW file: it goes out as a JPEG.
        let tiff = NSMutableData()
        let dest = try XCTUnwrap(CGImageDestinationCreateWithData(tiff, "public.tiff" as CFString, 1, nil))
        CGImageDestinationAddImage(dest, try XCTUnwrap(picture.cgImage), nil)
        XCTAssertTrue(CGImageDestinationFinalize(dest))
        let made = try XCTUnwrap(Share.shareFile(tiff as Data, type: "public.tiff", name: dir.appending(path: "b")))
        XCTAssertEqual(made.pathExtension, "jpg")
        let back = try XCTUnwrap(UIImage(contentsOfFile: made.path))
        XCTAssertEqual(back.size.width * back.scale, picture.size.width * picture.scale)
    }

    func testPhotosLoadOnlyNearTheScreen() {
        let screen = CGRect(x: 0, y: 1000, width: 400, height: 800)
        XCTAssertTrue(isNear(CGRect(x: 0, y: 1200, width: 100, height: 100), screen))
        // Within one screen below or above.
        XCTAssertTrue(isNear(CGRect(x: 0, y: 2500, width: 100, height: 100), screen))
        XCTAssertTrue(isNear(CGRect(x: 0, y: 300, width: 100, height: 100), screen))
        // Farther away.
        XCTAssertFalse(isNear(CGRect(x: 0, y: 2700, width: 100, height: 100), screen))
        XCTAssertFalse(isNear(CGRect(x: 0, y: 0, width: 100, height: 100), screen))
    }

    func testNewPhotosAddToTheDayIndex() {
        let a = cal.date(from: DateComponents(year: 2019, month: 10, day: 2, hour: 9))!
        let b = cal.date(from: DateComponents(year: 2021, month: 10, day: 2, hour: 9))!
        let start = buildDayIndex([a], calendar: cal)
        let grown = addingToDayIndex(start, [a, b], calendar: cal)
        XCTAssertEqual(grown, buildDayIndex([a, a, b], calendar: cal))
        XCTAssertEqual(addingToDayIndex(start, [], calendar: cal), start)
    }

    func testLossySkipsNullPrimitivesAndBadObjectsWithoutTruncating() throws {
        let photo = { (key: String) in
            #"{"key":"\#(key)","source":"device","ref":"r","width":4,"height":3,"time":0}"#
        }
        let json = #"{"favorites":[null,42,"str",[1,2],{"bad":true},\#(photo("d:1")),null,\#(photo("d:2"))]}"#
        let state = try JSONDecoder().decode(SavedState.self, from: Data(json.utf8))
        XCTAssertEqual(state.favorites.map(\.key), ["d:1", "d:2"])
    }

    func testSamplePhotosFileURLSanitizesTraversal() {
        let url = SamplePhotos.fileURL("../../state.json")
        XCTAssertEqual(url.deletingLastPathComponent().standardizedFileURL, SamplePhotos.folder.standardizedFileURL)
        XCTAssertEqual(url.lastPathComponent, "state.json")
    }

    func testDayRangeHandlesMidnightDSTWithoutOverlap() throws {
        var cairo = Calendar(identifier: .gregorian)
        cairo.timeZone = try XCTUnwrap(TimeZone(identifier: "Africa/Cairo"))
        // Egypt springs forward at midnight on the last Friday of April (2026-04-24).
        let (start, end) = try XCTUnwrap(dayRange(year: 2026, MonthDay(month: 3, day: 24), calendar: cairo))
        let nextStart = try XCTUnwrap(dayRange(year: 2026, MonthDay(month: 3, day: 25), calendar: cairo)).0
        XCTAssertEqual(end, nextStart)
        XCTAssertLessThan(start, end)
    }

    func testShareFileResetsTIFFOrientationAndDimensions() throws {
        let dir = FileManager.default.temporaryDirectory.appending(path: "share-orient-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let picture = UIGraphicsImageRenderer(size: CGSize(width: 40, height: 30), format: format).image { ctx in
            UIColor.blue.setFill(); ctx.fill(CGRect(x: 0, y: 0, width: 40, height: 30))
        }
        let tiff = NSMutableData()
        let dest = try XCTUnwrap(CGImageDestinationCreateWithData(tiff, "public.tiff" as CFString, 1, nil))
        let tiffProps: [CFString: Any] = [
            kCGImagePropertyOrientation: 6,
            kCGImagePropertyTIFFDictionary: [kCGImagePropertyTIFFOrientation: 6],
        ]
        CGImageDestinationAddImage(dest, try XCTUnwrap(picture.cgImage), tiffProps as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(dest))
        let outURL = try XCTUnwrap(Share.shareFile(tiff as Data, type: "public.tiff", name: dir.appending(path: "rot")))
        let outSrc = try XCTUnwrap(CGImageSourceCreateWithURL(outURL as CFURL, nil))
        let outProps = try XCTUnwrap(CGImageSourceCopyPropertiesAtIndex(outSrc, 0, nil) as? [CFString: Any])
        XCTAssertEqual(outProps[kCGImagePropertyOrientation] as? Int, 1)
        if let outTIFF = outProps[kCGImagePropertyTIFFDictionary] as? [CFString: Any],
           let tiffOrient = outTIFF[kCGImagePropertyTIFFOrientation] as? Int {
            XCTAssertEqual(tiffOrient, 1)
        }
        let decoded = try XCTUnwrap(UIImage(contentsOfFile: outURL.path))
        XCTAssertEqual(Int(decoded.size.width * decoded.scale), 30)
        XCTAssertEqual(Int(decoded.size.height * decoded.scale), 40)
    }

    func testRemindersNextFireDatesAvoidsSpringForwardDuplicate() throws {
        var ny = Calendar(identifier: .gregorian)
        ny.timeZone = try XCTUnwrap(TimeZone(identifier: "America/New_York"))
        // 2026-03-08 springs forward from 02:00 to 03:00 in America/New_York.
        let now = try XCTUnwrap(ny.date(from: DateComponents(year: 2026, month: 3, day: 7, hour: 12)))
        let fire = Reminders.nextFireDates(from: now, daysAhead: 5, hour: 2, minute: 30, calendar: ny)
        let days = fire.map { dayKey($0.date, calendar: ny) }
        XCTAssertEqual(days.count, Set(days).count)
        XCTAssertTrue(days.contains("2026-03-08"))
        XCTAssertTrue(days.contains("2026-03-09"))
    }

    func testMasonryAndMonthDayDefensiveBounds() {
        XCTAssertEqual(masonryColumns([1, 2], columns: 0), [])
        XCTAssertEqual(masonryColumns([0, -1, .nan], columns: 2).flatMap { $0 }.sorted(), [0, 1, 2])
        let shifted = MonthDay(month: 15, day: 1).shifted(by: 0)
        XCTAssertTrue((0..<12).contains(shifted.month))
    }

    @MainActor
    func testAppStoreAlbumsUndoAndCorruptBackup() async throws {
        let dir = FileManager.default.temporaryDirectory.appending(path: "store-test-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let file = dir.appending(path: "state.json")
        try Data("not-valid-json{".utf8).write(to: file)

        let store = AppStore(storageURL: file, persist: false)
        let files = try FileManager.default.contentsOfDirectory(atPath: dir.path)
        XCTAssertTrue(files.contains { $0.hasPrefix("state.corrupt-") && $0.hasSuffix(".json") })

        let p1 = Photo(key: "s:1", source: .sample, ref: "1.jpg", width: 100, height: 100, time: at(2020, 9, 2))
        let p2 = Photo(key: "s:2", source: .sample, ref: "2.jpg", width: 100, height: 100, time: at(2020, 9, 2))
        let album = store.createAlbum("  Autumn  ", photos: [p1, p1])
        XCTAssertEqual(album.name, "Autumn")
        XCTAssertEqual(album.photos.count, 1)
        let savesAfterCreate = store.albumSaves

        // Adding only duplicates returns 0 and does not increment albumSaves.
        XCTAssertEqual(store.add([p1], to: album.id), 0)
        XCTAssertEqual(store.albumSaves, savesAfterCreate)
        XCTAssertEqual(store.toastKind, .info)

        XCTAssertEqual(store.add([p1, p2], to: album.id), 1)
        XCTAssertEqual(store.albumSaves, savesAfterCreate + 1)

        store.removeFromAlbum(album.id, photoKey: p1.key)
        XCTAssertEqual(store.saved.albums.first?.photos.map(\.key), [p2.key])

        // Hide day with undo toast, then trigger undo.
        let md = MonthDay(month: 9, day: 2)
        store.setHidden(md, true)
        XCTAssertTrue(store.isHidden(md))
        XCTAssertNotNil(store.toastUndoLabel)
        store.triggerToastUndo()
        XCTAssertFalse(store.isHidden(md))
        XCTAssertNil(store.toast)

        // Custom home city override persists in SavedState.
        store.setHomeCity(42)
        XCTAssertEqual(store.saved.customHomeCity, 42)
        store.setHomeCity(nil)
        XCTAssertNil(store.saved.customHomeCity)
    }
}
