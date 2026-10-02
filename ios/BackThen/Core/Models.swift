import Foundation

enum PhotoSource: String, Codable {
    case device, google
}

/// One photo, from the phone's library or a Google Photos import.
struct Photo: Codable, Hashable, Identifiable {
    /// Stable key, prefixed by source: "d:<localIdentifier>" or "g:<googleId>".
    let key: String
    let source: PhotoSource
    /// For device photos, the PhotoKit local identifier. For Google photos, a file name in app storage.
    let ref: String
    let width: Int
    let height: Int
    /// Capture time.
    let time: Date

    var id: String { key }
}

struct Album: Codable, Hashable, Identifiable {
    let id: String
    var name: String
    var photos: [Photo]
    let createdAt: Date
}

struct YearGroup: Identifiable {
    let year: Int
    let photos: [Photo]
    var id: Int { year }
}

/// Photo counts per day: "month-day" -> year -> count.
typealias DayIndex = [String: [Int: Int]]
