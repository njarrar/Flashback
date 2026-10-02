import Foundation

/// A calendar day without a year. `month` is 0-based, to match the design's logic.
struct MonthDay: Hashable, Codable {
    let month: Int
    let day: Int

    var key: String { "\(month)-\(day)" }

    static func of(_ date: Date, calendar: Calendar = .current) -> MonthDay {
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

    var label: String { "\(months[month]) \(day)" }
    var shortLabel: String { "\(monthsShort[month]) \(day)" }
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

let months = ["January", "February", "March", "April", "May", "June",
              "July", "August", "September", "October", "November", "December"]
let monthsShort = months.map { String($0.prefix(3)) }

/// February has 29 so Feb 29 can always be picked.
let daysInMonth = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

func plural(_ n: Int, _ word: String) -> String { "\(n) \(word)\(n == 1 ? "" : "s")" }

func agoLabel(_ year: Int, thisYear: Int = Calendar.current.component(.year, from: Date())) -> String {
    switch thisYear - year {
    case 0: return "Earlier this year"
    case 1: return "1 year ago"
    case let diff: return "\(diff) years ago"
    }
}

/// A reminder time in the phone's 12 or 24 hour style.
func formatTime(hour: Int, minute: Int, locale: Locale = .autoupdatingCurrent) -> String {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = TimeZone(identifier: "UTC")!
    let date = cal.date(from: DateComponents(year: 2000, month: 1, day: 1, hour: hour, minute: minute))!
    var style = Date.FormatStyle(date: .omitted, time: .shortened, locale: locale, calendar: cal)
    style.timeZone = cal.timeZone
    return date.formatted(style)
}

/// Start and end (exclusive) of a local calendar day. Nil if the day doesn't exist that year.
func dayRange(year: Int, _ md: MonthDay, calendar: Calendar = .current) -> (Date, Date)? {
    let comps = DateComponents(year: year, month: md.month + 1, day: md.day)
    guard comps.isValidDate(in: calendar), let start = calendar.date(from: comps),
          let end = calendar.date(byAdding: .day, value: 1, to: start) else { return nil }
    return (start, end)
}

func yearOf(_ date: Date) -> Int { Calendar.current.component(.year, from: date) }

func weekday(year: Int, _ md: MonthDay) -> String {
    guard let (start, _) = dayRange(year: year, md) else { return "" }
    let f = DateFormatter()
    f.dateFormat = "EEEE"
    return f.string(from: start)
}
