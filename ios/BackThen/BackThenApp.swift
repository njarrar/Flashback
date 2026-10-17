import SwiftUI
import UserNotifications
import WidgetKit

@main
struct BackThenApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var store = AppStore()
    @Environment(\.scenePhase) private var phase

    init() {
        L.start()
        Nunito.register()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .tint(Palette.accent)
                // Right to left for Arabic, and dates and digits in the app's language.
                .environment(\.layoutDirection, L.lang.rtl ? .rightToLeft : .leftToRight)
                .environment(\.locale, L.locale)
                .onAppear { delegate.store = store }
                .onChange(of: phase) { _, now in
                    if now == .active { store.catchUpToToday() }
                    // New photos may have been taken since the app was last open.
                    if now == .active, store.saved.onboarded, store.saved.sources.device {
                        Task { await store.refreshIndex() }
                    }
                    // The widget picks again, in case photos changed while the app was open.
                    if now == .background { WidgetCenter.shared.reloadAllTimelines() }
                }
                // A tap on the home screen widget opens Today.
                .onOpenURL { url in
                    guard url.scheme == "backthen", url.host == "today" else { return }
                    store.viewer = nil
                    store.day = .today
                    store.tab = .today
                }
        }
    }
}

/// Opens the right day when someone taps the daily reminder.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    @MainActor var store: AppStore?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        guard let m = info["month"] as? Int, let d = info["day"] as? Int else { return }
        await MainActor.run {
            store?.day = MonthDay(month: m, day: d)
            store?.tab = .today
        }
    }
}
