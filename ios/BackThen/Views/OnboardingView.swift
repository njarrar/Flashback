import SwiftUI

struct OnboardingView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var denied = false
    @State private var connecting = false
    @State private var requestingReminder = false
    @State private var chosePhotos = Self.demoReminder

    private let quickHours = [8, 9, 12, 18, 20]

    var body: some View {
        let stepTransition: AnyTransition = reduceMotion
            ? .opacity
            : .asymmetric(
                insertion: .move(edge: .trailing).combined(with: .opacity),
                removal: .move(edge: .leading).combined(with: .opacity)
            )
        ZStack {
            if chosePhotos {
                reminder.transition(stepTransition)
            } else {
                welcome.transition(stepTransition)
            }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: chosePhotos)
        .onChange(of: scenePhase) { _, now in
            guard now == .active, !chosePhotos, !connecting, PhotoLibrary.hasAccess else { return }
            connecting = true
            Task {
                let ok = await store.connectDevice()
                connecting = false
                if ok {
                    denied = false
                    chosePhotos = true
                }
            }
        }
    }

    /// Debug screenshots only: start on the reminder step.
    private static var demoReminder: Bool {
        #if DEBUG
        AppStore.launchScene == "reminder"
        #else
        false
        #endif
    }

    private func stepIndicator(step: Int) -> some View {
        HStack(spacing: 6) {
            ForEach(1...2, id: \.self) { i in
                Capsule()
                    .fill(i <= step ? Palette.accent : Palette.line)
                    .frame(width: i == step ? 28 : 14, height: 4)
            }
        }
        .accessibilityHidden(true)
    }

    /// The header and buttons stay put. Between them the prints take whatever room the words
    /// leave, and step aside when large text needs it all; past that, the words scroll.
    private var welcome: some View {
        let needsSettings = denied || PhotoLibrary.isDenied
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Logo(size: 36)
                Text(L.s("app_name")).font(.nunito(22, .black)).tight(22).foregroundStyle(Palette.ink)
                Spacer()
                stepIndicator(step: 1)
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
                            if needsSettings {
                                Label(L.s("welcome_denied"), systemImage: "exclamationmark.circle")
                                    .font(.nunito(14, .medium)).foregroundStyle(Palette.accInk)
                                    .padding(12)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(Palette.accTint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                                    .padding(.top, 6)
                                    .transition(.opacity)
                            }
                        }
                        .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .scrollBounceBehavior(.basedOnSize)
            }
            VStack(spacing: 10) {
                if needsSettings {
                    Pill(label: L.s("open_settings"), systemImage: "gearshape", busy: connecting) {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                } else {
                    Pill(label: L.s("show_my_photos"), busy: connecting) {
                        guard !connecting else { return }
                        connecting = true
                        Task {
                            let ok = await store.connectDevice()
                            connecting = false
                            if ok { chosePhotos = true } else { denied = true }
                        }
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
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.25), value: needsSettings)
    }

    private var reminder: some View {
        let today = MonthDay.today
        let years = yearsForDay(store.index, today)
        let r = store.saved.reminder
        // The middle scrolls when large text no longer fits; the buttons stay.
        return VStack(spacing: 24) {
            HStack(spacing: 10) {
                Logo(size: 36)
                Text(L.s("app_name")).font(.nunito(22, .black)).tight(22).foregroundStyle(Palette.ink)
                Spacer()
                stepIndicator(step: 2)
            }
            .padding(.top, 16)
            GeometryReader { geo in
                ScrollView {
                    VStack(spacing: 24) {
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
                        .background(Palette.glass, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Palette.line))
                        .shadow(color: .black.opacity(0.08), radius: 14, y: 6)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(quickHours, id: \.self) { hour in
                                    let on = r.hour == hour && r.minute == 0
                                    Button {
                                        store.saved.reminder.hour = hour
                                        store.saved.reminder.minute = 0
                                    } label: {
                                        Text(formatTime(hour: hour, minute: 0))
                                            .font(.nunito(14, .semibold))
                                            .foregroundStyle(on ? Palette.bg : Palette.ink)
                                            .padding(.horizontal, 14)
                                            .frame(minHeight: 44)
                                            .background(on ? Palette.ink : Palette.surface, in: Capsule())
                                            .overlay(Capsule().stroke(on ? Palette.ink : Palette.line, lineWidth: 1))
                                    }
                                    .buttonStyle(PressStyle())
                                    .accessibilityAddTraits(on ? .isSelected : [])
                                }
                            }
                            .padding(.vertical, 2)
                        }

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
                Pill(label: L.s("turn_on_reminder"), busy: requestingReminder) {
                    guard !requestingReminder else { return }
                    requestingReminder = true
                    Task {
                        let ok = await Reminders.requestPermission()
                        requestingReminder = false
                        store.finishOnboarding(reminder: ok)
                    }
                }
                Pill(label: L.s("not_now"), kind: .ghost, height: 48) { store.finishOnboarding(reminder: false) }
                    .disabled(requestingReminder)
            }
        }
        .padding(.horizontal, 24).padding(.bottom, 24)
        .background(Palette.bg)
    }
}

