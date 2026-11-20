import CoreLocation
import SwiftUI
import UserNotifications

/// iPhone calendar sheet. Picks a month and day, not a year, since every year shows at
/// once. Each day with photos shows a tiny copy of one of them. Any day with past-year photos can be picked.
struct CalendarSheet: View {
    @Environment(AppStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var direction
    let pick: (MonthDay) -> Void
    @State private var month: Int
    @State private var slide: CGFloat = 0
    @State private var height: CGFloat = 410
    @State private var turnTask: Task<Void, Never>?
    /// The month a slide is on its way to, so a second quick tap counts from there.
    @State private var target: Int?

    init(start: MonthDay, pick: @escaping (MonthDay) -> Void) {
        self.pick = pick
        _month = State(initialValue: start.month)
    }

    var body: some View {
        let today = MonthDay.today
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .center, spacing: 8) {
                Menu {
                    ForEach(0..<12, id: \.self) { m in
                        let photoDays = (1...daysInMonth[m]).filter { hasPhotos(store.index, MonthDay(month: m, day: $0)) }.count
                        Button {
                            turnTask?.cancel()
                            target = nil
                            slide = 0
                            month = m
                        } label: {
                            let label = photoDays > 0 ? "\(L.monthName(m)) (\(L.num(photoDays)))" : L.monthName(m)
                            if m == month {
                                Label(label, systemImage: "checkmark")
                            } else {
                                Text(label)
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Text(L.monthName(month)).font(.nunito(24, .heavy)).tight(24).foregroundStyle(Palette.ink)
                        Image(systemName: "chevron.down").font(.system(size: 13, weight: .bold)).foregroundStyle(Palette.accent)
                    }
                    .frame(minHeight: 44).contentShape(Rectangle())
                }
                .accessibilityAddTraits(.isHeader)

                Spacer(minLength: 0)
                if month != today.month || store.day != today {
                    Button { pick(today) } label: {
                        Text(L.s("today")).font(.nunito(15, .semibold)).foregroundStyle(Palette.ink)
                            .padding(.horizontal, 14).frame(minHeight: 36)
                            .background(Palette.fill, in: Capsule())
                            .frame(minHeight: 44).contentShape(Rectangle())
                    }
                }
                arrow("chevron.backward", L.s("previous_month")) { turn(-1) }
                arrow("chevron.forward", L.s("next_month")) { turn(1) }
            }
            grid(today: today)
                .frame(minHeight: 250, alignment: .top)
                // SwiftUI mirrors `.offset(x:)` in RTL; negate so drag and month turns match screen direction.
                .offset(x: rtl ? -slide : slide)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 10)
                        .onChanged { g in
                            if !reduceMotion && abs(g.translation.width) > abs(g.translation.height) {
                                slide = g.translation.width
                            }
                        }
                        .onEnded { g in
                            let w = g.translation.width
                            // Right to left, the next month comes in from the left.
                            if abs(w) > 50 && abs(w) > abs(g.translation.height) { turn((w < 0) != rtl ? 1 : -1) }
                            else { withAnimation(reduceMotion ? nil : .spring(duration: 0.4, bounce: 0)) { slide = 0 } }
                        }
                )
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 12)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = max($0, 380) }
        .sensoryFeedback(.selection, trigger: month)
        .presentationDetents([.height(height)])
        .presentationCornerRadius(36)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
    }

    private func grid(today: MonthDay) -> some View {
        let index = store.index
        let days = (1...daysInMonth[month]).map { MonthDay(month: month, day: $0) }
        return LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 7), spacing: 0) {
            ForEach(days, id: \.key) { md in
                let sel = store.day == md
                let future = md.isAfter(today)
                // Days after today stay locked and faint, as in 1.7.
                let photos = !future && hasPhotos(index, md)
                let hidden = store.isHidden(md)
                let disabled = future
                Button { pick(md) } label: {
                    CalendarDay(md: md, photos: photos, hidden: hidden, selected: sel, today: md == today, future: future)
                        .frame(maxWidth: .infinity, minHeight: 50)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(disabled)
                .accessibilityLabel(label(md, index, today: today))
                .accessibilityAddTraits(sel ? .isSelected : [])
            }
        }
    }

    /// "October 16, 12 photos from 3 years", "October 16 is hidden", or "October 16, no photos".
    private func label(_ md: MonthDay, _ index: DayIndex, today: MonthDay) -> String {
        if store.isHidden(md) { return L.s("day_is_hidden", ["day": md.label]) }
        let years = yearsForDay(index, md).count
        if years == 0 {
            return md.isAfter(today) ? L.s("day_upcoming", ["day": md.label]) : L.s("day_no_photos", ["day": md.label])
        }
        return L.s("day_photos_from", ["day": md.label, "photos": L.p("photos", photoCount(index, md)), "years": L.p("years", years)])
    }

    private var rtl: Bool { direction == .rightToLeft }

    private func turn(_ by: Int) {
        // A tap mid-slide takes over from the one before, so two quick taps move two months.
        turnTask?.cancel()
        let next = ((target ?? month) + by + 12) % 12
        if reduceMotion {
            target = nil
            withAnimation(.easeInOut(duration: 0.2)) { month = next; slide = 0 }
            return
        }
        target = next
        // Slide the old month out in screen coordinates, then the new one in from the other side.
        let out: CGFloat = (by > 0) != rtl ? -60 : 60
        withAnimation(.easeIn(duration: 0.12)) { slide = out }
        turnTask = Task {
            try? await Task.sleep(for: .milliseconds(120))
            guard !Task.isCancelled else { return }
            target = nil
            month = next
            slide = -out
            withAnimation(.spring(duration: 0.4, bounce: 0)) { slide = 0 }
        }
    }

    private func arrow(_ icon: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 15, weight: .bold)).foregroundStyle(Palette.ink)
                .frame(width: 44, height: 44).background(Palette.fill, in: Circle())
        }
        .accessibilityLabel(label)
    }
}

