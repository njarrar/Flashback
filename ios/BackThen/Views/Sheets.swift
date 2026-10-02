import SwiftUI

/// iPhone calendar sheet from the design. Picks a month and day, not a year, since every
/// year shows at once. Dots mark days with photos. A tap jumps straight there.
struct CalendarSheet: View {
    @Environment(AppStore.self) private var store
    let pick: (MonthDay) -> Void
    @State private var month: Int

    init(start: MonthDay, pick: @escaping (MonthDay) -> Void) {
        self.pick = pick
        _month = State(initialValue: start.month)
    }

    var body: some View {
        let index = store.index
        let today = MonthDay.today
        VStack(spacing: 14) {
            HStack {
                arrow("chevron.left", "Previous month") { month = (month + 11) % 12 }
                Spacer()
                VStack(spacing: 2) {
                    Text(months[month]).font(.nunito(20, .bold)).foregroundStyle(Palette.ink)
                    Text("Every year at once").font(.nunito(13)).foregroundStyle(Palette.muted)
                }
                Spacer()
                arrow("chevron.right", "Next month") { month = (month + 1) % 12 }
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 4) {
                ForEach(1...daysInMonth[month], id: \.self) { d in
                    let md = MonthDay(month: month, day: d)
                    let has = hasPhotos(index, md)
                    let sel = store.day == md
                    Button { pick(md) } label: {
                        VStack(spacing: 3) {
                            Text("\(d)").font(.nunito(16, .semibold))
                                .foregroundStyle(sel ? .white : has ? Palette.ink : Palette.faint)
                            Circle().fill(has ? (sel ? .white : Palette.accent) : .clear).frame(width: 5, height: 5)
                        }
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(sel ? Palette.accent : .clear, in: Capsule())
                        .overlay(Capsule().strokeBorder(md == today && !sel ? Palette.ink : .clear, lineWidth: 1.5))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(months[month]) \(d)\(has ? ", has photos" : "")")
                    .accessibilityAddTraits(sel ? .isSelected : [])
                }
            }
            HStack {
                HStack(spacing: 6) {
                    Circle().fill(Palette.accent).frame(width: 5, height: 5)
                    Text("Has photos").font(.nunito(13)).foregroundStyle(Palette.muted)
                }
                Spacer()
                Pill(label: "Today", kind: .secondary, height: 40) { pick(today) }.fixedSize()
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 24)
        .padding(.bottom, 12)
        .presentationDetents([.height(480)])
        .presentationCornerRadius(36)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
    }

    private func arrow(_ icon: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 16, weight: .bold)).foregroundStyle(Palette.ink)
                .frame(width: 44, height: 44).background(Palette.fill, in: Circle())
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
        ScrollView {
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
                Divider()
                Text("Layout").font(.nunito(13, .bold)).foregroundStyle(Palette.muted)
                Picker("Layout", selection: Binding(get: { store.saved.layout }, set: { store.saved.layout = $0 })) {
                    Text("All years").tag(YearLayout.mosaic)
                    Text("One year at a time").tag(YearLayout.strip)
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
                            Text(hidden ? "Show \(day.label) again" : "Hide \(day.label)").font(.nunito(16, .semibold)).foregroundStyle(Palette.ink)
                            Text("Hidden dates don't come up on their own or in reminders.").font(.nunito(13)).foregroundStyle(Palette.muted)
                        }
                        Spacer()
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                let others = s.hiddenDates.compactMap { MonthDay(key: $0) }.filter { $0 != day }
                    .sorted { ($0.month, $0.day) < ($1.month, $1.day) }
                if !others.isEmpty {
                    Text("Hidden dates").font(.nunito(13, .bold)).foregroundStyle(Palette.muted)
                    ForEach(others, id: \.self) { md in
                        HStack {
                            Text(md.label).font(.nunito(16, .medium)).foregroundStyle(Palette.ink)
                            Spacer()
                            Pill(label: "Show", kind: .secondary, height: 36) { store.setHidden(md, false) }.fixedSize()
                        }
                    }
                }
            }
            .padding(20)
        }
        .presentationDetents([.medium, .large])
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
                    Text(plural(request.photos.count, "photo")).font(.nunito(14)).foregroundStyle(Palette.muted)
                }
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.muted2)
                        .frame(width: 36, height: 36).background(Palette.fill, in: Circle())
                }
                .accessibilityLabel("Close")
            }
            Pill(label: "Share", systemImage: "square.and.arrow.up") {
                Task { await Share.photos(request.photos, title: request.title) { dismiss() } }
            }
            Divider()
            Button {
                dismiss()
                Task { await store.saveToPhotos(name: request.title, photos: request.photos) }
            } label: {
                Label("Save to an album in Photos", systemImage: "square.and.arrow.down")
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
