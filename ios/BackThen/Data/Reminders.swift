import UserNotifications

/// One reminder a day, only on days with photos from past years.
enum Reminders {
    static let daysAhead = 30

    static func requestPermission() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
    }

    /// Schedules the next month of reminders. Called on every launch, so the window keeps moving.
    static func schedule(index: DayIndex, hour: Int, minute: Int) async {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        let cal = Calendar.app
        let now = Date()
        for k in 0..<daysAhead {
            guard let day = cal.date(byAdding: .day, value: k, to: cal.startOfDay(for: now)),
                  let when = cal.date(bySettingHour: hour, minute: minute, second: 0, of: day), when > now else { continue }
            let md = MonthDay.of(when)
            // Judge "past years" as of that day, not today.
            let years = yearsForDay(index, md, today: when)
            guard let oldest = years.last else { continue }
            let content = UNMutableNotificationContent()
            content.title = L.s("reminder_title_years", ["day": md.label, "years": L.p("years", years.count)])
            content.body = L.s("reminder_body_oldest", ["year": oldest])
            content.userInfo = ["month": md.month, "day": md.day]
            let trigger = UNCalendarNotificationTrigger(dateMatching: cal.dateComponents([.calendar, .year, .month, .day, .hour, .minute], from: when), repeats: false)
            try? await center.add(UNNotificationRequest(identifier: "day-\(k)", content: content, trigger: trigger))
        }
    }

    static func cancel() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
    }
}