/// Prints on top, words at the bottom of a page at least [viewport] tall. The prints get the
/// room the words leave, up to a little taller than wide, and none when that is under 104 points.
private struct WelcomeFit: Layout {
    let viewport: CGFloat
    private let gap: CGFloat = 24

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 320
        guard subviews.count >= 2 else {
            let h = subviews.first?.sizeThatFits(ProposedViewSize(width: width, height: nil)).height ?? 0
            return CGSize(width: width, height: max(viewport, h + gap * 2))
        }
        let (art, text) = heights(width, subviews)
        return CGSize(width: width, height: max(viewport, art + text + gap * 2))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count >= 2 else {
            subviews.first?.place(at: CGPoint(x: bounds.minX, y: bounds.minY + gap), proposal: ProposedViewSize(width: bounds.width, height: nil))
            return
        }
        let (art, text) = heights(bounds.width, subviews)
        subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.minY + gap), proposal: ProposedViewSize(width: bounds.width, height: art))
        subviews[1].place(at: CGPoint(x: bounds.minX, y: bounds.maxY - text - gap), proposal: ProposedViewSize(width: bounds.width, height: text))
    }

    private func heights(_ width: CGFloat, _ subviews: Subviews) -> (art: CGFloat, text: CGFloat) {
        let text = subviews[1].sizeThatFits(ProposedViewSize(width: width, height: nil)).height
        let room = viewport - text - gap * 2
        return (room < 104 ? 0 : min(room, width * 1.3), text)
    }
}

/// The three tilted prints, drawn on a 344 x 400 board and scaled to fit the room they get.
/// Extra horizontal breathing room keeps the rotated 2016 and 2021 prints from clipping.
/// Their captions are part of the drawing, so they keep their size whatever the text size.
private struct WelcomePrints: View {
    let label: String

    var body: some View {
        GeometryReader { geo in
            let s = min(geo.size.width / 344, geo.size.height / 400)
            ZStack(alignment: .top) {
                Print(year: 2016, label: label, image: "welcome-2016-hills", width: 150, angle: -7)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.leading, 14).padding(.top, 36)
                Print(year: 2021, label: label, image: "welcome-2021-sail", width: 150, angle: 6)
                    .frame(maxWidth: .infinity, alignment: .trailing).padding(.trailing, 14).padding(.top, 8)
                Print(year: 2025, label: label, image: "welcome-2025-cake", width: 176, angle: -1.5)
                    .padding(.top, 118)
            }
            .frame(width: 344, height: 400, alignment: .top)
            .scaleEffect(max(s, 0.01))
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .dynamicTypeSize(.large)
        .accessibilityHidden(true)
    }
}
