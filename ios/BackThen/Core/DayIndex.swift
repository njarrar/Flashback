import Foundation

/// Counts photos per calendar day and year from their capture times.
func buildDayIndex(_ times: [Date], calendar: Calendar = .app) -> DayIndex {
    addingToDayIndex([:], times, calendar: calendar)
}

/// The index with photos taken at `times` counted too: for new photos, without a full scan.
func addingToDayIndex(_ start: DayIndex, _ times: [Date], calendar: Calendar = .app) -> DayIndex {
    var index = start
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

func hasPhotos(_ index: DayIndex, _ md: MonthDay, today: Date = Date(), calendar: Calendar = .app) -> Bool {
    !yearsForDay(index, md, today: today, calendar: calendar).isEmpty
}

/// Photos on this day across the past years `yearsForDay` counts.
func photoCount(_ index: DayIndex, _ md: MonthDay, today: Date = Date(), calendar: Calendar = .app) -> Int {
    guard let byYear = index[md.key] else { return 0 }
    return yearsForDay(index, md, today: today, calendar: calendar).reduce(0) { $0 + (byYear[$1] ?? 0) }
}

/// The favorite to show for a day in the calendar: one taken on that day, in a year the day counts.
func favoriteCover(_ favorites: [Photo], _ md: MonthDay, years: [Int], calendar: Calendar = .app) -> Photo? {
    favorites.first { MonthDay.of($0.time, calendar: calendar) == md && years.contains(yearOf($0.time, calendar: calendar)) }
}

/// The closest earlier and later days that have photos, within 60 days.
func nearbyDays(_ index: DayIndex, _ md: MonthDay, today: Date = Date(), calendar: Calendar = .app) -> (before: MonthDay?, after: MonthDay?) {
    var before: MonthDay?
    var after: MonthDay?
    for k in 1...60 where before == nil || after == nil {
        if before == nil, hasPhotos(index, md.shifted(by: -k), today: today, calendar: calendar) { before = md.shifted(by: -k) }
        if after == nil, hasPhotos(index, md.shifted(by: k), today: today, calendar: calendar) { after = md.shifted(by: k) }
    }
    return (before, after)
}

/// Where a swipe on Today lands: the next day with photos going forward (`step` 1) or back
/// (`step` -1), and how many empty days it passed. Wraps across Dec 31 <-> Jan 1 and can visit
/// any day that has past-year photos, matching the unlocked Calendar.
func swipeTarget(_ index: DayIndex, from md: MonthDay, step: Int, today: Date = Date(), calendar: Calendar = .app) -> (day: MonthDay, skipped: Int)? {
    guard step != 0 else { return nil }
    let dir = step > 0 ? 1 : -1
    let thisYear = yearOf(today, calendar: calendar)
    let leap = isLeapYear(thisYear)
    var at = md
    var skipped = 0
    for _ in 0..<366 {
        let next = at.shifted(by: dir)
        if next == md { return nil }
        if hasPhotos(index, next, today: today, calendar: calendar) { return (next, skipped) }
        if !(next.month == 1 && next.day == 29 && !leap) {
            skipped += 1
        }
        at = next
    }
    return nil
}
