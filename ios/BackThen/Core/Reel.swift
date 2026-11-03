import Foundation

// A reel: a short video of picked photos, each stamped with its date, with a move between them.
// This file holds the timing and choices only. ReelRenderer.swift draws the frames and
// ReelExport.swift writes them to an MP4, all on the phone.

/// How each photo shows its date.
enum ReelLook: String, CaseIterable, Codable {
    /// The year in large type, with the date and how long ago.
    case year
    /// An orange date in the corner, like old film cameras printed.
    case film
    /// A small dark pill with the date, like the app's photo tiles.
    case clean
}

/// The move from one photo to the next.
enum ReelMove: String, CaseIterable, Codable {
    case fade, slide, zoom, flash
}

enum ReelSpeed: String, CaseIterable, Codable {
    case fast, normal, slow

    /// Seconds each photo stays on screen, counting the moves in and out.
    var seconds: Double {
        switch self {
        case .fast: 2.0
        case .normal: 2.8
        case .slow: 3.8
        }
    }
}

enum ReelSize: String, CaseIterable, Codable {
    /// 9:16, for Reels, Stories and TikTok.
    case story
    /// 4:5, for feed posts.
    case portrait
    /// 1:1.
    case square

    var pixels: (width: Int, height: Int) {
        switch self {
        case .story: (1080, 1920)
        case .portrait: (1080, 1350)
        case .square: (1080, 1080)
        }
    }

    var label: String {
        switch self {
        case .story: "9:16"
        case .portrait: "4:5"
        case .square: "1:1"
        }
    }
}

struct ReelOptions: Equatable, Codable {
    var look: ReelLook = .year
    var move: ReelMove = .fade
    var newestFirst = true
    var speed: ReelSpeed = .normal
    var size: ReelSize = .story
    /// Fill the frame with the photo, cropping it, instead of showing the whole photo.
    var fill = false
    /// A last card with the app icon.
    var endCard = true
}

/// When each card of a reel starts and ends. Cards overlap by the length of the move between them.
struct ReelPlan: Equatable {
    enum Kind: Equatable {
        case intro
        /// A photo, by its place in the reel's order.
        case photo(Int)
        case outro
    }

    struct Item: Equatable {
        let kind: Kind
        let start: Double
        let length: Double
        var end: Double { start + length }
    }

    /// What shows at one moment: one card, or two while the move runs from `a` to `b`.
    struct Moment: Equatable {
        let a: Item
        let b: Item?
        /// How far the move has gone, 0 to 1. 0 when there is no move.
        let progress: Double
    }

    static let maxPhotos = 30
    static let fps = 30
    static let introLength = 2.0
    static let outroLength = 2.0

    let items: [Item]
    /// Seconds two cards share while one moves to the next.
    let overlap: Double
    let total: Double
    let move: ReelMove

    init(count: Int, options: ReelOptions, reduceMotion: Bool = false) {
        let move = reduceMotion && options.move == .flash ? .fade : options.move
        let overlap: Double = switch move {
        case .flash: 0.45
        case .slide: 0.5
        default: 0.65
        }
        var kinds: [(Kind, Double)] = [(.intro, Self.introLength)]
        for i in 0..<min(count, Self.maxPhotos) { kinds.append((.photo(i), options.speed.seconds)) }
        if options.endCard { kinds.append((.outro, Self.outroLength)) }
        var start = 0.0
        var items: [Item] = []
        for (kind, length) in kinds {
            items.append(Item(kind: kind, start: start, length: length))
            start += length - overlap
        }
        self.items = items
        self.overlap = overlap
        self.move = move
        total = start + overlap
    }

    var frameCount: Int { Int((total * Double(Self.fps) - 1e-6).rounded(.up)) }

    func moment(at time: Double) -> Moment {
        let t = min(max(time, 0), total)
        var k = 0
        for (n, item) in items.enumerated() where item.start <= t { k = n }
        if k > 0, t < items[k - 1].end {
            return Moment(a: items[k - 1], b: items[k], progress: min(max((t - items[k].start) / overlap, 0), 1))
        }
        return Moment(a: items[k], b: nil, progress: 0)
    }
}

/// The photos a reel shows, in its order: newest or oldest first, at most `ReelPlan.maxPhotos`.
func reelOrder(_ photos: [Photo], newestFirst: Bool) -> [Photo] {
    let sorted = photos.sorted { newestFirst ? $0.time > $1.time : $0.time < $1.time }
    return Array(sorted.prefix(ReelPlan.maxPhotos))
}

/// The date the way film cameras printed it: "'15 11 3". Always these digits, since it copies the camera.
func filmStamp(_ date: Date, calendar: Calendar = .app) -> String {
    let c = calendar.dateComponents([.year, .month, .day], from: date)
    let yy = String(format: "%02d", (c.year ?? 0) % 100)
    return "'\(yy) \(c.month ?? 0) \(c.day ?? 0)"
}

/// Smooth start and end for moves.
func reelEase(_ p: Double) -> Double {
    p < 0.5 ? 4 * p * p * p : 1 - pow(-2 * p + 2, 3) / 2
}
