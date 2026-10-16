import SwiftUI

/// iPhone calendar sheet. Picks a month and day, not a year, since every year shows at
/// once. Each day is shaded by how many past years have photos on it. A tap jumps there.
struct CalendarSheet: View {
    @Environment(AppStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var direction
    let pick: (MonthDay) -> Void
    @State private var month: Int
    @State private var slide: CGFloat = 0
    @State private var height: CGFloat = 400

    init(start: MonthDay, pick: @escaping (MonthDay) -> Void) {
        self.pick = pick
        _month = State(initialValue: start.month)
    }

    var body: some View {
        let today = MonthDay.today
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L.monthName(month)).font(.nunito(24, .heavy)).tight(24).foregroundStyle(Palette.ink)
                        .accessibilityAddTraits(.isHeader)
                    Text(L.s("calendar_hint")).font(.nunito(13)).foregroundStyle(Palette.muted)
                }
                Spacer(minLength: 0)
                if month != today.month || store.day != today {
                    Button { pick(today) } label: {
                        Text(L.s("today")).font(.nunito(15, .semibold)).foregroundStyle(Palette.ink)
                            .padding(.horizontal, 8).frame(height: 38)
                    }
                }
                arrow("chevron.backward", L.s("previous_month")) { turn(-1) }
                arrow("chevron.forward", L.s("next_month")) { turn(1) }
            }
            grid(today: today)
                .offset(x: slide)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 10)
                        .onChanged { g in if !reduceMotion && abs(g.translation.width) > abs(g.translation.height) { slide = g.translation.width } }
                        .onEnded { g in
                            let w = g.translation.width
                            // Right to left, the next month comes in from the left.
                            if abs(w) > 50 && abs(w) > abs(g.translation.height) { turn((w < 0) != rtl ? 1 : -1) }
                            else { withAnimation(.spring(duration: 0.4, bounce: 0)) { slide = 0 } }
                        }
                )
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 12)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = $0 }
        .sensoryFeedback(.selection, trigger: month)
        .presentationDetents([.height(height)])
        .presentationCornerRadius(36)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
    }

    private func grid(today: MonthDay) -> some View {
        let index = store.index
        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 7), spacing: 0) {
            ForEach(1...daysInMonth[month], id: \.self) { d in
                let md = MonthDay(month: month, day: d)
                let years = yearsForDay(index, md).count
                let sel = store.day == md
                let (fill, ink) = shade(years, selected: sel)
                Button { pick(md) } label: {
                    Text(L.num(d)).font(.nunito(16, .semibold)).monospacedDigit().foregroundStyle(ink)
                        .frame(width: 40, height: 40)
                        .background(fill, in: Circle())
                        .overlay(Circle().strokeBorder(md == today && !sel ? Palette.ink : .clear, lineWidth: 1.5))
                        .frame(maxWidth: .infinity, minHeight: 46)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(years == 0 ? L.s("day_no_photos", ["day": md.label])
                                    : L.s("day_photos_from", ["day": md.label, "years": L.p("years", years)]))
                .accessibilityAddTraits(sel ? .isSelected : [])
            }
        }
    }

    /// Fill and number color for a day with photos from `years` past years.
    private func shade(_ years: Int, selected: Bool) -> (Color, Color) {
        if selected { return (Palette.accent, .white) }
        switch years {
        case 0: return (.clear, Palette.faint)
        case 1: return (Palette.cal1, Palette.cal1Ink)
        case 2...3: return (Palette.cal2, Palette.cal2Ink)
        default: return (Palette.cal3, Palette.cal3Ink)
        }
    }

    private var rtl: Bool { direction == .rightToLeft }

    private func turn(_ by: Int) {
        let next = (month + by + 12) % 12
        if reduceMotion {
            withAnimation(.easeInOut(duration: 0.2)) { month = next; slide = 0 }
            return
        }
        // Slide the old month out, then the new one in from the other side.
        let out: CGFloat = (by > 0) != rtl ? -60 : 60
        withAnimation(.easeIn(duration: 0.12)) { slide = out }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
            month = next
            slide = -out
            withAnimation(.spring(duration: 0.4, bounce: 0)) { slide = 0 }
        }
    }

    private func arrow(_ icon: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 15, weight: .bold)).foregroundStyle(Palette.ink)
                .frame(width: 38, height: 38).background(Palette.fill, in: Circle())
        }
        .accessibilityLabel(label)
    }
}

