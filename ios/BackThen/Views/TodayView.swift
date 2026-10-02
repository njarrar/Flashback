import SwiftUI

struct TodayView: View {
    @Environment(AppStore.self) private var store
    @State private var result: (day: MonthDay, groups: [YearGroup])?
    @State private var calendar = false
    @State private var settings = false
    @State private var google = false
    @State private var selecting = false
    @State private var picked: [Photo] = []
    @State private var albumFor: [Photo]?
    @State private var shareFor: ShareRequest?
    @State private var stripPick: Int?
    @Binding var hideTabs: Bool

    var body: some View {
        let day = store.day
        let groups = result?.day == day ? result?.groups : nil
        let today = MonthDay.today
        let total = groups?.reduce(0) { $0 + $1.photos.count } ?? 0
        let hidden = store.isHidden(day)
        let summary = hidden ? "You hid this date" : groups == nil ? "Looking through your library…"
            : groups!.isEmpty ? (store.indexing ? "Looking through your library…" : "No photos on this date")
            : "\(plural(total, "photo")) from \(plural(groups!.count, "year"))"
        let selection = Selection(on: selecting, keys: Set(picked.map(\.key))) { p in
            if let i = picked.firstIndex(where: { $0.key == p.key }) { picked.remove(at: i) } else { picked.append(p) }
        }

        ScrollViewReader { scroll in
            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Spacer()
                        GlassButton(accessibility: "Options", action: { settings = true }) {
                            Image(systemName: "ellipsis").font(.system(size: 17, weight: .bold))
                        }
                        GlassButton(accessibility: selecting ? "Cancel" : "Select", action: toggleSelect) {
                            Text(selecting ? "Cancel" : "Select").font(.nunito(15, .semibold))
                        }
                        GlassButton(accessibility: "Pick a day", action: { calendar = true }) {
                            Image(systemName: "calendar").font(.system(size: 17))
                        }
                    }
                    .id("top")
                    Button { calendar = true } label: {
                        HStack(spacing: 8) {
                            Text(day.label).font(.nunito(34, .bold)).tight(34).foregroundStyle(Palette.ink)
                            Image(systemName: "chevron.down").font(.system(size: 16, weight: .bold)).foregroundStyle(Palette.accent)
                        }
                    }
                    .padding(.top, 6)
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityHint("Pick another day")
                    Text(summary).font(.nunito(15)).foregroundStyle(Palette.muted)
                    if day != today {
                        Pill(label: "Back to today", systemImage: "arrow.uturn.left", kind: .tint, height: 34) { go(today) }
                            .fixedSize().padding(.top, 8)
                    }
                }
                .padding(.horizontal, 20)

                if hidden {
                    HiddenDay(day: day) { store.setHidden(day, false) }
                } else if let groups {
                    if groups.isEmpty {
                        EmptyDay(day: day, noSources: !store.saved.sources.device && !store.saved.sources.google,
                                 near: nearbyDays(store.index, day), go: go, connect: { settings = true })
                    } else if store.saved.layout == .strip {
                        let years = groups.map(\.year)
                        let at = stripPick.flatMap { years.firstIndex(of: $0) } ?? 0
                        let g = groups[at]
                        LazyVStack(spacing: 0, pinnedViews: .sectionHeaders) {
                            Section {
                                StripYear(group: g, day: day, selection: selection,
                                          newer: at > 0 ? years[at - 1] : nil, older: at + 1 < years.count ? years[at + 1] : nil,
                                          share: { shareFor = ShareRequest(title: "\(day.label), \(g.year)", photos: g.photos) },
                                          pick: { stripPick = $0 })
                            } header: {
                                YearPills(years: years, selected: g.year) { stripPick = $0 }
                            }
                        }
                    } else {
                        LazyVStack(spacing: 0) {
                            ForEach(groups) { g in
                                YearSection(group: g, day: day, selection: selection) {
                                    shareFor = ShareRequest(title: "\(day.label), \(g.year)", photos: g.photos)
                                }
                            }
                        }
                    }
                } else {
                    Skeleton()
                }
                Color.clear.frame(height: 120)
            }
            .refreshable { await store.refreshIndex() }
            .onChange(of: store.day) { _, _ in stripPick = nil; scroll.scrollTo("top") }
        }
        .background(Palette.bg)
        .task(id: "\(day.key)-\(store.index.count)-\(store.saved.googlePhotos.count)-\(store.saved.sources.device)") {
            let groups = await store.photos(for: day)
            result = (day, groups)
        }
        .overlay(alignment: .bottom) {
            if selecting {
                SelectBar(count: picked.count, share: {
                    shareFor = ShareRequest(title: plural(picked.count, "photo"), photos: picked)
                }, album: { albumFor = picked })
                .padding(.bottom, 4)
            }
        }
        .sheet(isPresented: $calendar) {
            CalendarSheet(start: store.day) { md in go(md) }
        }
        .sheet(isPresented: $settings) { SettingsSheet(showGoogle: $google) }
        .sheet(isPresented: $google) { GoogleSheet() }
        .sheet(item: $shareFor) { ShareSheetView(request: $0) }
        .sheet(isPresented: Binding(get: { albumFor != nil }, set: { if !$0 { albumFor = nil } })) {
            AlbumSheet(photos: albumFor ?? [], created: { album in store.openAlbumID = album.id }, done: stopSelecting)
        }
        .onChange(of: store.tab) { _, _ in stopSelecting() }
        .onChange(of: store.demoCalendar) { _, on in if on { calendar = true } }
    }

    private func toggleSelect() {
        if selecting { stopSelecting() } else { selecting = true; hideTabs = true }
    }

    private func stopSelecting() {
        selecting = false
        picked = []
        hideTabs = false
    }

    private func go(_ md: MonthDay) {
        calendar = false
        stopSelecting()
        store.day = md
    }
}

