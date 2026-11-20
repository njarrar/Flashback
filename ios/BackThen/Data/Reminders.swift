import UserNotifications

/// One reminder a day, only on days with photos from past years.
enum Reminders {
    static let daysAhead = 30

    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }

    /// Computes upcoming reminder fire dates without duplicating across spring-forward DST gaps.
    static func nextFireDates(
        from now: Date = Date(),
        daysAhead: Int = daysAhead,
        hour: Int,
        minute: Int,
        calendar: Calendar = .app
    ) -> [(offset: Int, date: Date, components: DateComponents)] {
        let startOfToday = calendar.startOfDay(for: now)
        var out: [(Int, Date, DateComponents)] = []
        var seenDays = Set<String>()
        for k in 0..<daysAhead {
            guard let day = calendar.date(byAdding: .day, value: k, to: startOfToday) else { continue }
            let dayParts = calendar.dateComponents([.year, .month, .day], from: day)
            guard let y = dayParts.year, let m = dayParts.month, let d = dayParts.day else { continue }
            let comps = DateComponents(calendar: calendar, timeZone: calendar.timeZone, year: y, month: m, day: d, hour: hour, minute: minute, second: 0)
            let when: Date
            if comps.isValidDate(in: calendar), let exact = calendar.date(from: comps) {
                when = exact
            } else if let adjusted = calendar.nextDate(
                after: calendar.startOfDay(for: day),
                matching: DateComponents(hour: hour, minute: minute),
                matchingPolicy: .nextTimePreservingSmallerComponents
            ) {
                let adjParts = calendar.dateComponents([.year, .month, .day], from: adjusted)
                if adjParts.year == y && adjParts.month == m && adjParts.day == d {
                    when = adjusted
                } else {
                    let afterGap = DateComponents(calendar: calendar, timeZone: calendar.timeZone, year: y, month: m, day: d, hour: min(hour + 1, 23), minute: minute, second: 0)
                    guard let fallback = calendar.date(from: afterGap) else { continue }
                    when = fallback
                }
            } else {
                continue
            }
            guard when > now else { continue }
            let dayStamp = "\(y)-\(m)-\(d)"
            guard seenDays.insert(dayStamp).inserted else { continue }
            let finalComps = calendar.dateComponents([.calendar, .year, .month, .day, .hour, .minute], from: when)
            out.append((k, when, finalComps))
        }
        return out
    }

    /// Schedules the next month of reminders. Called on every launch, so the window keeps moving.
    static func schedule(index: DayIndex, hour: Int, minute: Int) async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .authorized
            || settings.authorizationStatus == .provisional
            || settings.authorizationStatus == .ephemeral else { return }
        center.removeAllPendingNotificationRequests()
        let cal = Calendar.app
        for (k, when, triggerComps) in nextFireDates(from: Date(), daysAhead: daysAhead, hour: hour, minute: minute, calendar: cal) {
            let md = MonthDay.of(when, calendar: cal)
            // Judge "past years" as of that day, not today.
            let years = yearsForDay(index, md, today: when, calendar: cal)
            guard let oldest = years.last else { continue }
            let content = UNMutableNotificationContent()
            content.title = L.s("reminder_title_years", ["day": md.label, "years": L.p("years", years.count)])
            content.body = L.s("reminder_body_oldest", ["year": oldest])
            content.sound = .default
            content.userInfo = ["month": md.month, "day": md.day]
            let trigger = UNCalendarNotificationTrigger(dateMatching: triggerComps, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: "day-\(k)", content: content, trigger: trigger))
        }
    }

    static func cancel() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
    }
}
