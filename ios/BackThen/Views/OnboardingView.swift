import SwiftUI

struct OnboardingView: View {
    @Environment(AppStore.self) private var store
    @State private var denied = false
    @State private var chosePhotos = Self.demoReminder

    var body: some View {
        if chosePhotos { reminder } else { welcome }
    }

    /// Debug screenshots only: start on the reminder step.
    private static var demoReminder: Bool {
        #if DEBUG
        AppStore.launchScene == "reminder"
        #else
        false
        #endif
    }

    /// The header and buttons stay put. Between them the prints take whatever room the words
    /// leave, and step aside when large text needs it all; past that, the words scroll.
    private var welcome: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Logo(size: 36)
                Text(L.s("app_name")).font(.nunito(22, .black)).tight(22).foregroundStyle(Palette.ink)
            }
            .padding(.top, 16)
            GeometryReader { geo in
                ScrollView {
                    WelcomeFit(viewport: geo.size.height) {
                        WelcomePrints(label: MonthDay.today.shortLabel)
                        VStack(alignment: .leading, spacing: 10) {
                            Text(L.s("welcome_title")).font(.nunito(32, .bold)).tight(32).foregroundStyle(Palette.ink)
                                .accessibilityAddTraits(.isHeader)
                            Text(L.s("welcome_body"))
                                .font(.nunito(16)).foregroundStyle(Palette.muted)
                            if denied {
                                Label(L.s("welcome_denied"), systemImage: "exclamationmark.circle")
                                    .font(.nunito(14, .medium)).foregroundStyle(Palette.accInk)
                                    .padding(12).background(Palette.accTint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                    .padding(.top, 6)
                            }
                        }
                        .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .scrollBounceBehavior(.basedOnSize)
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
        // The middle scrolls when large text no longer fits; the buttons stay.
        return VStack(spacing: 28) {
            GeometryReader { geo in
                ScrollView {
                    VStack(spacing: 28) {
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
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .frame(minHeight: geo.size.height)
                }
                .scrollBounceBehavior(.basedOnSize)
            }
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

/// Prints on top, words at the bottom of a page at least [viewport] tall. The prints get the
/// room the words leave, up to a little taller than wide, and none when that is under 150 points.
private struct WelcomeFit: Layout {
    let viewport: CGFloat
    private let gap: CGFloat = 24

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 320
        let (art, text) = heights(width, subviews)
        return CGSize(width: width, height: max(viewport, art + text + gap * 2))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let (art, text) = heights(bounds.width, subviews)
        subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.minY + gap), proposal: ProposedViewSize(width: bounds.width, height: art))
        subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.maxY - text - gap), proposal: ProposedViewSize(width: bounds.width, height: text))
    }

    private func heights(_ width: CGFloat, _ subviews: Subviews) -> (art: CGFloat, text: CGFloat) {
        let text = subviews[1].sizeThatFits(ProposedViewSize(width: width, height: nil)).height
        let room = viewport - text - gap * 2
        return (room < 150 ? 0 : min(room, width * 1.3), text)
    }
}

/// The three tilted prints, drawn on a 312 x 400 board and scaled to fit the room they get.
/// Their captions are part of the drawing, so they keep their size whatever the text size.
private struct WelcomePrints: View {
    let label: String

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .top) {
                Print(year: 2016, label: label, image: "welcome-2016-hills", width: 150, angle: -7)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.top, 36)
                Print(year: 2021, label: label, image: "welcome-2021-sail", width: 150, angle: 6)
                    .frame(maxWidth: .infinity, alignment: .trailing).padding(.top, 8)
                Print(year: 2025, label: label, image: "welcome-2025-cake", width: 176, angle: -1.5)
                    .padding(.top, 118)
            }
            .frame(width: 312, height: 400, alignment: .top)
            .scaleEffect(min(geo.size.width / 312, geo.size.height / 400))
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .dynamicTypeSize(.large)
        .accessibilityHidden(true)
    }
}
