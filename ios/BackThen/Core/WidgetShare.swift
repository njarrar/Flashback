import Foundation
import os

/// What the home screen widget needs from the app's settings. The widget can't read the
/// app's own files, so the app writes this to the folder they share (an App Group).
struct WidgetInfo: Codable, Equatable, Sendable {
    /// The phone's photos are a connected source.
    var device = false
    /// Hidden days, as `MonthDay.key`. The widget never shows them.
    var hidden: Set<String> = []

    static let kind = "TodayWidget"
    static let group = "group.com.njarrar.backthen"
    private static let logger = Logger(subsystem: "com.njarrar.backthen", category: "WidgetShare")

    private static var file: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)?.appending(path: "widget.json")
    }

    /// Nil when there is no shared folder (a build without the App Group) or nothing saved yet.
    static func read() -> WidgetInfo? {
        guard let file, let data = try? Data(contentsOf: file) else { return nil }
        return try? JSONDecoder().decode(WidgetInfo.self, from: data)
    }

    @discardableResult
    func write() -> Bool {
        guard let file = Self.file else {
            Self.logger.error("App Group container unavailable for \(Self.group, privacy: .public)")
            return false
        }
        do {
            let data = try JSONEncoder().encode(self)
            try data.write(to: file, options: .atomic)
            return true
        } catch {
            Self.logger.error("Failed writing widget.json: \(error.localizedDescription, privacy: .public)")
            return false
        }
    }
}
