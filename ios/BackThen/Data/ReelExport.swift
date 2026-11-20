import AVFoundation
import UIKit

/// Writes a reel to an MP4 on the phone, frame by frame. Nothing leaves the phone.
enum ReelExport {
    enum Failure: Error { case writer, frame }

    /// Dedicated serial queue for CoreGraphics rendering and AVAssetWriter appends so the
    /// Swift Concurrency cooperative thread pool is never starved.
    private static let encodeQueue = DispatchQueue(label: "com.njarrar.backthen.reelexport", qos: .userInitiated)
    private static let rootDirectory = FileManager.default.temporaryDirectory.appending(path: "reels")

    /// Long side of the photos loaded for the video, the whole photo inside it. Enough to
    /// fill a 1080 by 1920 frame with the slow zoom.
    static let photoSide: CGFloat = 2400

    /// Removes finished export folders older than `age` (default 1 hour) without interrupting
    /// active Share Sheet / AirDrop transfers from recent exports.
    static func pruneStaleExports(olderThan age: TimeInterval = 3600) {
        let fm = FileManager.default
        let cutoff = Date().addingTimeInterval(-age)
        let items = (try? fm.contentsOfDirectory(
            at: rootDirectory,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles]
        )) ?? []
        for item in items {
            let mod = (try? item.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            if mod < cutoff {
                try? fm.removeItem(at: item)
            }
        }
    }

    /// Makes the video and returns its file. `progress` gets 0 to 1. Cancelling the task stops it
    /// and removes the half-made file. `load` gets a full-size photo by its place in the reel.
    static func make(
        renderer: ReelRenderer,
        name: String,
        load: @escaping @Sendable (Int) async -> UIImage?,
        progress: @escaping @Sendable (Double) -> Void
    ) async throws -> URL {
        let fm = FileManager.default
        try fm.createDirectory(at: rootDirectory, withIntermediateDirectories: true)
        pruneStaleExports()
        let runDir = rootDirectory.appending(path: UUID().uuidString)
        try fm.createDirectory(at: runDir, withIntermediateDirectories: true)
        let url = runDir.appending(path: "\(name).mp4")
        do {
            try await write(renderer: renderer, to: url, load: load, progress: progress)
            return url
        } catch {
            try? fm.removeItem(at: runDir)
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
        let bitrate = max(3_500_000, Int((Double(w * h) / Double(1080 * 1920)) * 9_000_000.0))
        let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
        writer.shouldOptimizeForNetworkUse = true
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: w,
            AVVideoHeightKey: h,
            AVVideoColorPropertiesKey: [
                AVVideoColorPrimariesKey: AVVideoColorPrimaries_ITU_R_709_2,
                AVVideoTransferFunctionKey: AVVideoTransferFunction_ITU_R_709_2,
                AVVideoYCbCrMatrixKey: AVVideoYCbCrMatrix_ITU_R_709_2,
            ],
            AVVideoCompressionPropertiesKey: [
                AVVideoAverageBitRateKey: bitrate,
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

        var finished = false
        defer {
            if !finished {
                input.markAsFinished()
                writer.cancelWriting()
            }
        }

        // Full-size photos, loaded once per appearance and dropped once the reel moves past them.
        var full: [Int: UIImage] = [:]
        var attempted = Set<Int>()
        renderer.image = { full[$0] }
        defer { renderer.image = { _ in nil } }

        let plan = renderer.plan
        let frames = plan.frameCount
        for f in 0..<frames {
            if Task.isCancelled { throw CancellationError() }
            let t = Double(f) / Double(ReelPlan.fps)
            let m = plan.moment(at: t)
            var needed = Set<Int>()
            for item in [m.a, m.b].compactMap({ $0 }) {
                if case .photo(let i) = item.kind { needed.insert(i) }
            }
            for i in needed where !attempted.contains(i) {
                attempted.insert(i)
                full[i] = await load(i)
            }
            for key in full.keys where !needed.contains(key) { full[key] = nil }
            attempted.formIntersection(needed)

            while !input.isReadyForMoreMediaData {
                if Task.isCancelled { throw CancellationError() }
                guard writer.status == .writing else { throw writer.error ?? Failure.writer }
                try await Task.sleep(for: .milliseconds(4))
            }
            let presentationTime = CMTime(value: CMTimeValue(f), timescale: CMTimeScale(ReelPlan.fps))
            let appendError: Error? = await withCheckedContinuation { cont in
                encodeQueue.async {
                    let err: Error? = autoreleasepool {
                        guard writer.status == .writing else { return writer.error ?? Failure.writer }
                        guard let buffer = frame(renderer, adaptor: adaptor, time: t, width: w, height: h) else {
                            return Failure.frame
                        }
                        guard adaptor.append(buffer, withPresentationTime: presentationTime) else {
                            return writer.error ?? Failure.frame
                        }
                        return nil
                    }
                    cont.resume(returning: err)
                }
            }
            if let appendError { throw appendError }
            progress(Double(f + 1) / Double(frames))
        }
        input.markAsFinished()
        await writer.finishWriting()
        finished = true
        if writer.status != .completed { throw writer.error ?? Failure.writer }
    }

    private static func frame(_ renderer: ReelRenderer, adaptor: AVAssetWriterInputPixelBufferAdaptor, time: Double, width w: Int, height h: Int) -> CVPixelBuffer? {
        var made: CVPixelBuffer?
        if let pool = adaptor.pixelBufferPool {
            CVPixelBufferPoolCreatePixelBuffer(nil, pool, &made)
        } else {
            CVPixelBufferCreate(nil, w, h, kCVPixelFormatType_32BGRA, nil, &made)
        }
        guard let buffer = made else { return nil }
        CVPixelBufferLockBaseAddress(buffer, [])
        defer { CVPixelBufferUnlockBaseAddress(buffer, []) }
        let colorSpace = CGColorSpace(name: CGColorSpace.sRGB) ?? CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: CVPixelBufferGetBaseAddress(buffer), width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: CVPixelBufferGetBytesPerRow(buffer), space: colorSpace,
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

    /// "Flashback 2026-11-03 14.30.05": a plain name that sorts by date where the file lands.
    static func fileName(now: Date = Date()) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.calendar = .app
        f.dateFormat = "yyyy-MM-dd HH.mm.ss"
        return "Flashback \(f.string(from: now))"
    }
}
