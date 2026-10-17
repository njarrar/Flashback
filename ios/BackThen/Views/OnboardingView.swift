import SwiftUI

struct OnboardingView: View {
    @Environment(AppStore.self) private var store
    @State private var denied = false
    @State private var chosePhotos = false

    var body: some View {
        if chosePhotos { reminder } else { welcome }
    }

    private var welcome: some View {
        let label = MonthDay.today.shortLabel
        return VStack(alignment: .leading, spacing: 24) {
            HStack(spacing: 10) {
                Logo(size: 36)
                Text(L.s("app_name")).font(.nunito(22, .black)).tight(22).foregroundStyle(Palette.ink)
            }
            .padding(.top, 16)
            ZStack(alignment: .top) {
                Print(year: 2016, label: label, image: "welcome-2016-hills", width: 150, angle: -7)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.top, 36)
                Print(year: 2021, label: label, image: "welcome-2021-sail", width: 150, angle: 6)
                    .frame(maxWidth: .infinity, alignment: .trailing).padding(.top, 8)
                Print(year: 2025, label: label, image: "welcome-2025-cake", width: 176, angle: -1.5)
                    .padding(.top, 118)
            }
            .frame(maxHeight: .infinity, alignment: .top)
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 10) {
                Text(L.s("welcome_title")).font(.nunito(32, .bold)).tight(32).foregroundStyle(Palette.ink)
                    .accessibilityAddTraits(.isHeader)
                Text(L.s("welcome_body"))
                    .font(.nunito(16)).foregroundStyle(Palette.muted)
            }
            if denied {
                Label(L.s("welcome_denied"), systemImage: "exclamationmark.circle")
                    .font(.nunito(14, .medium)).foregroundStyle(Palette.accInk)
                    .padding(12).background(Palette.accTint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            VStack(spacing: 10) {
                Pill(label: L.s("show_my_photos")) {
                    Task {
                        if await store.connectDevice() { chosePhotos = true } else { denied = true }
                    }
                }
                HStack(spacing: 6) {
                    Image(systemName: "lock").font(.system(size: 13))
                    Text(L.s("privacy_line")).font(.nunito(13, .semibold))
                }
                .foregroundStyle(Palette.muted2)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.top, 2)
            }
        }
        .padding(.horizontal, 24).padding(.bottom, 24)
        .background(Palette.bg)
    }

    private var reminder: some View {
        let today = MonthDay.today
        let years = yearsForDay(store.index, today)
        let r = store.saved.reminder
        return VStack(spacing: 28) {
            Spacer()
            HStack(alignment: .top, spacing: 12) {
                Logo(size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(L.s("app_name")).font(.nunito(14, .semibold))
                        Spacer()
                        Text(formatTime(hour: r.hour, minute: r.minute)).font(.nunito(14)).foregroundStyle(Palette.muted)
                    }
                    Text(years.isEmpty ? L.s("reminder_title_any", ["day": today.shortLabel])
                         : L.s("reminder_title_years", ["day": today.shortLabel, "years": L.p("years", years.count)])).font(.nunito(14, .semibold))
                    Text(years.last.map { L.s("reminder_body_oldest", ["year": $0]) } ?? L.s("reminder_body")).font(.nunito(14)).foregroundStyle(Palette.muted)
                }
                .foregroundStyle(Palette.ink)
            }
            .padding(14)
            .background(Palette.glass, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Palette.line))
            VStack(alignment: .leading, spacing: 10) {
                Text(L.s("reminder_onboarding_title")).font(.nunito(32, .bold)).tight(32).foregroundStyle(Palette.ink).accessibilityAddTraits(.isHeader)
                Text(L.s("reminder_onboarding_body")).font(.nunito(16)).foregroundStyle(Palette.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Spacer()
            VStack(spacing: 6) {
                Pill(label: L.s("turn_on_reminder")) {
                    Task { store.finishOnboarding(reminder: await Reminders.requestPermission()) }
                }
                Pill(label: L.s("not_now"), kind: .ghost, height: 48) { store.finishOnboarding(reminder: false) }
            }
        }
        .padding(.horizontal, 24).padding(.bottom, 24)
        .background(Palette.bg)
    }
}
