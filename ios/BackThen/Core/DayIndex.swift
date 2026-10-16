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

/// How full each day of the year is, for the calendar's shading: 0 for no photos, then 1 to 4.
/// Days with photos are ranked by how many they have and split into four even groups, so the
/// shades spread out the same way for a small library or a huge one. Keyed by `MonthDay.key`.
func dayLevels(_ index: DayIndex, today: Date = Date()) -> [String: Int] {
    var counts: [String: Int] = [:]
    for m in 0..<12 {
        for d in 1...daysInMonth[m] {
            let md = MonthDay(month: m, day: d)
            let n = photoCount(index, md, today: today)
            if n > 0 { counts[md.key] = n }
        }
    }
    guard !counts.isEmpty else { return [:] }
    let sorted = counts.values.sorted()
    let total = Double(sorted.count)
    func lowerBound(_ n: Int) -> Int {
        var lo = 0, hi = sorted.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if sorted[mid] < n { lo = mid + 1 } else { hi = mid }
        }
        return lo
    }
    return counts.mapValues { n in
        // Where this count sits among all days, 0 to 1. Ties share the middle of their run.
        let below = lowerBound(n)
        let same = lowerBound(n + 1) - below
        let place = (Double(below) + Double(same) / 2) / total
        return 1 + min(3, Int(place * 4))
    }
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
    if device.isEmpty || google.isEmpty { return device + google }
    var landscapeTimes: [TimeInterval] = []
    var portraitTimes: [TimeInterval] = []
    for p in device {
        if p.width >= p.height {
            landscapeTimes.append(p.time.timeIntervalSince1970)
        } else {
            portraitTimes.append(p.time.timeIntervalSince1970)
        }
    }
    landscapeTimes.sort()
    portraitTimes.sort()

    func hasNear(_ sorted: [TimeInterval], _ target: TimeInterval) -> Bool {
        var lo = 0, hi = sorted.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if sorted[mid] < target { lo = mid + 1 } else { hi = mid }
        }
        if lo < sorted.count && abs(sorted[lo] - target) < 2 { return true }
        if lo > 0 && abs(sorted[lo - 1] - target) < 2 { return true }
        return false
    }

    let kept = google.filter { g in
        let t = g.time.timeIntervalSince1970
        let bucket = g.width >= g.height ? landscapeTimes : portraitTimes
        return !hasNear(bucket, t)
    }
    return device + kept
}
