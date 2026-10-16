import Foundation

extension Calendar {
    /// The app matches photos by Gregorian date, whatever calendar the phone shows.
    /// Made once, since it is used for every photo; it follows the phone if the time zone changes.
    static let app: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = .autoupdatingCurrent
        return c
    }()
}

/// A calendar day without a year. `month` is 0-based, to match the design's logic.
struct MonthDay: Hashable, Codable {
    let month: Int
    let day: Int

    var key: String { "\(month)-\(day)" }

    static func of(_ date: Date, calendar: Calendar = .app) -> MonthDay {
        let c = calendar.dateComponents([.month, .day], from: date)
        return MonthDay(month: c.month! - 1, day: c.day!)
    }

    static var today: MonthDay { of(Date()) }

    /// Moves forward or back, wrapping around the year.
    func shifted(by delta: Int) -> MonthDay {
        var m = month
        var d = day + delta
        while d < 1 {
            m = (m + 11) % 12
            d += daysInMonth[m]
        }
        while d > daysInMonth[m] {
            d -= daysInMonth[m]
            m = (m + 1) % 12
        }
        return MonthDay(month: m, day: d)
    }

    /// "October 16" in the app's language.
    var label: String { L.monthDay(self, "MMMMd") }
    /// "Oct 16" in the app's language.
    var shortLabel: String { L.monthDay(self, "MMMd") }
}

// In an extension, so MonthDay keeps its memberwise init.
extension MonthDay {
    /// Reads a `key` back, or nil if it isn't one.
    init?(key: String) {
        let parts = key.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 2, (0..<12).contains(parts[0]), (1...daysInMonth[parts[0]]).contains(parts[1]) else { return nil }
        self.init(month: parts[0], day: parts[1])
    }
}

/// February has 29 so Feb 29 can always be picked.
let daysInMonth = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

func agoLabel(_ year: Int, thisYear: Int = Calendar.app.component(.year, from: Date())) -> String {
    let diff = thisYear - year
    return diff == 0 ? L.s("ago_this_year") : L.p("ago_years", diff)
}

/// The reminder time, 12 or 24 hour as the language and phone prefer.
func formatTime(hour: Int, minute: Int) -> String { L.time(hour: hour, minute: minute) }

/// Start and end (exclusive) of a local calendar day. Nil if the day doesn't exist that year.
func dayRange(year: Int, _ md: MonthDay, calendar: Calendar = .app) -> (Date, Date)? {
    let comps = DateComponents(year: year, month: md.month + 1, day: md.day)
    guard comps.isValidDate(in: calendar), let start = calendar.date(from: comps),
          let end = calendar.date(byAdding: .day, value: 1, to: start) else { return nil }
    return (start, end)
}

func yearOf(_ date: Date) -> Int { Calendar.app.component(.year, from: date) }

func weekday(year: Int, _ md: MonthDay) -> String {
    guard let (start, _) = dayRange(year: year, md) else { return "" }
    return L.date(start, "EEEE")
}

/// "October 16, 2021" in the app's language.
func fullDate(year: Int, _ md: MonthDay) -> String {
    guard let (start, _) = dayRange(year: year, md) else { return md.label }
    return L.date(start, "yMMMMd")
}
