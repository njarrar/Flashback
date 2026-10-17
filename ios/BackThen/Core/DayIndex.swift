import Foundation

/// Counts photos per calendar day and year from their capture times.
func buildDayIndex(_ times: [Date], calendar: Calendar = .app) -> DayIndex {
    var index: DayIndex = [:]
    for t in times {
        let c = calendar.dateComponents([.year, .month, .day], from: t)
        let key = "\(c.month! - 1)-\(c.day!)"
        index[key, default: [:]][c.year!, default: 0] += 1
    }
    return index
}

/// Past years with photos on this day, newest first. This year counts only once the
/// day has passed, so "today" never shows photos from earlier today.
func yearsForDay(_ index: DayIndex, _ md: MonthDay, today: Date = Date(), calendar: Calendar = .app) -> [Int] {
    guard let byYear = index[md.key] else { return [] }
    let now = calendar.dateComponents([.year, .month, .day], from: today)
    let thisYear = now.year!
    let passed = md.month < now.month! - 1 || (md.month == now.month! - 1 && md.day < now.day!)
    return byYear.filter { y, n in n > 0 && (y < thisYear || (y == thisYear && passed)) }
        .keys.sorted(by: >)
}

func hasPhotos(_ index: DayIndex, _ md: MonthDay, today: Date = Date()) -> Bool {
    !yearsForDay(index, md, today: today).isEmpty
}

/// Photos on this day across the past years `yearsForDay` counts.
func photoCount(_ index: DayIndex, _ md: MonthDay, today: Date = Date()) -> Int {
    guard let byYear = index[md.key] else { return 0 }
    return yearsForDay(index, md, today: today).reduce(0) { $0 + (byYear[$1] ?? 0) }
}

/// The favorite to show for a day in the calendar: one taken on that day, in a year the day counts.
func favoriteCover(_ favorites: [Photo], _ md: MonthDay, years: [Int]) -> Photo? {
    favorites.first { MonthDay.of($0.time) == md && years.contains(yearOf($0.time)) }
}

/// The closest earlier and later days that have photos, within 60 days.
func nearbyDays(_ index: DayIndex, _ md: MonthDay, today: Date = Date()) -> (before: MonthDay?, after: MonthDay?) {
    var before: MonthDay?
    var after: MonthDay?
    for k in 1...60 where before == nil || after == nil {
        if before == nil, hasPhotos(index, md.shifted(by: -k), today: today) { before = md.shifted(by: -k) }
        if after == nil, hasPhotos(index, md.shifted(by: k), today: today) { after = md.shifted(by: k) }
    }
    return (before, after)
}
