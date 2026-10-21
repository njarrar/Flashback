import SwiftUI

struct TodayView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var result: (day: MonthDay, groups: [YearGroup])?
    @State private var calendar = false
    @State private var settings = false
    @State private var selecting = false
    @State private var picked: [Photo] = []
    @State private var albumFor: [Photo]?
    /// Bottom edge of the top bar and of the big date title, in screen points.
    @State private var barBottom: CGFloat = 0
    @State private var titleBottom: CGFloat = .infinity
    /// Years whose header is pinned under the top bar right now.
    @State private var pinnedYears: Set<Int> = []
    @State private var shareFor: ShareRequest?
    @State private var stripPick: Int?
    /// How far the day has moved sideways under a swipe, and the days a swipe would land on.
    @State private var swipeX: CGFloat = 0
    @State private var neighbors: (back: (day: MonthDay, skipped: Int)?, forward: (day: MonthDay, skipped: Int)?) = (nil, nil)
    @State private var pageWidth: CGFloat = 400
    @State private var pageHeight: CGFloat = 800
    /// Where the year pills sit, so a sideways drag on them scrolls them and keeps the day.
    @State private var pillsFrame: CGRect = .zero
    @Environment(\.layoutDirection) private var direction
    @State private var placeOpen: City?
    @State private var placeAsk = false

    var body: some View {
        let day = store.day
        let groups = result?.day == day ? result?.groups : nil
        let today = MonthDay.today
        let total = groups?.reduce(0) { $0 + $1.photos.count } ?? 0
        let hidden = store.isHidden(day)
        let summary = hidden ? L.s("you_hid_date") : groups == nil ? L.s("today_loading")
            : groups!.isEmpty ? (store.indexing ? L.s("today_loading") : L.s("today_none"))
            : L.s("photos_from_years", ["photos": L.p("photos", total), "years": L.p("years", groups!.count)])
        let selection = Selection(on: selecting, keys: Set(picked.map(\.key)), start: { p in
            selecting = true
            picked = [p]
        }) { p in
            if let i = picked.firstIndex(where: { $0.key == p.key }) { picked.remove(at: i) } else { picked.append(p) }
        }

        ScrollViewReader { scroll in
            ScrollView {
              Group {
                VStack(alignment: .leading, spacing: 4) {
                    Button { calendar = true } label: {
                        HStack(spacing: 8) {
                            Text(day.label).font(.nunito(34, .bold)).tight(34).foregroundStyle(Palette.ink)
                            Image(systemName: "chevron.down").font(.system(size: 16, weight: .bold)).foregroundStyle(Palette.accent)
                        }
                    }
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityHint(L.s("pick_another_day"))
                    .accessibilityActions {
                        if let t = neighbors.back { Button(L.s("previous_day_photos")) { land(on: t) } }
                        if let t = neighbors.forward { Button(L.s("next_day_photos")) { land(on: t) } }
                    }
                    .onGeometryChange(for: CGFloat.self) { $0.frame(in: .global).maxY } action: { titleBottom = $0 }
                    Text(summary).font(.nunito(15)).foregroundStyle(Palette.muted)
                    if day != today {
                        Pill(label: L.s("back_to_today"), systemImage: "arrow.uturn.backward", kind: .tint, height: 44) { go(today) }
                            .fixedSize().padding(.top, 8)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 4)
                .padding(.bottom, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .id("top")

                if let card = store.placeCard, !selecting {
                    PlaceCardView(card: card) { placeOpen = card.city }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 22)
                }

                if hidden {
                    HiddenDay(day: day) { store.setHidden(day, false) }
                } else if let groups {
                    if groups.isEmpty {
                        EmptyDay(day: day, noSources: !store.saved.sources.device,
                                 near: nearbyDays(store.index, day), go: go, connect: { settings = true })
                    } else if store.saved.layout == .cards {
                        YearCards(groups: groups, day: day, selection: selection, height: max(360, pageHeight * 0.64)) { g in
                            shareFor = ShareRequest(title: fullDate(year: g.year, day), photos: g.photos)
                        }
                    } else if store.saved.layout == .strip {
                        let years = groups.map(\.year)
                        let at = stripPick.flatMap { years.firstIndex(of: $0) } ?? 0
                        let g = groups[at]
                        LazyVStack(spacing: 0, pinnedViews: .sectionHeaders) {
                            Section {
                                StripYear(group: g, day: day, selection: selection,
                                          newer: at > 0 ? years[at - 1] : nil, older: at + 1 < years.count ? years[at + 1] : nil,
                                          share: { shareFor = ShareRequest(title: fullDate(year: g.year, day), photos: g.photos) },
                                          pick: { stripPick = $0 })
                            } header: {
                                YearPills(years: years, selected: g.year) { stripPick = $0 }
                                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { pillsFrame = $0 }
                            }
                        }
                    } else {
                        LazyVStack(spacing: 0, pinnedViews: [.sectionHeaders]) {
                            ForEach(groups) { g in
                                Section {
                                    YearSection(group: g, day: day, selection: selection) {
                                        shareFor = ShareRequest(title: fullDate(year: g.year, day), photos: g.photos)
                                    }
                                } header: {
                                    PinnedYear(group: g, day: day, barBottom: barBottom, pinned: $pinnedYears) {
                                        shareFor = ShareRequest(title: fullDate(year: g.year, day), photos: g.photos)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Skeleton()
                }
                Color.clear.frame(height: 120)
              }
              // Offsets flip right to left; swipeX is in screen terms, like the finger.
              .offset(x: direction == .rightToLeft ? -swipeX : swipeX)
              .opacity(1 - min(abs(swipeX) / max(pageWidth, 1), 1) * 0.5)
            }
            .simultaneousGesture(daySwipe)
            .onGeometryChange(for: CGSize.self) { $0.size } action: { pageWidth = $0.width; pageHeight = $0.height }
            .refreshable {
                await store.refreshIndex(force: true)
                result = (day, await store.photos(for: day))
            }
            .onChange(of: store.day) { _, _ in stripPick = nil; scroll.scrollTo("top") }
            .safeAreaInset(edge: .top, spacing: 0) { topBar(day) }
        }
        .background(Palette.bg)
        .task(id: "\(day.key)-\(store.indexVersion)") {
            neighbors = (swipeTarget(store.index, from: day, step: -1), swipeTarget(store.index, from: day, step: 1))
            let groups = await store.photos(for: day)
            result = (day, groups)
        }
        .overlay(alignment: .bottom) {
            if selecting {
                SelectBar(count: picked.count, share: {
                    shareFor = ShareRequest(title: L.p("photos", picked.count), photos: picked)
                }, album: { albumFor = picked })
                .padding(.bottom, 4)
            }
        }
        .sheet(isPresented: $calendar) {
            CalendarSheet(start: store.day) { md in go(md) }
        }
        .sheet(isPresented: $settings) { SettingsSheet() }
        .sheet(isPresented: $placeAsk) { PlaceAskSheet() }
        .navigationDestination(item: $placeOpen) { PlacePage(city: $0) }
        .task(id: store.indexVersion) {
            // Asked once, a moment after Today has photos to show. Never in the screenshots.
            #if DEBUG
            if AppStore.launchScene != nil { return }
            #endif
            guard !store.saved.places.asked, store.samplePhotos.isEmpty, !store.index.isEmpty, CityList.shared != nil else { return }
            try? await Task.sleep(for: .seconds(1.5))
            if !Task.isCancelled, !store.saved.places.asked, store.samplePhotos.isEmpty, !calendar, !settings { placeAsk = true }
        }
        .sheet(item: $shareFor) { ShareSheetView(request: $0) }
        .sheet(isPresented: Binding(get: { albumFor != nil }, set: { if !$0 { albumFor = nil } })) {
            AlbumSheet(photos: albumFor ?? [], created: { album in store.openAlbumID = album.id }, done: stopSelecting)
        }
        .toolbar(selecting ? .hidden : .visible, for: .tabBar)
        .sensoryFeedback(.selection, trigger: picked.count) { _, _ in selecting }
        .sensoryFeedback(.selection, trigger: day)
        .onChange(of: store.tab) { _, _ in stopSelecting() }
        .onChange(of: store.demoCalendar) { _, on in if on { calendar = true } }
        #if DEBUG
        .task(id: result?.groups.first?.year) { demoScene() }
        #endif
    }

    #if DEBUG
    /// Debug screenshots only: opens a sheet or mode once the photos show.
    private func demoScene() {
        guard let scene = store.demoScene, let first = result?.groups.first else { return }
        store.demoScene = nil
        switch scene {
        case "settings": settings = true
        case "select": selecting = true; picked = Array(first.photos.prefix(3))
        case "share": shareFor = ShareRequest(title: fullDate(year: first.year, store.day), photos: first.photos)
        case "addalbum": albumFor = Array(first.photos.prefix(3))
        default: break
        }
    }
    #endif

    /// How far the big title has gone under the top bar: 0 at rest, 1 once it is hidden.
    private var scrolled: CGFloat {
        min(max((barBottom + 20 - titleBottom) / 20, 0), 1)
    }

    /// The bar at the top of Today. Clear at rest; once the date title scrolls under it,
    /// it blurs what passes beneath and shows a small date title of its own.
    private func topBar(_ day: MonthDay) -> some View {
        let p = scrolled
        let size: CGFloat = p > 0.5 ? 36 : 40
        return HStack(spacing: 8) {
            Button { calendar = true } label: {
                HStack(spacing: 6) {
                    Text(day.label).font(.nunito(17, .bold)).foregroundStyle(Palette.ink)
                    Image(systemName: "chevron.down").font(.system(size: 13, weight: .bold)).foregroundStyle(Palette.accent)
                }
            }
            .opacity(p)
            .allowsHitTesting(p > 0.5)
            .accessibilityHidden(p < 0.5)
            .accessibilityLabel(day.label)
            .accessibilityHint(L.s("pick_another_day"))
            Spacer()
            // Select and ••• share one capsule. The date title opens the calendar.
            HStack(spacing: 0) {
                Button(action: toggleSelect) {
                    Text(L.s(selecting ? "cancel" : "select")).font(.nunito(15, .semibold))
                        .padding(.horizontal, 16).frame(minHeight: size).contentShape(Rectangle())
                }
                Palette.line.frame(width: 1, height: 20)
                optionsMenu(day, size: size)
            }
            .foregroundStyle(Palette.ink)
            .background(Palette.glass, in: Capsule())
            .overlay(Capsule().stroke(Palette.line, lineWidth: 1))
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.15), value: size)
        .padding(.leading, 20).padding(.trailing, 16)
        .padding(.vertical, 8)
        .background {
            Rectangle().fill(.bar).opacity(p).ignoresSafeArea(edges: .top)
        }
        .overlay(alignment: .bottom) {
            // One hairline only: a pinned year header draws its own.
            Palette.line.frame(height: 1 / 3).opacity(pinnedYears.isEmpty ? p : 0)
        }
        .onGeometryChange(for: CGFloat.self) { $0.frame(in: .global).maxY } action: { barBottom = $0 }
    }

    /// The ••• menu: hide or show this date, the layout, then the full settings sheet.
    private func optionsMenu(_ day: MonthDay, size: CGFloat) -> some View {
        let hidden = store.isHidden(day)
        return Menu {
            Button {
                store.setHidden(day, !hidden)
            } label: {
                Label(hidden ? L.s("show_day_again", ["day": day.label]) : L.s("hide_date"), systemImage: hidden ? "eye" : "eye.slash")
            }
            Picker(L.s("layout"), selection: Binding(get: { store.saved.layout }, set: { store.saved.layout = $0 })) {
                Text(L.s("layout_all_years")).tag(YearLayout.mosaic)
                Text(L.s("layout_one_year")).tag(YearLayout.strip)
                Text(L.s("layout_cards")).tag(YearLayout.cards)
            }
            .pickerStyle(.inline)
            Button { settings = true } label: { Label(L.s("settings"), systemImage: "gearshape") }
        } label: {
            Image(systemName: "ellipsis").font(.system(size: 17, weight: .bold))
                .frame(width: 44, height: size).contentShape(Rectangle())
        }
        .accessibilityLabel(L.s("options"))
    }

    /// Sideways drags move between days with photos: toward the left for the next day,
    /// mirrored right to left. A drag that is mostly up and down stays a scroll.
    private var daySwipe: some Gesture {
        DragGesture(minimumDistance: 24, coordinateSpace: .global)
            .onChanged { v in
                guard sideways(v) else { if swipeX != 0 { swipeX = 0 }; return }
                let dx = v.translation.width
                // Gives way slowly when there is no day that way.
                swipeX = target(dx) == nil ? dx / 4 : dx
            }
            .onEnded { v in
                let dx = v.translation.width
                if sideways(v), abs(dx) > 80 || abs(v.predictedEndTranslation.width) > 240, let t = target(dx) {
                    turn(to: t, leaving: dx < 0 ? -pageWidth : pageWidth)
                } else {
                    withAnimation(reduceMotion ? nil : .spring(duration: 0.25)) { swipeX = 0 }
                }
            }
    }

    private func sideways(_ v: DragGesture.Value) -> Bool {
        if selecting { return false }
        if store.saved.layout == .strip, pillsFrame.contains(v.startLocation) { return false }
        return abs(v.translation.width) > abs(v.translation.height) * 1.5
    }

    private func target(_ dx: CGFloat) -> (day: MonthDay, skipped: Int)? {
        (dx < 0) != (direction == .rightToLeft) ? neighbors.forward : neighbors.back
    }

    /// Slides the day out, swaps it, and slides the new one in from the other side.
    private func turn(to t: (day: MonthDay, skipped: Int), leaving out: CGFloat) {
        if reduceMotion { swipeX = 0; land(on: t); return }
        withAnimation(.easeIn(duration: 0.15)) { swipeX = out } completion: {
            land(on: t)
            swipeX = -out
            withAnimation(.easeOut(duration: 0.22)) { swipeX = 0 }
        }
    }

    private func land(on t: (day: MonthDay, skipped: Int)) {
        go(t.day)
        if t.skipped > 0 { store.say(L.p("skipped_days", t.skipped)) }
    }

    private func toggleSelect() {
        if selecting { stopSelecting() } else { selecting = true }
    }

    private func stopSelecting() {
        selecting = false
        picked = []
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
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill2).frame(width: 128, height: 48)
                Spacer()
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill).frame(width: 76, height: 28)
            }
            HStack(spacing: 4) {
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill2).frame(height: 228)
                VStack(spacing: 4) {
                    RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill)
                    RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill2)
                }
                .frame(width: 110, height: 228)
            }
        }
        .padding(16).padding(.top, 8)
        .accessibilityLabel(L.s("loading"))
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
            Text(L.s("day_is_hidden", ["day": day.label])).font(.nunito(24, .bold)).tight(24).foregroundStyle(Palette.ink)
            Text(L.s("hidden_body"))
                .font(.nunito(15)).foregroundStyle(Palette.muted).frame(maxWidth: 300, alignment: .leading)
            Pill(label: L.s("show_it_again"), height: 40, action: show).fixedSize().padding(.top, 4)
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
                .frame(width: 56, height: 56).background(Palette.fill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            Text(noSources ? L.s("empty_no_sources_title") : L.s("empty_title", ["day": day.label])).font(.nunito(24, .bold)).tight(24).foregroundStyle(Palette.ink)
            Text(noSources ? L.s("empty_no_sources_ios")
                 : near.before != nil || near.after != nil ? L.s("empty_nearby")
                 : L.s("empty_none"))
                .font(.nunito(15)).foregroundStyle(Palette.muted).frame(maxWidth: 300, alignment: .leading)
            HStack(spacing: 8) {
                if noSources { Pill(label: L.s("connect_photos"), height: 40, action: connect).fixedSize() }
                if let b = near.before { Pill(label: b.shortLabel, systemImage: "arrow.backward", kind: .secondary, height: 40) { go(b) }.fixedSize() }
                if let a = near.after { Pill(label: a.shortLabel, systemImage: "arrow.forward", kind: .secondary, height: 40) { go(a) }.fixedSize() }
            }
            .padding(.top, 4)
        }
        .padding(.horizontal, 32)
        .padding(.top, 64)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