/// Make a new album from the chosen photos, or add them to one that exists.
struct AlbumSheet: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let photos: [Photo]
    var created: (Album) -> Void = { _ in }
    var done: () -> Void = {}
    @State private var name = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 2) {
                Text(L.s("add_to_album")).font(.nunito(22, .bold)).foregroundStyle(Palette.ink)
                Text(L.s("n_selected", ["photos": L.p("photos", photos.count)])).font(.nunito(14)).foregroundStyle(Palette.muted)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text(L.s("new_album_name")).font(.nunito(13, .semibold)).foregroundStyle(Palette.muted2)
                HStack(spacing: 8) {
                    TextField(L.s("album_name_example"), text: $name)
                        .font(.nunito(16, .medium))
                        .focused($focused)
                        .submitLabel(.done)
                        .onSubmit(create)
                        .padding(.horizontal, 14)
                        .frame(height: 48)
                        .background(Palette.bg, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(focused ? Palette.accent : Palette.line, lineWidth: 1))
                    Pill(label: L.s("create"), height: 48, action: create)
                        .fixedSize()
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            if !store.saved.albums.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text(L.s("add_to_existing")).font(.nunito(13, .semibold)).foregroundStyle(Palette.muted2).padding(.bottom, 4)
                    ScrollView {
                        ForEach(store.saved.albums) { a in
                            Button {
                                store.add(photos, to: a.id)
                                dismiss()
                                done()
                            } label: {
                                HStack(spacing: 12) {
                                    Group {
                                        if let first = a.photos.first { PhotoImage(photo: first) } else { Palette.fill }
                                    }
                                    .frame(width: 48, height: 48).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(a.name).font(.nunito(16, .semibold)).foregroundStyle(Palette.ink).lineLimit(1)
                                        Text(L.p("photos", a.photos.count)).font(.nunito(13)).foregroundStyle(Palette.muted)
                                    }
                                    Spacer()
                                    Image(systemName: "plus.circle.fill").font(.system(size: 22)).foregroundStyle(Palette.accent)
                                }
                                .padding(6)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(RowPressStyle())
                            .accessibilityLabel(L.s("add_to_named", ["name": a.name]))
                        }
                    }
                    .frame(maxHeight: 220)
                }
            }
        }
        .padding(20)
        .presentationDetents([.medium, .large])
        .presentationCornerRadius(36)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
    }

    private func create() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        let album = store.createAlbum(trimmed, photos: photos)
        dismiss()
        done()
        created(album)
    }
}

/// Photo sources and the daily reminder. Opened from the "..." button.
struct SettingsSheet: View {
    @Environment(AppStore.self) private var store
    @Binding var showGoogle: Bool
    @Environment(\.dismiss) private var dismiss
    private let times = [(8, 0), (9, 0), (12, 0), (18, 0), (20, 0)]

