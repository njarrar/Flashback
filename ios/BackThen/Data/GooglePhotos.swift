import Foundation
import UIKit

// Google Photos import through the Picker API.
//
// Since March 31, 2025 Google no longer lets third-party apps read a user's whole
// Google Photos library. The Picker API is the supported way in: the user picks photos
// in a Google screen and the app gets only those. Back Then saves a copy of each pick
// on the phone and folds it into the same day-by-day view.
// https://developers.google.com/photos/support/updates

enum GooglePhotosError: Error {
    case http(String)
}

enum GooglePhotos {
    private static let api = "https://photospicker.googleapis.com/v1"

    private struct PollingConfig: Decodable { let pollInterval: String?; let timeoutIn: String? }
    private struct Session: Decodable {
        let id: String
        let pickerUri: String?
        let mediaItemsSet: Bool?
        let pollingConfig: PollingConfig?
    }
    private struct Metadata: Decodable { let width: Int?; let height: Int? }
    private struct MediaFile: Decodable { let baseUrl: String; let mediaFileMetadata: Metadata? }
    private struct Item: Decodable { let id: String; let createTime: String; let type: String?; let mediaFile: MediaFile }
    private struct Page: Decodable { let mediaItems: [Item]?; let nextPageToken: String? }

    static var folder: URL {
        let dir = URL.applicationSupportDirectory.appending(path: "google-photos")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func fileURL(_ name: String) -> URL { folder.appending(path: name) }

    private static func seconds(_ s: String?, _ fallback: Double) -> Double {
        s.flatMap { Double($0.replacingOccurrences(of: "s", with: "")) } ?? fallback
    }

    /// True for HTTPS links on the Google hosts the Picker API uses. Only these get the sign-in token.
    static func isGoogleHTTPS(_ url: URL) -> Bool {
        guard url.scheme == "https", let host = url.host()?.lowercased() else { return false }
        return host == "photospicker.googleapis.com" || host.hasSuffix(".googleusercontent.com") || host.hasSuffix(".ggpht.com")
    }

    /// A request that carries the token, only to Google, with a timeout.
    private static func request(_ string: String, token: String) throws -> URLRequest {
        guard let url = URL(string: string), isGoogleHTTPS(url) else { throw GooglePhotosError.http("Refusing to send the Google token to \(string)") }
        var req = URLRequest(url: url, timeoutInterval: 30)
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        return req
    }

    private static func call<T: Decodable>(_ token: String, _ path: String, method: String = "GET", body: Data? = nil) async throws -> T {
        var req = try request(api + path, token: token)
        req.httpMethod = method
        if let body {
            req.httpBody = body
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw GooglePhotosError.http(String(decoding: data, as: UTF8.self))
        }
        return try JSONDecoder().decode(T.self, from: data.isEmpty ? Data("{}".utf8) : data)
    }

    private struct Empty: Decodable {}

    /// Opens the Google Photos picker, waits for the user to tap Done, then saves the
    /// picks into app storage. Returns nil if the user never finishes picking.
    @MainActor
    static func pick(token: String, onProgress: @escaping (Int, Int) -> Void) async throws -> [Photo]? {
        let session: Session = try await call(token, "/sessions", method: "POST", body: Data("{}".utf8))
        defer { Task { let _: Empty? = try? await call(token, "/sessions/\(session.id)", method: "DELETE") } }
        let every = seconds(session.pollingConfig?.pollInterval, 3)
        let giveUpAt = Date().addingTimeInterval(seconds(session.pollingConfig?.timeoutIn, 600))

        // Opens in the Google Photos app if installed, otherwise in Safari.
        if let uri = session.pickerUri, let url = URL(string: uri + "/autoclose") {
            await UIApplication.shared.open(url)
        }

        var ready = false
        while !ready && Date() < giveUpAt {
            try await Task.sleep(for: .seconds(every))
            let s: Session = try await call(token, "/sessions/\(session.id)")
            ready = s.mediaItemsSet ?? false
        }
        guard ready else { return nil }

        var items: [Item] = []
        var pageToken: String?
        repeat {
            var q = "sessionId=\(session.id)&pageSize=100"
            if let pageToken { q += "&pageToken=\(pageToken)" }
            let page: Page = try await call(token, "/mediaItems?\(q)")
            items += page.mediaItems ?? []
            pageToken = page.nextPageToken
        } while !(pageToken ?? "").isEmpty

        let picked = items.filter { $0.type == "PHOTO" }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let isoPlain = ISO8601DateFormatter()
        var out: [Photo] = []
        for (n, item) in picked.enumerated() {
            onProgress(n, picked.count)
            // Base URLs expire after an hour and need the token, so keep a local copy.
            let name = item.id.replacingOccurrences(of: "[^A-Za-z0-9_-]", with: "_", options: .regularExpression) + ".jpg"
            let target = fileURL(name)
            if !FileManager.default.fileExists(atPath: target.path) {
                let req = try request(item.mediaFile.baseUrl + "=w2048-h2048", token: token)
                let (tmp, response) = try await URLSession.shared.download(for: req)
                guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                    throw GooglePhotosError.http("Download failed")
                }
                try FileManager.default.moveItem(at: tmp, to: target)
            }
            let time = iso.date(from: item.createTime) ?? isoPlain.date(from: item.createTime) ?? Date()
            out.append(Photo(key: "g:\(item.id)", source: .google, ref: name,
                             width: item.mediaFile.mediaFileMetadata?.width ?? 1,
                             height: item.mediaFile.mediaFileMetadata?.height ?? 1, time: time))
        }
        onProgress(picked.count, picked.count)
        return out
    }

    static func deleteAll() {
        try? FileManager.default.removeItem(at: folder)
    }
}
