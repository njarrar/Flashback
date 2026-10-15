import SwiftUI

struct OnboardingView: View {
    @Environment(AppStore.self) private var store
    @State private var denied = false
    @State private var chosePhotos = false
    @State private var google = false

    var body: some View {
        if chosePhotos || store.saved.sources.google { reminder } else { welcome }
    }

    private var welcome: some View {
        let label = MonthDay.today.shortLabel
        return VStack(alignment: .leading, spacing: 24) {
            HStack(spacing: 10) {
                Logo(size: 36)
                Text("Back Then").font(.nunito(22, .black)).tight(22).foregroundStyle(Palette.ink)
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
                Text("See what this day looked like, every year.").font(.nunito(32, .bold)).tight(32).foregroundStyle(Palette.ink)
                    .accessibilityAddTraits(.isHeader)
                Text("Each morning, Back Then finds the photos you took on today's date in past years and sorts them by year.")
                    .font(.nunito(16)).foregroundStyle(Palette.muted)
            }
            if denied {
                Label("Without photo access there is nothing to show. You can allow it later in Settings.", systemImage: "exclamationmark.circle")
                    .font(.nunito(14, .medium)).foregroundStyle(Palette.accInk)
                    .padding(12).background(Palette.accTint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            // Apple Photos leads on iPhone, as in the design.
            VStack(spacing: 10) {
                Pill(label: "Use Apple Photos", systemImage: "apple.logo") {
                    Task {
                        if await store.connectDevice() { chosePhotos = true } else { denied = true }
                    }
                }
                Pill(label: "Use Google Photos", systemImage: "photo.on.rectangle.angled", kind: .secondary) { google = true }
                HStack(spacing: 6) {
                    Image(systemName: "lock").font(.system(size: 13))
                    Text("Your photos stay on this phone. Nothing is uploaded.").font(.nunito(13, .medium))
                }
                .foregroundStyle(Palette.muted)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.top, 2)
            }
        }
        .padding(.horizontal, 24).padding(.bottom, 24)
        .background(Palette.bg)
        .sheet(isPresented: $google) { GoogleSheet() }
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
                        Text("Back Then").font(.nunito(14, .semibold))
                        Spacer()
                        Text(formatTime(hour: r.hour, minute: r.minute)).font(.nunito(14)).foregroundStyle(Palette.muted)
                    }
                    Text("\(today.shortLabel) has photos from \(years.isEmpty ? "past years" : plural(years.count, "year"))").font(.nunito(14, .semibold))
                    Text((years.last.map { "The oldest is from \($0). " } ?? "") + "Tap to look back.").font(.nunito(14)).foregroundStyle(Palette.muted)
                }
                .foregroundStyle(Palette.ink)
            }
            .padding(14)
            .background(Palette.glass, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Palette.line))
            VStack(alignment: .leading, spacing: 10) {
                Text("One reminder a day").font(.nunito(32, .bold)).tight(32).foregroundStyle(Palette.ink).accessibilityAddTraits(.isHeader)
                Text("Only on days that have photos from past years. You can change the time later.").font(.nunito(16)).foregroundStyle(Palette.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Spacer()
            VStack(spacing: 6) {
                Pill(label: "Turn on reminder") {
                    Task { store.finishOnboarding(reminder: await Reminders.requestPermission()) }
                }
                Pill(label: "Not now", kind: .ghost, height: 48) { store.finishOnboarding(reminder: false) }
            }
        }
        .padding(.horizontal, 24).padding(.bottom, 24)
        .background(Palette.bg)
    }
}