    var body: some View {
        let s = store.saved
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(L.s("photo_sources")).font(.nunito(13, .bold)).foregroundStyle(Palette.muted)
                row(icon: "apple.logo", title: L.s("apple_photos"), status: L.s(s.sources.device ? "connected" : "not_connected"), on: s.sources.device) {
                    guard !s.sources.device else { return }
                    Task {
                        if PhotoLibrary.canAsk || PhotoLibrary.hasAccess {
                            if await store.connectDevice() { store.say(L.s("toast_apple_connected")) }
                        } else if let url = URL(string: UIApplication.openSettingsURLString) {
                            await UIApplication.shared.open(url)
                        }
                    }
                }
                row(icon: "photo.on.rectangle.angled", title: L.s("google_photos"),
                    status: s.sources.google ? L.s("n_picked", ["photos": L.p("photos", s.googlePhotos.count)]) : L.s("not_connected"), on: s.sources.google) {
                    dismiss()
                    showGoogle = true
                }
                Divider()
                HStack(spacing: 12) {
                    Image(systemName: "bell").foregroundStyle(Palette.ink)
                    Text(L.s("daily_reminder")).font(.nunito(16, .semibold)).foregroundStyle(Palette.ink)
                    Spacer()
                    if s.reminder.on {
                        Button {
                            let i = times.firstIndex { $0.0 == s.reminder.hour && $0.1 == s.reminder.minute } ?? 0
                            let next = times[(i + 1) % times.count]
                            store.setReminder(hour: next.0, minute: next.1)
                        } label: {
                            Text(formatTime(hour: s.reminder.hour, minute: s.reminder.minute)).font(.nunito(15, .semibold)).foregroundStyle(Palette.ink)
                                .padding(.horizontal, 12).padding(.vertical, 6).background(Palette.fill, in: Capsule())
                        }
                        .accessibilityLabel(L.s("change_reminder_time"))
                    }
                    Toggle(L.s("daily_reminder"), isOn: Binding(get: { s.reminder.on }, set: { on in
                        Task {
                            if on, !(await Reminders.requestPermission()) { return store.say(L.s("toast_notifications_off")) }
                            store.setReminder(on: on)
                        }
                    }))
                    .labelsHidden()
                    .tint(Palette.accent)
                }
                Text(L.s("reminder_settings_note")).font(.nunito(13)).foregroundStyle(Palette.muted)
                Divider()
                Text(L.s("layout")).font(.nunito(13, .bold)).foregroundStyle(Palette.muted)
                Picker(L.s("layout"), selection: Binding(get: { store.saved.layout }, set: { store.saved.layout = $0 })) {
                    Text(L.s("layout_all_years")).tag(YearLayout.mosaic)
                    Text(L.s("layout_one_year")).tag(YearLayout.strip)
                }
                .pickerStyle(.segmented)
                Divider()
                let day = store.day
                let hidden = store.isHidden(day)
                Button {
                    store.setHidden(day, !hidden)
                    if !hidden { dismiss() }
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: hidden ? "eye" : "eye.slash").font(.system(size: 17)).frame(width: 22).foregroundStyle(Palette.ink)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(L.s(hidden ? "show_day_again" : "hide_day", ["day": day.label])).font(.nunito(16, .semibold)).foregroundStyle(Palette.ink)
                            Text(L.s("hidden_note")).font(.nunito(13)).foregroundStyle(Palette.muted)
                        }
                        Spacer()
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                let others = s.hiddenDates.compactMap { MonthDay(key: $0) }.filter { $0 != day }
                    .sorted { ($0.month, $0.day) < ($1.month, $1.day) }
                if !others.isEmpty {
                    Text(L.s("hidden_dates")).font(.nunito(13, .bold)).foregroundStyle(Palette.muted)
                    ForEach(others, id: \.self) { md in
                        HStack {
                            Text(md.label).font(.nunito(16, .medium)).foregroundStyle(Palette.ink)
                            Spacer()
                            Pill(label: L.s("show"), kind: .secondary, height: 36) { store.setHidden(md, false) }.fixedSize()
                        }
                    }
                }
            }
            .padding(20)
        }
        .presentationDetents([.medium, .large])
        .presentationCornerRadius(36)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
    }

    private func row(icon: String, title: String, status: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 19)).frame(width: 22).foregroundStyle(Palette.ink)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(.nunito(16, .semibold)).foregroundStyle(Palette.ink)
                    Text(status).font(.nunito(13, .medium)).foregroundStyle(on ? Palette.muted : Palette.accent)
                }
                Spacer()
                Image(systemName: on ? "checkmark" : "plus.circle").font(.system(size: 17, weight: .bold)).foregroundStyle(on ? Palette.muted : Palette.accent)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Explains what Google allows, runs the picker, or manages the link.
