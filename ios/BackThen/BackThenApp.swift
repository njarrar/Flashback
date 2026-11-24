import SwiftUI
import UserNotifications

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
                    if now == .active {
                        store.catchUpToToday()
                        // New photos may have been taken since the app was last open.
                        if store.saved.onboarded, store.saved.sources.device {
                            Task { await store.refreshIndex() }
                        }
                    } else {
                        store.flushSave()
                    }
                }
                // A tap on the home screen widget opens Today.
                .onOpenURL { url in
                    guard url.scheme == "backthen" || url.scheme == "flashback", url.host == "today" else { return }
                    store.openDeepLink(day: .today)
                }
        }
    }
}

/// Opens the right day when someone taps the daily reminder.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    @MainActor var store: AppStore? {
        didSet {
            if let store, let pending = pendingDay {
                pendingDay = nil
                open(pending, in: store)
            }
        }
    }
    @MainActor private var pendingDay: MonthDay?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        guard let m = info["month"] as? Int,
              let d = info["day"] as? Int,
              let md = MonthDay(key: "\(m)-\(d)") else { return }
        await MainActor.run {
            if let store {
                open(md, in: store)
            } else {
                pendingDay = md
            }
        }
    }

    @MainActor
    private func open(_ md: MonthDay, in store: AppStore) {
        store.openDeepLink(day: md)
    }
}
