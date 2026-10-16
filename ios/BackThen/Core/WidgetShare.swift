import Foundation

/// What the home screen widget needs from the app's settings. The widget can't read the
/// app's own files, so the app writes this to the folder they share (an App Group).
struct WidgetInfo: Codable, Equatable {
    /// The phone's photos are a connected source.
    var device = false
    /// Hidden days, as `MonthDay.key`. The widget never shows them.
    var hidden: Set<String> = []

    static let kind = "TodayWidget"
    static let group = "group.com.njarrar.backthen"

    private static var file: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)?.appending(path: "widget.json")
    }

    /// Nil when there is no shared folder (a build without the App Group) or nothing saved yet.
    static func read() -> WidgetInfo? {
        guard let file, let data = try? Data(contentsOf: file) else { return nil }
        return try? JSONDecoder().decode(WidgetInfo.self, from: data)
    }

    func write() {
        guard let file = Self.file, let data = try? JSONEncoder().encode(self) else { return }
        try? data.write(to: file, options: .atomic)
    }
}