/// One day in the calendar: a tiny copy of one of its photos, dimmed, with the number on top, or a plain
/// number. Hidden days display a micro eye.slash indicator.
private struct CalendarDay: View {
    @Environment(AppStore.self) private var store
    let md: MonthDay
    let photos: Bool
    let hidden: Bool
    let selected: Bool
    let today: Bool
    let future: Bool
    @State private var cover: Photo?

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 11, style: .continuous)
        let hasCover = photos && cover != nil
        Text(L.num(md.day))
            .font(.nunito(16, photos ? .heavy : .semibold, maxSize: 20))
            .monospacedDigit()
            .lineLimit(1)
            .minimumScaleFactor(0.75)
            .foregroundStyle(hasCover ? .white : future && !photos ? Palette.muted.opacity(0.65) : photos ? Palette.ink : Palette.muted)
            .shadow(color: hasCover ? .black.opacity(0.45) : .clear, radius: 0.5, y: 0.5)
            .frame(width: 40, height: 40)
            .background {
                if photos {
                    ZStack {
                        Palette.fill
                        // A dark wash under the number, so it reads on bright photos too.
                        if let cover { PhotoImage(photo: cover); Palette.dayWash }
                    }
                    .clipShape(shape)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if hidden {
                    Image(systemName: "eye.slash")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundStyle(Palette.muted)
                        .padding(3)
                        .accessibilityHidden(true)
                }
            }
            // The picked day gets a coral ring, set off from the photo by a thin gap. Today gets a thin ink ring.
            .overlay {
                if selected {
                    RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Palette.accent, lineWidth: 2.5).padding(-4)
                } else if today {
                    shape.strokeBorder(Palette.ink, lineWidth: 1.5)
                }
            }
            .dynamicTypeSize(...DynamicTypeSize.xxLarge)
            .task(id: "\(md.key)-\(photos)-\(store.indexVersion)") {
                cover = photos ? await store.cover(for: md) : nil
            }
    }
}

