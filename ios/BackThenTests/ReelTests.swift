import AVFoundation
import UIKit
import XCTest
@testable import Back_Then

final class ReelTests: XCTestCase {
    private func photo(_ year: Int, _ hour: Int = 12) -> Photo {
        let date = Calendar.app.date(from: DateComponents(year: year, month: 11, day: 3, hour: hour))!
        return Photo(key: "s:\(year)-\(hour)", source: .sample, ref: "\(year).jpg", width: 1200, height: 900, time: date)
    }

    // MARK: Timing

    func testPlanAddsIntroPhotosAndEndCard() {
        let plan = ReelPlan(count: 3, options: ReelOptions())
        XCTAssertEqual(plan.items.map(\.kind), [.intro, .photo(0), .photo(1), .photo(2), .outro])
        // 2 + 3 × 2.8 + 2, less four moves of 0.65 each.
        XCTAssertEqual(plan.total, 9.8, accuracy: 0.0001)
        XCTAssertEqual(plan.frameCount, 294)
    }

    func testPlanWithoutEndCardEndsOnLastPhoto() {
        var o = ReelOptions()
        o.endCard = false
        let plan = ReelPlan(count: 2, options: o)
        XCTAssertEqual(plan.items.last?.kind, .photo(1))
        XCTAssertEqual(plan.moment(at: plan.total).a.kind, .photo(1))
        XCTAssertNil(plan.moment(at: plan.total).b)
    }

    func testMomentsCoverTheWholeReel() {
        let plan = ReelPlan(count: 4, options: ReelOptions())
        XCTAssertEqual(plan.moment(at: 0).a.kind, .intro)
        XCTAssertNil(plan.moment(at: 0).b)
        // Halfway through the first move.
        let move = plan.items[1].start + plan.overlap / 2
        let m = plan.moment(at: move)
        XCTAssertEqual(m.a.kind, .intro)
        XCTAssertEqual(m.b?.kind, .photo(0))
        XCTAssertEqual(m.progress, 0.5, accuracy: 0.0001)
        XCTAssertEqual(plan.moment(at: plan.total).a.kind, .outro)
        // Every frame lands on a card, with progress in range.
        for f in 0...plan.frameCount {
            let m = plan.moment(at: Double(f) / Double(ReelPlan.fps))
            XCTAssert((0...1).contains(m.progress))
            if let b = m.b { XCTAssertLessThan(m.a.start, b.start) }
        }
    }

    func testSpeedsAndMoveLengths() {
        var o = ReelOptions()
        o.speed = .slow
        o.move = .flash
        let slow = ReelPlan(count: 1, options: o)
        XCTAssertEqual(slow.overlap, 0.45)
        XCTAssertEqual(slow.items[1].length, 3.8)
        o.move = .slide
        XCTAssertEqual(ReelPlan(count: 1, options: o).overlap, 0.5)
    }

    func testReduceMotionTurnsFlashIntoFade() {
        var o = ReelOptions()
        o.move = .flash
        XCTAssertEqual(ReelPlan(count: 2, options: o, reduceMotion: true).move, .fade)
        XCTAssertEqual(ReelPlan(count: 2, options: o, reduceMotion: false).move, .flash)
    }

    func testPlanCapsPhotos() {
        let plan = ReelPlan(count: 50, options: ReelOptions())
        XCTAssertEqual(plan.items.count, ReelPlan.maxPhotos + 2)
    }

    func testOrderNewestOrOldestFirstAndCapped() {
        let photos = [photo(2019), photo(2025), photo(2015), photo(2025, 9)]
        XCTAssertEqual(reelOrder(photos, newestFirst: true).map(\.key), ["s:2025-12", "s:2025-9", "s:2019-12", "s:2015-12"])
        XCTAssertEqual(reelOrder(photos, newestFirst: false).map(\.key), ["s:2015-12", "s:2019-12", "s:2025-9", "s:2025-12"])
        let many = (1980..<2025).map { photo($0) }
        XCTAssertEqual(reelOrder(many, newestFirst: true).count, ReelPlan.maxPhotos)
        XCTAssertEqual(reelOrder(many, newestFirst: true).first?.key, "s:2024-12")
    }

    func testFilmStampLooksLikeACamera() {
        XCTAssertEqual(filmStamp(photo(2015).time), "'15 11 3")
        XCTAssertEqual(filmStamp(photo(2005).time), "'05 11 3")
    }

    func testSizes() {
        XCTAssertTrue(ReelSize.story.pixels == (1080, 1920))
        XCTAssertTrue(ReelSize.portrait.pixels == (1080, 1350))
        XCTAssertTrue(ReelSize.square.pixels == (1080, 1080))
    }

    func testOptionsSaveAndLoad() throws {
        var o = ReelOptions()
        o.look = .film
        o.size = .square
        o.endCard = false
        let back = try JSONDecoder().decode(ReelOptions.self, from: JSONEncoder().encode(o))
        XCTAssertEqual(back, o)
    }

    // MARK: Drawing and video