private struct Skeleton: View {
    var body: some View {
        VStack(spacing: 14) {
            HStack(alignment: .bottom) {
                RoundedRectangle(cornerRadius: 10).fill(Palette.fill2).frame(width: 128, height: 48)
                Spacer()
                RoundedRectangle(cornerRadius: 8).fill(Palette.fill).frame(width: 76, height: 28)
            }
            HStack(spacing: 4) {
                RoundedRectangle(cornerRadius: 10).fill(Palette.fill2).frame(height: 228)
                VStack(spacing: 4) {
                    RoundedRectangle(cornerRadius: 10).fill(Palette.fill)
                    RoundedRectangle(cornerRadius: 10).fill(Palette.fill2)
                }
                .frame(width: 110, height: 228)
            }
        }
        .padding(16).padding(.top, 8)
        .accessibilityLabel("Loading")
    }
}

/// Shown instead of photos on a day the user hid.
private struct HiddenDay: View {
    let day: MonthDay
    let show: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: "eye.slash").font(.system(size: 24)).foregroundStyle(Palette.muted)
                .frame(width: 56, height: 56).background(Palette.fill, in: RoundedRectangle(cornerRadius: 16))
            Text("\(day.label) is hidden").font(.nunito(24, .bold)).tight(24).foregroundStyle(Palette.ink)
            Text("It won't come up on its own or in reminders.")
                .font(.nunito(15)).foregroundStyle(Palette.muted).frame(maxWidth: 300, alignment: .leading)
            Pill(label: "Show it again", height: 40, action: show).fixedSize().padding(.top, 4)
        }
        .padding(.horizontal, 32)
        .padding(.top, 64)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct EmptyDay: View {
    let day: MonthDay
    let noSources: Bool
    let near: (before: MonthDay?, after: MonthDay?)
    let go: (MonthDay) -> Void
    let connect: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: "camera.badge.ellipsis").font(.system(size: 26)).foregroundStyle(Palette.muted)
                .frame(width: 56, height: 56).background(Palette.fill, in: RoundedRectangle(cornerRadius: 16))
            Text(noSources ? "No photos connected" : "Nothing from \(day.label) yet").font(.nunito(24, .bold)).tight(24).foregroundStyle(Palette.ink)
            Text(noSources ? "Connect Apple Photos or Google Photos to see this day in past years."
                 : near.before != nil || near.after != nil ? "You didn't take photos on this date in past years. These nearby days have some."
                 : "You didn't take photos on this date in past years.")
                .font(.nunito(15)).foregroundStyle(Palette.muted).frame(maxWidth: 300, alignment: .leading)
            HStack(spacing: 8) {
                if noSources { Pill(label: "Connect photos", height: 40, action: connect).fixedSize() }
                if let b = near.before { Pill(label: b.shortLabel, systemImage: "arrow.left", kind: .secondary, height: 40) { go(b) }.fixedSize() }
                if let a = near.after { Pill(label: a.shortLabel, systemImage: "arrow.right", kind: .secondary, height: 40) { go(a) }.fixedSize() }
            }
            .padding(.top, 4)
        }
        .padding(.horizontal, 32)
        .padding(.top, 64)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
