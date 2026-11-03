import AVFoundation
import UIKit

/// Writes a reel to an MP4 on the phone, frame by frame. Nothing leaves the phone.
enum ReelExport {
    enum Failure: Error { case writer, frame }

    /// Long side of the photos loaded for the video. Enough for a 1080 wide frame with the slow zoom.
    static let photoSide: CGFloat = 1600

    /// Makes the video and returns its file. `progress` gets 0 to 1. Cancelling the task stops it
    /// and removes the half-made file. `load` gets a full-size photo by its place in the reel.
    static func make(
        renderer: ReelRenderer,
        name: String,
        load: @escaping @Sendable (Int) async -> UIImage?,
        progress: @escaping @Sendable (Double) -> Void
    ) async throws -> URL {
        let dir = FileManager.default.temporaryDirectory.appending(path: "reels")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        // One reel at a time on disk: old ones went out through the share sheet already.
        for old in (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? [] {
            try? FileManager.default.removeItem(at: old)
        }
        let url = dir.appending(path: "\(name).mp4")
        do {
            try await write(renderer: renderer, to: url, load: load, progress: progress)
            return url
        } catch {
            try? FileManager.default.removeItem(at: url)
            throw error
        }
    }

    private static func write(
        renderer: ReelRenderer,
        to url: URL,
        load: @escaping @Sendable (Int) async -> UIImage?,
        progress: @escaping @Sendable (Double) -> Void
    ) async throws {
        let (w, h) = renderer.options.size.pixels
        let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: w,
            AVVideoHeightKey: h,
            AVVideoCompressionPropertiesKey: [
                AVVideoAverageBitRateKey: 9_000_000,
                AVVideoProfileLevelKey: AVVideoProfileLevelH264HighAutoLevel,
                AVVideoExpectedSourceFrameRateKey: ReelPlan.fps,
                AVVideoMaxKeyFrameIntervalKey: ReelPlan.fps,
            ],
        ])
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: w,
            kCVPixelBufferHeightKey as String: h,
        ])
        guard writer.canAdd(input) else { throw Failure.writer }
        writer.add(input)
        guard writer.startWriting() else { throw writer.error ?? Failure.writer }
        writer.startSession(atSourceTime: .zero)

        // Full-size photos, loaded as the reel reaches them and dropped once it has passed.
        var full: [Int: UIImage] = [:]
        renderer.image = { full[$0] }
        defer { renderer.image = { _ in nil } }

        let plan = renderer.plan
        let frames = plan.frameCount
        for f in 0..<frames {
            if Task.isCancelled {
                input.markAsFinished()
                writer.cancelWriting()
                throw CancellationError()
            }
            let t = Double(f) / Double(ReelPlan.fps)
            let m = plan.moment(at: t)
            var needed = Set<Int>()
            for item in [m.a, m.b].compactMap({ $0 }) {
                if case .photo(let i) = item.kind { needed.insert(i) }
            }
            for i in needed where full[i] == nil { full[i] = await load(i) }
            for key in full.keys where !needed.contains(key) { full[key] = nil }

            while !input.isReadyForMoreMediaData {
                try await Task.sleep(for: .milliseconds(4))
            }
            guard let buffer = try frame(renderer, adaptor: adaptor, time: t, width: w, height: h) else { throw Failure.frame }
            guard adaptor.append(buffer, withPresentationTime: CMTime(value: CMTimeValue(f), timescale: CMTimeScale(ReelPlan.fps))) else {
                throw writer.error ?? Failure.frame
            }
            progress(Double(f + 1) / Double(frames))
        }
        input.markAsFinished()
        await writer.finishWriting()
        if writer.status != .completed { throw writer.error ?? Failure.writer }
    }

    private static func frame(_ renderer: ReelRenderer, adaptor: AVAssetWriterInputPixelBufferAdaptor, time: Double, width w: Int, height h: Int) throws -> CVPixelBuffer? {
        var made: CVPixelBuffer?
        if let pool = adaptor.pixelBufferPool {
            CVPixelBufferPoolCreatePixelBuffer(nil, pool, &made)
        } else {
            CVPixelBufferCreate(nil, w, h, kCVPixelFormatType_32BGRA, nil, &made)
        }
        guard let buffer = made else { return nil }
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        guard let ctx = CGContext(
            data: CVPixelBufferGetBaseAddress(buffer), width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: CVPixelBufferGetBytesPerRow(buffer), space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue
        ) else { return nil }
        // UIKit drawing expects the origin at the top left.
        ctx.translateBy(x: 0, y: CGFloat(h))
        ctx.scaleBy(x: 1, y: -1)
        UIGraphicsPushContext(ctx)
        renderer.draw(in: ctx, size: CGSize(width: w, height: h), time: time)
        UIGraphicsPopContext()
        return buffer
    }

    /// "Flashback 2026-11-03": a plain name that sorts by date where the file lands.
    static func fileName(now: Date = Date()) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.calendar = .app
        f.dateFormat = "yyyy-MM-dd HH.mm"
        return "Flashback \(f.string(from: now))"
    }
}