    /// A test picture: a color wash with a white bar, so frames can't come out flat.
    private func picture(_ hue: CGFloat, _ size: CGSize) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            UIColor(hue: hue, saturation: 0.7, brightness: 0.8, alpha: 1).setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            UIColor.white.setFill()
            ctx.fill(CGRect(x: 0, y: size.height * 0.4, width: size.width, height: size.height * 0.2))
        }
    }

    func testEveryLookAndMoveDrawsWithoutTrouble() {
        let photos = [photo(2025), photo(2021), photo(2016)]
        let small = photos.indices.map { picture(CGFloat($0) / 3, CGSize(width: 320, height: 240)) as UIImage? }
        for look in ReelLook.allCases {
            for move in ReelMove.allCases {
                for fill in [false, true] {
                    var o = ReelOptions()
                    o.look = look
                    o.move = move
                    o.fill = fill
                    let r = ReelRenderer(photos: photos, options: o, small: small, reduceMotion: false)
                    let size = CGSize(width: 108, height: 192)
                    let format = UIGraphicsImageRendererFormat()
                    format.scale = 1
                    let image = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
                        for t in stride(from: 0.0, through: r.plan.total, by: 0.25) {
                            r.draw(in: ctx.cgContext, size: size, time: t)
                        }
                    }
                    XCTAssertEqual(image.size, size)
                }
            }
        }
    }

    func testRendererWordsFollowTheLanguage() {
        let r = ReelRenderer(photos: [photo(2025), photo(2015)], options: ReelOptions(), small: [nil, nil], reduceMotion: false,
                             now: Calendar.app.date(from: DateComponents(year: 2026, month: 11, day: 3))!)
        XCTAssertEqual(r.cards.map(\.year), [L.num(2025), L.num(2015)])
        XCTAssertEqual(r.cards[1].ago, L.p("ago_years", 11))
        XCTAssertEqual(r.cards[1].film, "'15 11 3")
        XCTAssertEqual(r.introTitle, L.date(photo(2015).time, "MMMMd"))
    }

    /// Makes a real MP4 and reads it back.
    private func export(_ o: ReelOptions, count: Int) async throws -> (URL, ReelPlan) {
        let photos = (0..<count).map { photo(2025 - $0 * 3) }
        let small = photos.indices.map { picture(CGFloat($0) / CGFloat(count), CGSize(width: 320, height: 240)) as UIImage? }
        let r = ReelRenderer(photos: photos, options: o, small: small, reduceMotion: false)
        let progress = ProgressBox()
        let url = try await ReelExport.make(
            renderer: r, name: "test-\(o.size.rawValue)",
            load: { i in self.picture(CGFloat(i) / CGFloat(count), CGSize(width: 1600, height: 1200)) },
            progress: { progress.last = $0 }
        )
        XCTAssertEqual(progress.last, 1, accuracy: 0.0001)
        return (url, r.plan)
    }

    func testExportWritesAPlayableStoryVideo() async throws {
        var o = ReelOptions()
        o.look = .film
        o.speed = .fast
        let (url, plan) = try await export(o, count: 3)
        let asset = AVURLAsset(url: url)
        let duration = try await asset.load(.duration).seconds
        XCTAssertEqual(duration, plan.total, accuracy: 0.1)
        let tracks = try await asset.loadTracks(withMediaType: .video)
        let track = try XCTUnwrap(tracks.first)
        let size = try await track.load(.naturalSize)
        XCTAssertEqual(size, CGSize(width: 1080, height: 1920))
        // A frame from the middle of the first photo decodes and isn't one flat color.
        let gen = AVAssetImageGenerator(asset: asset)
        gen.requestedTimeToleranceBefore = .zero
        gen.requestedTimeToleranceAfter = .zero
        let at = CMTime(seconds: plan.items[1].start + 1, preferredTimescale: 600)
        let (cg, _) = try await gen.image(at: at)
        XCTAssertEqual(cg.width, 1080)
        XCTAssertGreaterThan(spread(cg), 40)
        try? FileManager.default.removeItem(at: url)
    }

    func testExportWritesSquareFillVideoWithoutEndCard() async throws {
        var o = ReelOptions()
        o.size = .square
        o.fill = true
        o.move = .slide
        o.endCard = false
        o.speed = .fast
        let (url, plan) = try await export(o, count: 2)
        let asset = AVURLAsset(url: url)
        let tracks = try await asset.loadTracks(withMediaType: .video)
        let track = try XCTUnwrap(tracks.first)
        let size = try await track.load(.naturalSize)
        XCTAssertEqual(size, CGSize(width: 1080, height: 1080))
        let duration = try await asset.load(.duration).seconds
        XCTAssertEqual(duration, plan.total, accuracy: 0.1)
        try? FileManager.default.removeItem(at: url)
    }

    func testCancelledExportLeavesNoFile() async throws {
        let photos = (0..<5).map { photo(2025 - $0) }
        let r = ReelRenderer(photos: photos, options: ReelOptions(), small: photos.map { _ in nil }, reduceMotion: false)
        let task = Task {
            try await ReelExport.make(renderer: r, name: "cancelled", load: { _ in nil }, progress: { _ in })
        }
        task.cancel()
        do {
            _ = try await task.value
            XCTFail("A cancelled export should throw")
        } catch {}
        let file = FileManager.default.temporaryDirectory.appending(path: "reels/cancelled.mp4")
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
    }

    /// How far apart the darkest and brightest of a few sample points are, 0 to 255.
    private func spread(_ cg: CGImage) -> Int {
        let w = 64, h = 64
        var px = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = CGContext(data: &px, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        var lo = 255, hi = 0
        for i in stride(from: 0, to: px.count, by: 4) {
            let v = (Int(px[i]) + Int(px[i + 1]) + Int(px[i + 2])) / 3
            lo = min(lo, v)
            hi = max(hi, v)
        }
        return hi - lo
    }
}

private final class ProgressBox: @unchecked Sendable {
    var last = 0.0
}