/// Make a new album from the chosen photos, or add them to one that exists.
struct AlbumSheet: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var textSize
    let photos: [Photo]
    var created: (Album) -> Void = { _ in }
    var done: () -> Void = {}
    @State private var name = ""
    @FocusState private var focused: Bool

    var body: some View {
        let inputLayout = textSize >= .xxLarge
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 10))
            : AnyLayout(HStackLayout(spacing: 8))
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 12) {
                    if !photos.isEmpty {
                        ZStack(alignment: .topLeading) {
                            ForEach(Array(Array(photos.prefix(3).enumerated()).reversed()), id: \.element.key) { i, p in
                                PhotoImage(photo: p)
                                    .frame(width: 40, height: 40)
                                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                                    .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(Palette.surface, lineWidth: 1.5))
                                    .offset(x: CGFloat(i * 5), y: CGFloat(i * 4))
                            }
                        }
                        .frame(width: 52, height: 50, alignment: .topLeading)
                        .environment(\.layoutDirection, .leftToRight)
                        .accessibilityHidden(true)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L.s("add_to_album")).font(.nunito(22, .bold)).foregroundStyle(Palette.ink)
                            .accessibilityAddTraits(.isHeader)
                        Text(L.s("n_selected", ["photos": L.p("photos", photos.count)])).font(.nunito(14)).foregroundStyle(Palette.muted)
                    }
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.muted2)
                            .frame(width: 36, height: 36).background(Palette.fill, in: Circle())
                            .frame(width: 44, height: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel(L.s("close"))
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text(L.s("new_album_name")).font(.nunito(13, .semibold)).foregroundStyle(Palette.muted2)
                    inputLayout {
                        TextField(L.s("album_name_example"), text: $name)
                            .font(.nunito(16, .medium))
                            .focused($focused)
                            .submitLabel(.done)
                            .onSubmit(create)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .frame(minHeight: 48)
                            .background(Palette.bg, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(focused ? Palette.accent : Palette.line, lineWidth: 1))
                        Pill(label: L.s("create"), height: 48, wide: textSize >= .xxLarge ? true : false, action: create)
                            .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }

                if !store.saved.albums.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(L.s("add_to_existing")).font(.nunito(13, .semibold)).foregroundStyle(Palette.muted2).padding(.bottom, 4)
                        ForEach(store.saved.albums) { a in
                            Button {
                                let added = store.add(photos, to: a.id)
                                if added == 0 {
                                    store.showToast(L.s("already_in_album", ["name": a.name]), kind: .info, undoLabel: nil, undo: nil)
                                }
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
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(RowPressStyle())
                            .accessibilityLabel(L.s("add_to_named", ["name": a.name]))
                        }
                    }
                }
            }
            .padding(20)
        }
        .scrollBounceBehavior(.basedOnSize)
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

