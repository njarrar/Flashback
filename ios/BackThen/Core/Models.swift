import Foundation

enum PhotoSource: String, Codable {
    /// The phone's Photos library.
    case device
    /// A JPEG in app storage. Only the debug screenshots use these (see Demo.swift).
    case sample
}

/// Where the screenshot workflow drops sample JPEGs.
enum SamplePhotos {
    static let folder = URL.applicationSupportDirectory.appending(path: "samples")
    static func fileURL(_ name: String) -> URL { folder.appending(path: name) }
}

/// One photo from the phone's library.
struct Photo: Codable, Hashable, Identifiable {
    /// Stable key, prefixed by source: "d:<localIdentifier>" or "s:<file name>".
    let key: String
    let source: PhotoSource
    /// For device photos, the PhotoKit local identifier. For samples, a file name in app storage.
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

extension Album {
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        photos = try c.decode(Lossy<Photo>.self, forKey: .photos).items
        createdAt = try c.decode(Date.self, forKey: .createdAt)
    }
}

/// Decodes a list one item at a time and skips any that no longer decode,
/// such as Google Photos picks saved by version 1.8 and earlier.
struct Lossy<T: Decodable>: Decodable {
    let items: [T]

    init(from decoder: Decoder) throws {
        var c = try decoder.unkeyedContainer()
        var out: [T] = []
        while !c.isAtEnd {
            if let item = try? c.decode(T.self) {
                out.append(item)
            } else if (try? c.decode(Skip.self)) == nil {
                break
            }
        }
        items = out
    }

    private struct Skip: Decodable {}
}

struct YearGroup: Identifiable {
    let year: Int
    let photos: [Photo]
    var id: Int { year }
}

/// Photo counts per day: "month-day" -> year -> count.
typealias DayIndex = [String: [Int: Int]]
