import SwiftUI

/// iPhone calendar sheet. Picks a month and day, not a year, since every year shows at
/// once. Each day is shaded by how many past years have photos on it. A tap jumps there.
struct CalendarSheet: View {
    @Environment(AppStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
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
                    Text(months[month]).font(.nunito(24, .heavy)).tight(24).foregroundStyle(Palette.ink)
                        .accessibilityAddTraits(.isHeader)
                    Text("Darker days have photos from more years").font(.nunito(13)).foregroundStyle(Palette.muted)
                }
                Spacer(minLength: 0)
                if month != today.month || store.day != today {
                    Button { pick(today) } label: {
                        Text("Today").font(.nunito(15, .semibold)).foregroundStyle(Palette.ink)
                            .padding(.horizontal, 8).frame(height: 38)
                    }
                }
                arrow("chevron.left", "Previous month") { turn(-1) }
                arrow("chevron.right", "Next month") { turn(1) }
            }
            grid(today: today)
                .offset(x: slide)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 10)
                        .onChanged { g in if !reduceMotion && abs(g.translation.width) > abs(g.translation.height) { slide = g.translation.width } }
                        .onEnded { g in
                            let w = g.translation.width
                            if abs(w) > 50 && abs(w) > abs(g.translation.height) { turn(w < 0 ? 1 : -1) }
                            else { withAnimation(.spring(duration: 0.4, bounce: 0)) { slide = 0 } }
                        }
                )
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 12)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = $0 }
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
                    Text("\(d)").font(.nunito(16, .semibold)).monospacedDigit().foregroundStyle(ink)
                        .frame(width: 40, height: 40)
                        .background(fill, in: Circle())
                        .overlay(Circle().strokeBorder(md == today && !sel ? Palette.ink : .clear, lineWidth: 1.5))
                        .frame(maxWidth: .infinity, minHeight: 46)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(months[month]) \(d), \(years == 0 ? "no photos" : "photos from \(plural(years, "year"))")")
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

    private func turn(_ by: Int) {
        let next = (month + by + 12) % 12
        if reduceMotion {
            withAnimation(.easeInOut(duration: 0.2)) { month = next; slide = 0 }
            return
        }
        // Slide the old month out, then the new one in from the other side.
        withAnimation(.easeIn(duration: 0.12)) { slide = by > 0 ? -60 : 60 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
            month = next
            slide = by > 0 ? 60 : -60
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
                Text("Add to album").font(.nunito(22, .bold)).foregroundStyle(Palette.ink)
                Text("\(plural(photos.count, "photo")) selected").font(.nunito(14)).foregroundStyle(Palette.muted)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text("New album name").font(.nunito(13, .semibold)).foregroundStyle(Palette.muted2)
                HStack(spacing: 8) {
                    TextField("e.g. Fall in the city", text: $name)
                        .font(.nunito(16, .medium))
                        .focused($focused)
                        .submitLabel(.done)
                        .onSubmit(create)
                        .padding(.horizontal, 14)
                        .frame(height: 48)
                        .background(Palette.bg, in: RoundedRectangle(cornerRadius: 14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(focused ? Palette.accent : Palette.line, lineWidth: 1))
                    Pill(label: "Create", height: 48, action: create)
                        .fixedSize()
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            if !store.saved.albums.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Or add to an existing album").font(.nunito(13, .semibold)).foregroundStyle(Palette.muted2).padding(.bottom, 4)
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
                                    .frame(width: 48, height: 48).clipShape(RoundedRectangle(cornerRadius: 10))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(a.name).font(.nunito(16, .semibold)).foregroundStyle(Palette.ink).lineLimit(1)
                                        Text(plural(a.photos.count, "photo")).font(.nunito(13)).foregroundStyle(Palette.muted)
                                    }
                                    Spacer()
                                    Image(systemName: "plus.circle.fill").font(.system(size: 22)).foregroundStyle(Palette.accent)
                                }
                                .padding(6)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Add to \(a.name)")
                        }
                    }
                    .frame(maxHeight: 220)
                }
            }
        }
        .padding(20)
        .presentationDetents([.medium, .large])
        .presentationCornerRadius(32)
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
        VStack(alignment: .leading, spacing: 14) {
            Text("Photo sources").font(.nunito(13, .bold)).foregroundStyle(Palette.muted)
            row(icon: "apple.logo", title: "Apple Photos", status: s.sources.device ? "Connected" : "Not connected", on: s.sources.device) {
                guard !s.sources.device else { return }
                Task {
                    if PhotoLibrary.canAsk || PhotoLibrary.hasAccess {
                        if await store.connectDevice() { store.say("Apple Photos connected") }
                    } else if let url = URL(string: UIApplication.openSettingsURLString) {
                        await UIApplication.shared.open(url)
                    }
                }
            }
            row(icon: "photo.on.rectangle.angled", title: "Google Photos",
                status: s.sources.google ? "\(plural(s.googlePhotos.count, "photo")) picked" : "Not connected", on: s.sources.google) {
                dismiss()
                showGoogle = true
            }
            Divider()
            HStack(spacing: 12) {
                Image(systemName: "bell").foregroundStyle(Palette.ink)
                Text("Daily reminder").font(.nunito(16, .semibold)).foregroundStyle(Palette.ink)
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
                    .accessibilityLabel("Change reminder time")
                }
                Toggle("Daily reminder", isOn: Binding(get: { s.reminder.on }, set: { on in
                    Task {
                        if on, !(await Reminders.requestPermission()) { return store.say("Notifications are off in Settings") }
                        store.setReminder(on: on)
                    }
                }))
                .labelsHidden()
                .tint(Palette.accent)
            }
            Text("Only on days that have photos from past years. Tap the time to change it.").font(.nunito(13)).foregroundStyle(Palette.muted)
        }
        .padding(20)
        .presentationDetents([.height(340)])
        .presentationCornerRadius(32)
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
                .frame(width: 52, height: 52).background(Palette.fill, in: RoundedRectangle(cornerRadius: 16))
            if let progress = store.googleImport {
                Text(progress.total > 0 ? "Saving your picks" : "Waiting for Google Photos").font(.nunito(24, .heavy)).tight(24).foregroundStyle(Palette.ink)
                Text(progress.total > 0 ? "\(progress.done) of \(progress.total) photos" : "Pick photos in Google Photos, tap Done, then come back here.")
                    .font(.nunito(15, .medium)).foregroundStyle(Palette.muted)
                ProgressView().tint(Palette.accent).frame(maxWidth: .infinity)
                Pill(label: "Cancel", kind: .ghost, height: 48) { store.cancelGoogleImport() }
            } else {
                Text(connected ? "Google Photos is connected" : "Add Google Photos").font(.nunito(24, .heavy)).tight(24).foregroundStyle(Palette.ink)
                Text(connected
                     ? "\(plural(store.saved.googlePhotos.count, "photo")) picked. Pick more any time."
                     : "Google only lets apps see the photos you pick. Choose the ones you want Back Then to bring back, such as a whole year or a trip. Copies of shots already in Apple Photos show once.")
                    .font(.nunito(15, .medium)).foregroundStyle(Palette.muted).fixedSize(horizontal: false, vertical: true)
                if GoogleConfig.isConfigured {
                    VStack(spacing: 6) {
                        Pill(label: connected ? "Pick more photos" : "Continue with Google") { store.importFromGoogle() }
                        if connected {
                            Pill(label: "Disconnect", kind: .ghost, height: 48) { store.disconnectGoogle(); dismiss() }
                        } else {
                            Pill(label: "Not now", kind: .ghost, height: 48) { dismiss() }
                        }
                    }
                } else {
                    Text("This build has no Google client ID yet. See the README to add one.")
                        .font(.nunito(14)).foregroundStyle(Palette.accInk).padding(12)
                        .background(Palette.accTint, in: RoundedRectangle(cornerRadius: 14))
                    Pill(label: "Close", kind: .ghost, height: 48) { dismiss() }
                }
            }
        }
        .padding(20)
        .presentationDetents([.medium])
        .presentationCornerRadius(32)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(store.googleImport != nil)
        .onChange(of: store.googleImport == nil) { _, idle in if idle && store.saved.sources.google { dismiss() } }
    }
}