/// Photo sources, daily reminder, places, layout, hidden dates, and licenses. Opened from the "..." button.
struct SettingsSheet: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var notificationsDenied = false
    @State private var locationDenied = false
    @State private var cityList: CityList?
    @State private var showLicenses = false

    private var reminderTimeBinding: Binding<Date> {
        Binding(
            get: {
                let r = store.saved.reminder
                var c = DateComponents(year: 2024, month: 1, day: 1, hour: r.hour, minute: r.minute)
                c.calendar = Calendar.app
                return c.date ?? Date()
            },
            set: { date in
                let c = Calendar.app.dateComponents([.hour, .minute], from: date)
                if let h = c.hour, let m = c.minute {
                    store.setReminder(hour: h, minute: m)
                }
            }
        )
    }

    var body: some View {
        let s = store.saved
        let isLimited = PhotoLibrary.isLimited
        let isDenied = PhotoLibrary.isDenied || store.photoAccessRevoked
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(L.s("settings")).font(.nunito(24, .heavy)).tight(24).foregroundStyle(Palette.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.muted2)
                            .frame(width: 36, height: 36).background(Palette.fill, in: Circle())
                            .frame(width: 44, height: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel(L.s("close"))
                }

                Text(L.s("photo_sources")).font(.nunito(13, .bold)).foregroundStyle(Palette.muted)
                let photoStatus = isDenied ? L.s("not_connected") : isLimited ? L.s("limited_access") : L.s(s.sources.device ? "connected" : "not_connected")
                row(
                    icon: "iphone",
                    title: L.s("phone_photos"),
                    status: photoStatus,
                    on: s.sources.device && !isDenied && !isLimited,
                    trailingIcon: isLimited ? "photo.badge.plus" : nil
                ) {
                    if isLimited {
                        PhotoLibrary.presentLimitedPicker()
                    } else if isDenied {
                        openSettings()
                    } else if !s.sources.device {
                        Task {
                            if PhotoLibrary.canAsk || PhotoLibrary.hasAccess {
                                if await store.connectDevice() { store.say(L.s("toast_apple_connected")) }
                            } else {
                                openSettings()
                            }
                        }
                    }
                }
                if isLimited {
                    Text(L.s("limited_access_note")).font(.nunito(13)).foregroundStyle(Palette.muted)
                    HStack(spacing: 8) {
                        Pill(label: L.s("manage_photos"), kind: .secondary, height: 44) {
                            PhotoLibrary.presentLimitedPicker()
                        }
                        .fixedSize()
                        Pill(label: L.s("open_settings"), kind: .ghost, height: 44) {
                            openSettings()
                        }
                        .fixedSize()
                    }
                } else if isDenied {
                    Pill(label: L.s("open_settings"), systemImage: "gearshape", kind: .secondary, height: 44, action: openSettings)
                        .fixedSize()
                }

                Divider()
                HStack(spacing: 12) {
                    Image(systemName: "bell").foregroundStyle(Palette.ink)
                    Text(L.s("daily_reminder")).font(.nunito(16, .semibold)).foregroundStyle(Palette.ink)
                    Spacer()
                    if s.reminder.on {
                        DatePicker(
                            L.s("change_reminder_time"),
                            selection: reminderTimeBinding,
                            displayedComponents: .hourAndMinute
                        )
                        .labelsHidden()
                        .datePickerStyle(.compact)
                        .tint(Palette.accent)
                    }
                    Toggle(L.s("daily_reminder"), isOn: Binding(get: { s.reminder.on }, set: { on in
                        Task {
                            if on, !(await Reminders.requestPermission()) {
                                notificationsDenied = true
                                store.showToast(L.s("toast_notifications_off"), kind: .error, undoLabel: nil, undo: nil)
                                return
                            }
                            notificationsDenied = false
                            store.setReminder(on: on)
                        }
                    }))
                    .labelsHidden()
                    .tint(Palette.accent)
                }
                .frame(minHeight: 44)
                Text(L.s("reminder_settings_note")).font(.nunito(13)).foregroundStyle(Palette.muted)
                if notificationsDenied && !s.reminder.on {
                    Pill(label: L.s("open_settings"), systemImage: "gearshape", kind: .secondary, height: 44, action: openSettings)
                        .fixedSize()
                }

                Divider()
                if CityList.available {
                    HStack(spacing: 12) {
                        Image(systemName: "mappin.and.ellipse").foregroundStyle(Palette.ink)
                        Text(L.s("places_title")).font(.nunito(16, .semibold)).foregroundStyle(Palette.ink)
                        Spacer()
                        Toggle(L.s("places_title"), isOn: Binding(get: { s.places.on }, set: { on in
                            Task {
                                await store.setPlaces(on)
                                let status = CLLocationManager().authorizationStatus
                                locationDenied = (status == .denied || status == .restricted)
                            }
                        }))
                        .labelsHidden()
                        .tint(Palette.accent)
                    }
                    .frame(minHeight: 44)
                    if store.placesScanning {
                        HStack(spacing: 8) {
                            ProgressView().tint(Palette.muted)
                            Text(L.s("places_scanning")).font(.nunito(13, .medium)).foregroundStyle(Palette.muted)
                        }
                    }
                    if s.places.on && !s.placeIndex.isEmpty, let cityList {
                        homeCityRow(s: s, cities: cityList)
                    }
                    Text(L.s("places_note")).font(.nunito(13)).foregroundStyle(Palette.muted)
                    if locationDenied && !s.places.on {
                        Pill(label: L.s("open_settings"), systemImage: "gearshape", kind: .secondary, height: 44, action: openSettings)
                            .fixedSize()
                    }
                    Divider()
                }

                Text(L.s("layout")).font(.nunito(13, .bold)).foregroundStyle(Palette.muted)
                // Stacked, not side by side: three long names don't fit in one row.
                VStack(spacing: 4) {
                    ForEach([(YearLayout.mosaic, "layout_all_years"), (.strip, "layout_one_year"), (.cards, "layout_cards")], id: \.0) { layout, key in
                        let on = store.saved.layout == layout
                        Button { store.saved.layout = layout } label: {
                            Text(L.s(key)).font(.nunito(15, .semibold)).foregroundStyle(on ? Palette.ink : Palette.muted)
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(on ? Palette.surface : .clear, in: Capsule())
                                .contentShape(Capsule())
                        }
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
                .padding(4)
                .background(Palette.fill, in: RoundedRectangle(cornerRadius: 24, style: .continuous))

                Divider()
                let day = store.day
                let hidden = store.isHidden(day)
                Button {
                    store.setHidden(day, !hidden)
                    if !hidden {
                        store.showToast(
                            L.s("toast_day_hidden", ["day": day.shortLabel]),
                            kind: .info,
                            undoLabel: L.s("undo"),
                            undo: { store.setHidden(day, false) }
                        )
                        dismiss()
                    }
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: hidden ? "eye" : "eye.slash").font(.system(size: 17)).frame(width: 22).foregroundStyle(Palette.ink)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(L.s(hidden ? "show_day_again" : "hide_day", ["day": day.label])).font(.nunito(16, .semibold)).foregroundStyle(Palette.ink)
                            Text(L.s("hidden_note")).font(.nunito(13)).foregroundStyle(Palette.muted)
                        }
                        Spacer()
                    }
                    .padding(.vertical, 6)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(RowPressStyle())

                let others = s.hiddenDates.compactMap { MonthDay(key: $0) }.filter { $0 != day }
                    .sorted { ($0.month, $0.day) < ($1.month, $1.day) }
                if !others.isEmpty {
                    Text(L.s("hidden_dates")).font(.nunito(13, .bold)).foregroundStyle(Palette.muted)
                    ForEach(others, id: \.self) { md in
                        HStack {
                            Text(md.label).font(.nunito(16, .medium)).foregroundStyle(Palette.ink)
                            Spacer()
                            Pill(label: L.s("show"), kind: .secondary, height: 44) { store.setHidden(md, false) }.fixedSize()
                        }
                        .frame(minHeight: 44)
                    }
                }

                Divider()
                Button { showLicenses = true } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "doc.text").font(.system(size: 17)).frame(width: 22).foregroundStyle(Palette.muted)
                        Text(L.s("licenses")).font(.nunito(15, .semibold)).foregroundStyle(Palette.ink)
                        Spacer()
                        Image(systemName: "chevron.forward").font(.system(size: 13, weight: .bold)).foregroundStyle(Palette.faint)
                    }
                    .padding(.vertical, 6)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(RowPressStyle())
            }
            .padding(20)
        }
        .presentationDetents([.medium, .large])
        .presentationCornerRadius(36)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
        .task {
            let notif = await UNUserNotificationCenter.current().notificationSettings()
            notificationsDenied = notif.authorizationStatus == .denied
            let loc = CLLocationManager().authorizationStatus
            locationDenied = (loc == .denied || loc == .restricted)
            if CityList.available {
                cityList = await CityList.load()
            }
        }
        .alert(L.s("licenses"), isPresented: $showLicenses) {
            Button(L.s("close"), role: .cancel) {}
        } message: {
            Text("GeoNames: CC BY 4.0\nNunito & Tajawal: SIL Open Font License 1.1")
        }
    }

    @ViewBuilder
    private func homeCityRow(s: SavedState, cities: CityList) -> some View {
        let activeID = s.customHomeCity ?? homeCity(s.placeIndex)
        let activeName = activeID.flatMap { $0 < cities.cities.count ? cities.cities[$0].name : nil }
        let visited = s.placeIndex
            .sorted { $0.value.count > $1.value.count }
            .prefix(25)
            .compactMap { id, _ in id < cities.cities.count ? cities.cities[id] : nil }
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(L.s("home_city")).font(.nunito(14, .semibold)).foregroundStyle(Palette.muted)
                if let activeName {
                    Text(activeName).font(.nunito(15, .semibold)).foregroundStyle(Palette.ink)
                }
            }
            Spacer()
            if !visited.isEmpty {
                Menu {
                    ForEach(visited, id: \.id) { city in
                        Button {
                            store.setHomeCity(city.id)
                        } label: {
                            if city.id == activeID {
                                Label(city.name, systemImage: "checkmark")
                            } else {
                                Text(city.name)
                            }
                        }
                    }
                } label: {
                    Text(L.s("change"))
                        .font(.nunito(14, .semibold))
                        .foregroundStyle(Palette.ink)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 36)
                        .background(Palette.fill, in: Capsule())
                        .frame(minHeight: 44).contentShape(Rectangle())
                }
            }
        }
        .frame(minHeight: 44)
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }

    private func row(icon: String, title: String, status: String, on: Bool, trailingIcon: String? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 19)).frame(width: 22).foregroundStyle(Palette.ink)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title).font(.nunito(16, .semibold)).foregroundStyle(Palette.ink)
                    Text(status).font(.nunito(13, .medium)).foregroundStyle(on ? Palette.muted : Palette.accent)
                }
                Spacer()
                Image(systemName: trailingIcon ?? (on ? "checkmark" : "plus.circle"))
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(on ? Palette.muted : Palette.accent)
            }
            .padding(.vertical, 6)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(RowPressStyle())
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
    @State private var height: CGFloat = 310
    @State private var sharing = false
    @State private var saving = false

    var body: some View {
        ScrollView {
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
                    .environment(\.layoutDirection, .leftToRight)
                    .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(request.title).font(.nunito(18, .bold)).foregroundStyle(Palette.ink).fixedSize(horizontal: false, vertical: true)
                        Text(L.p("photos", request.photos.count)).font(.nunito(14)).foregroundStyle(Palette.muted)
                    }
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.muted2)
                            .frame(width: 36, height: 36).background(Palette.fill, in: Circle())
                            .frame(width: 44, height: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel(L.s("close"))
                }
                // Big photos take a moment to get ready; the spinner shows it's working.
                Pill(label: L.s("share"), systemImage: "square.and.arrow.up", busy: sharing) {
                    guard !sharing else { return }
                    sharing = true
                    Task {
                        let ok = await Share.photos(request.photos, title: request.title) { dismiss() }
                        sharing = false
                        if !ok {
                            store.showToast(L.s("share_failed"), kind: .error, undoLabel: nil, undo: nil)
                        }
                    }
                }
                .disabled(saving)
                Divider()
                Button {
                    guard !saving else { return }
                    saving = true
                    Task {
                        await store.saveToPhotos(name: request.title, photos: request.photos)
                        saving = false
                        dismiss()
                    }
                } label: {
                    HStack(spacing: 12) {
                        if saving {
                            ProgressView().tint(Palette.ink).frame(width: 22)
                        } else {
                            Image(systemName: "square.and.arrow.down").font(.system(size: 17)).frame(width: 22)
                        }
                        Text(L.s("save_copies_photos")).font(.nunito(16, .medium))
                    }
                    .foregroundStyle(Palette.ink)
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(RowPressStyle())
                .disabled(saving || sharing)
            }
            .padding(20)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height = max($0, 300) }
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.height(min(height, 560)), .large])
        .presentationCornerRadius(32)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
    }
}
