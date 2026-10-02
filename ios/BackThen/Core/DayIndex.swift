import Foundation

/// Counts photos per calendar day and year from their capture times.
func buildDayIndex(_ times: [Date], calendar: Calendar = .current) -> DayIndex {
    var index: DayIndex = [:]
    for t in times {
        let c = calendar.dateComponents([.year, .month, .day], from: t)
        let key = "\(c.month! - 1)-\(c.day!)"
        index[key, default: [:]][c.year!, default: 0] += 1
    }
    return index
}

func mergeIndexes(_ indexes: DayIndex...) -> DayIndex {
    var out: DayIndex = [:]
    for index in indexes {
        for (key, byYear) in index {
            for (y, n) in byYear { out[key, default: [:]][y, default: 0] += n }
        }
    }
    return out
}

/// Past years with photos on this day, newest first. This year counts only once the
/// day has passed, so "today" never shows photos from earlier today.
func yearsForDay(_ index: DayIndex, _ md: MonthDay, today: Date = Date(), calendar: Calendar = .current) -> [Int] {
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

/// Drops Google copies of shots already on the phone. Google keeps the capture time,
/// so a match within two seconds and the same orientation counts as the same shot.
func dropDuplicates(device: [Photo], google: [Photo]) -> [Photo] {
    func landscape(_ p: Photo) -> Bool { p.width >= p.height }
    let kept = google.filter { g in
        !device.contains { abs($0.time.timeIntervalSince(g.time)) < 2 && landscape($0) == landscape(g) }
    }
    return device + kept
}
