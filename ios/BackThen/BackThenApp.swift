import SwiftUI
import UserNotifications

@main
struct BackThenApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var store = AppStore()
    @Environment(\.scenePhase) private var phase

    init() {
        Nunito.register()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .tint(Palette.accent)
                .onAppear { delegate.store = store }
                .onChange(of: phase) { _, now in
                    // New photos may have been taken since the app was last open.
                    if now == .active, store.saved.onboarded, store.saved.sources.device {
                        Task { await store.refreshIndex() }
                    }
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