struct GoogleSheet: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let connected = store.saved.sources.google
        VStack(alignment: .leading, spacing: 16) {
            Image(systemName: "photo.on.rectangle.angled").font(.system(size: 24)).foregroundStyle(Palette.ink)
                .frame(width: 52, height: 52).background(Palette.fill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            if let progress = store.googleImport {
                Text(L.s(progress.total > 0 ? "google_saving" : "google_waiting")).font(.nunito(24, .heavy)).tight(24).foregroundStyle(Palette.ink)
                Text(progress.total > 0 ? L.s("google_progress", ["done": progress.done, "photos": L.p("photos", progress.total)]) : L.s("google_waiting_body"))
                    .font(.nunito(15, .medium)).foregroundStyle(Palette.muted)
                ProgressView().tint(Palette.accent).frame(maxWidth: .infinity)
                Pill(label: L.s("cancel"), kind: .ghost, height: 48) { store.cancelGoogleImport() }
            } else {
                Text(L.s(connected ? "google_connected_title" : "google_add_title")).font(.nunito(24, .heavy)).tight(24).foregroundStyle(Palette.ink)
                Text(connected
                     ? L.s("google_connected_body", ["photos": L.p("photos", store.saved.googlePhotos.count)])
                     : L.s("google_intro_ios"))
                    .font(.nunito(15, .medium)).foregroundStyle(Palette.muted).fixedSize(horizontal: false, vertical: true)
                if GoogleConfig.isConfigured {
                    VStack(spacing: 6) {
                        Pill(label: L.s(connected ? "pick_more" : "continue_google")) { store.importFromGoogle() }
                        if connected {
                            Pill(label: L.s("disconnect"), kind: .ghost, height: 48) { store.disconnectGoogle(); dismiss() }
                        } else {
                            Pill(label: L.s("not_now"), kind: .ghost, height: 48) { dismiss() }
                        }
                    }
                } else {
                    Text(L.s("google_no_client"))
                        .font(.nunito(14)).foregroundStyle(Palette.accInk).padding(12)
                        .background(Palette.accTint, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    Pill(label: L.s("close"), kind: .ghost, height: 48) { dismiss() }
                }
            }
        }
        .padding(20)
        .presentationDetents([.medium])
        .presentationCornerRadius(36)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(store.googleImport != nil)
        .onChange(of: store.googleImport == nil) { _, idle in if idle && store.saved.sources.google { dismiss() } }
    }
}

/// What to share: a year, a selection, or an album.
struct ShareRequest: Identifiable {
    let id = UUID()
    let title: String
    let photos: [Photo]
}

/// The design's share sheet: a preview, then send through any app or save to an album in Photos.
struct ShareSheetView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let request: ShareRequest

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 14) {
                ZStack(alignment: .topLeading) {
                    ForEach(Array(Array(request.photos.prefix(3).enumerated()).reversed()), id: \.element.key) { i, p in
                        PhotoImage(photo: p)
                            .frame(width: 52, height: 52)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.surface, lineWidth: 2))
                            .shadow(color: Palette.hex(0x141416, 0.16), radius: 4, y: 2)
                            .rotationEffect(.degrees([-6, 4, -1][i]))
                            .offset(x: CGFloat(i * 6), y: CGFloat(i * 5))
                    }
                }
                .frame(width: 64, height: 64, alignment: .topLeading)
                .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(request.title).font(.nunito(18, .bold)).foregroundStyle(Palette.ink).lineLimit(2)
                    Text(L.p("photos", request.photos.count)).font(.nunito(14)).foregroundStyle(Palette.muted)
                }
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.muted2)
                        .frame(width: 36, height: 36).background(Palette.fill, in: Circle())
                }
                .accessibilityLabel(L.s("close"))
            }
            Pill(label: L.s("share"), systemImage: "square.and.arrow.up") {
                Task { await Share.photos(request.photos, title: request.title) { dismiss() } }
            }
            Divider()
            Button {
                dismiss()
                Task { await store.saveToPhotos(name: request.title, photos: request.photos) }
            } label: {
                Label(L.s("save_copies_photos"), systemImage: "square.and.arrow.down")
                    .font(.nunito(16, .medium)).foregroundStyle(Palette.ink)
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(20)
        .presentationDetents([.height(300)])
        .presentationCornerRadius(32)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
    }
}
