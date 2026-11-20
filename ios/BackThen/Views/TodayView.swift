import SwiftUI

struct TodayView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var textSize
    @State private var result: (day: MonthDay, groups: [YearGroup])?
    @State private var calendar = false
    @State private var settings = false
    @State private var selecting = false
    @State private var picked: [Photo] = []
    @State private var albumFor: [Photo]?
    /// Bottom edge of the top bar and its height, in screen points.
    @State private var barBottom: CGFloat = 0
    @State private var barHeight: CGFloat = 56
    @State private var capsuleWidth: CGFloat = 130
    /// How far the big title has gone under the top bar: 0 at rest, 1 once it is hidden.
    /// Kept in steps of 0.05, so scrolling past it doesn't redraw the screen on every frame.
    @State private var scrolled: CGFloat = 0
    /// Years whose header is pinned under the top bar right now.
    @State private var pinnedYears: Set<Int> = []
    @State private var shareFor: ShareRequest?
    @State private var stripPick: Int?
    /// How far the day has moved sideways under a swipe, and the days a swipe would land on.
    @State private var swipeX: CGFloat = 0
    @State private var neighbors: (back: (day: MonthDay, skipped: Int)?, forward: (day: MonthDay, skipped: Int)?) = (nil, nil)
    @State private var pageWidth: CGFloat = 400
    @State private var pageHeight: CGFloat = 800
    /// Where interactive horizontal regions sit, so sideways drags on them don't swipe the day.
    @State private var pillsFrame: CGRect = .zero
    @State private var cardsFrame: CGRect = .zero
    @State private var boundaryHit = 0
    @State private var boundaryArmed = false
    @State private var limitedBannerDismissed = false
    @Environment(\.layoutDirection) private var direction
    @State private var placeOpen: City?
    @State private var placeAsk = false
    @State private var drag = DragSelect()

    var body: some View {
        let day = store.day
        let groups = result?.day == day ? result?.groups : nil
        let today = MonthDay.today
        let total: Int = groups?.reduce(0) { $0 + $1.photos.count } ?? 0
        let hidden = store.isHidden(day)
        let canSelect = !hidden && total > 0
        let stackHeader: Bool = textSize >= .accessibility1 || capsuleWidth > pageWidth * 0.42
        // Strict non-overlapping opacity handoff: large title fades out completely by 0.45;
        // compact title fades in only after 0.55 so both can never overlap.
        let largeTitleOpacity: CGFloat = max(0, min(1, 1 - scrolled / 0.45))
        let compactTitleOpacity: CGFloat = max(0, min(1, (scrolled - 0.55) / 0.45))
        let barBgOpacity: CGFloat = max(0, min(1, (scrolled - 0.1) / 0.6))

        let summary = summaryText(hidden: hidden, groups: groups, total: total)

        let activeStripGroup: YearGroup? = {
            guard store.saved.layout == .strip, let groups, !groups.isEmpty else { return nil }
            let years = groups.map(\.year)
            let at = stripPick.flatMap { years.firstIndex(of: $0) } ?? 0
            return groups[at]
        }()

        let visiblePhotos: [Photo] = activeStripGroup?.photos ?? (groups?.flatMap(\.photos) ?? [])
        let pickedKeys = Set(picked.map(\.key))
        let allSelected = !visiblePhotos.isEmpty && visiblePhotos.allSatisfy { pickedKeys.contains($0.key) }

        let selection = Selection(
            on: selecting,
            keys: pickedKeys,
            start: { p in
                selecting = true
                picked = [p]
                drag.armed = true
            },
            batch: { photos, select in
                if select {
                    let existing = Set(picked.map(\.key))
                    let toAdd = photos.filter { !existing.contains($0.key) }
                    if !toAdd.isEmpty { picked += toAdd }
                } else {
                    let drop = Set(photos.map(\.key))
                    picked = picked.filter { !drop.contains($0.key) }
                }
            }
        ) { p in
            if let i = picked.firstIndex(where: { $0.key == p.key }) { picked.remove(at: i) } else { picked.append(p) }
        }

        ScrollViewReader { scroll in
            ScrollView {
                Group {
                    headerView(day: day, today: today, stackHeader: stackHeader, largeTitleOpacity: largeTitleOpacity, summary: summary)

                    dayContent(day: day, groups: groups, hidden: hidden, selection: selection, activeStripGroup: activeStripGroup)
                    Color.clear.frame(height: 120)
                }
                .background(
                    DragSelectHook(
                        drag: drag,
                        enabled: selecting && store.saved.layout != .cards,
                        order: { visiblePhotos },
                        selecting: $selecting,
                        picked: $picked
                    )
                )
                .environment(\.dragSelect, store.saved.layout == .cards ? nil : drag)
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
            .onChange(of: store.day) { _, _ in
                stripPick = nil
                scroll.scrollTo("top")
            }
            .onChange(of: stripPick) { _, _ in
                withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) {
                    scroll.scrollTo("top", anchor: .top)
                }
            }
            .safeAreaInset(edge: .top, spacing: 0) {
                topBar(day, canSelect: canSelect, compactOpacity: compactTitleOpacity, barOpacity: barBgOpacity)
            }
        }
        .background(Palette.bg)
        .task(id: "\(day.key)-\(store.indexVersion)") {
            neighbors = (swipeTarget(store.index, from: day, step: -1), swipeTarget(store.index, from: day, step: 1))
            let groups = await store.photos(for: day)
            result = (day, groups)
        }
        .overlay(alignment: .bottom) {
            if selecting {
                SelectBar(
                    count: picked.count,
                    allSelected: allSelected,
                    onToggleAll: {
                        picked = allSelected ? [] : visiblePhotos
                    },
                    share: {
                        shareFor = ShareRequest(title: L.p("photos", picked.count), photos: picked)
                    },
                    album: { albumFor = picked },
                    reel: { startReel(picked) }
                )
                .padding(.bottom, 4)
            }
        }
        .sheet(isPresented: $calendar) {
            // The screenshot demo opens on last month until the 15th, so the calendar shot
            // has a month of photo days rather than days still to come (see make_photos.py).
            CalendarSheet(start: store.demoCalendar && store.day.day < 15 ? store.day.shifted(by: -store.day.day) : store.day) { md in go(md) }
        }
        .sheet(isPresented: $settings) { SettingsSheet() }
        .sheet(isPresented: $placeAsk) { PlaceAskSheet() }
        .navigationDestination(item: $placeOpen) { PlacePage(city: $0) }
        .task(id: store.indexVersion) {
            // Asked once, a moment after Today has photos to show. Never in the screenshots.
            #if DEBUG
            if AppStore.launchScene != nil { return }
            #endif
            guard !store.saved.places.asked, store.samplePhotos.isEmpty, !store.index.isEmpty, CityList.available else { return }
            try? await Task.sleep(for: .seconds(1.5))
            if !Task.isCancelled, !store.saved.places.asked, store.samplePhotos.isEmpty,
               !calendar, !settings, !selecting, store.viewer == nil, store.reel == nil,
               shareFor == nil, albumFor == nil {
                placeAsk = true
            }
        }
        .sheet(item: $shareFor) { ShareSheetView(request: $0) }
        .sheet(isPresented: Binding(get: { albumFor != nil }, set: { if !$0 { albumFor = nil } })) {
            AlbumSheet(photos: albumFor ?? [], created: { album in store.openAlbumID = album.id }, done: stopSelecting)
        }
        .toolbar(selecting ? .hidden : .visible, for: .tabBar)
        .sensoryFeedback(.selection, trigger: picked.count) { _, _ in selecting }
        .sensoryFeedback(.selection, trigger: day)
        .sensoryFeedback(.impact(flexibility: .rigid), trigger: boundaryHit)
        .onChange(of: store.tab) { _, _ in stopSelecting() }
        .onChange(of: total) { _, n in if n == 0 && selecting { stopSelecting() } }
        .onChange(of: hidden) { _, h in if h && selecting { stopSelecting() } }
        .onChange(of: store.demoCalendar) { _, on in if on { calendar = true } }
        .onDisappear { drag.cancel() }
        #if DEBUG
        .task(id: result?.groups.first?.year) { demoScene() }
        #endif
    }

    @ViewBuilder
    private func headerView(day: MonthDay, today: MonthDay, stackHeader: Bool, largeTitleOpacity: CGFloat, summary: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Button { calendar = true } label: {
                HStack(spacing: 8) {
                    Text(day.label).font(.nunito(34, .bold)).tight(34).foregroundStyle(Palette.ink)
                        .lineLimit(1).minimumScaleFactor(0.6)
                    Image(systemName: "chevron.down").font(.system(size: 16, weight: .bold)).foregroundStyle(Palette.accent)
                }
            }
            // Share the top bar row at normal text sizes; stack cleanly below at AX1+ or when capsule is wide.
            .padding(.trailing, stackHeader ? 0 : capsuleWidth + 8)
            .opacity(largeTitleOpacity)
            .allowsHitTesting(largeTitleOpacity > 0.1)
            .accessibilityHidden(largeTitleOpacity < 0.1)
            .accessibilityAddTraits(.isHeader)
            .accessibilityHint(L.s("pick_another_day"))
            .accessibilityActions {
                if let t = neighbors.back { Button(L.s("previous_day_photos")) { land(on: t) } }
                if let t = neighbors.forward { Button(L.s("next_day_photos")) { land(on: t) } }
            }
            .onGeometryChange(for: CGFloat.self) { proxy in
                let frame = proxy.frame(in: .global)
                let refBar: CGFloat = stackHeader
                    ? barBottom + 20
                    : (barBottom - barHeight + 8 + frame.height) + 8
                return Self.progress(bar: refBar, title: frame.maxY)
            } action: { scrolled = $0 }

            HStack(alignment: .center, spacing: 8) {
                Text(summary).font(.nunito(15)).foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 4)
            }
            if day != today {
                Pill(label: L.s("back_to_today"), systemImage: "arrow.uturn.backward", kind: .tint, height: 44) { go(today) }
                    .fixedSize().padding(.top, 6)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, stackHeader ? 4 : 8 - barHeight)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .id("top")
    }

    @ViewBuilder
    private func dayContent(day: MonthDay, groups: [YearGroup]?, hidden: Bool, selection: Selection, activeStripGroup: YearGroup?) -> some View {
        if store.photoAccessRevoked && !selecting {
            accessBanner(
                icon: "exclamationmark.triangle.fill",
                title: L.s("empty_no_sources_title"),
                body: L.s("toast_allow_access"),
                actionLabel: L.s("open_settings"),
                dismissible: false
            ) {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
        } else if PhotoLibrary.isLimited && !limitedBannerDismissed && !selecting {
            accessBanner(
                icon: "photo.badge.plus",
                title: L.s("limited_access"),
                body: L.s("limited_access_note"),
                actionLabel: L.s("manage_photos"),
                dismissible: true
            ) {
                PhotoLibrary.presentLimitedPicker()
            }
        }

        if let card = store.placeCard, !selecting {
            PlaceCardView(card: card) { placeOpen = card.city }
                .padding(.horizontal, 16)
                .padding(.bottom, 22)
        }

        if hidden {
            HiddenDay(day: day) { store.setHidden(day, false) }
        } else if let groups {
            if groups.isEmpty && store.indexing {
                Skeleton(layout: store.saved.layout)
            } else if groups.isEmpty {
                EmptyDay(
                    day: day,
                    noSources: !store.saved.sources.device || store.photoAccessRevoked,
                    isLimited: PhotoLibrary.isLimited,
                    near: nearbyDays(store.index, day),
                    go: go,
                    connect: {
                        if store.photoAccessRevoked || PhotoLibrary.isDenied {
                            if let url = URL(string: UIApplication.openSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                        } else {
                            settings = true
                        }
                    },
                    manageLimited: { PhotoLibrary.presentLimitedPicker() }
                )
            } else if store.saved.layout == .cards {
                YearCards(groups: groups, day: day, selection: selection, height: max(360, pageHeight * 0.64)) { g in
                    shareFor = ShareRequest(title: fullDate(year: g.year, day), photos: g.photos)
                }
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { cardsFrame = $0 }
            } else if store.saved.layout == .strip, let g = activeStripGroup {
                let years = groups.map(\.year)
                let at = years.firstIndex(of: g.year) ?? 0
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
                            PinnedYear(group: g, day: day, barBottom: barBottom, pinned: $pinnedYears, selection: selection) {
                                shareFor = ShareRequest(title: fullDate(year: g.year, day), photos: g.photos)
                            }
                        }
                    }
                }
            }
        } else {
            Skeleton(layout: store.saved.layout)
        }
    }

    private func summaryText(hidden: Bool, groups: [YearGroup]?, total: Int) -> String {
        if hidden { return L.s("you_hid_date") }
        guard let groups else { return L.s("today_loading") }
        if groups.isEmpty { return store.indexing ? L.s("today_loading") : L.s("today_none") }
        let parts: [String: Any] = ["photos": L.p("photos", total), "years": L.p("years", groups.count)]
        return L.s("photos_from_years", parts)
    }

    private func accessBanner(
        icon: String,
        title: String,
        body: String,
        actionLabel: String,
        dismissible: Bool,
        action: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Palette.accInk)
                    .frame(width: 32, height: 32)
                    .background(Palette.accTint, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.nunito(15, .bold)).foregroundStyle(Palette.ink)
                    Text(body).font(.nunito(13)).foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 4)
                if dismissible {
                    Button { limitedBannerDismissed = true } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Palette.muted)
                            .frame(width: 44, height: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel(L.s("close"))
                }
            }
            Pill(label: actionLabel, kind: .secondary, height: 40, action: action).fixedSize()
        }
        .padding(14)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Palette.line, lineWidth: 1))
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
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
        case "reel", "reelmore":
            store.reel = ReelRequest(photos: result?.groups.flatMap(\.photos) ?? [], tab: scene == "reel" ? .stamp : .more)
        default: break
        }
    }
    #endif

    /// How far the big title has gone under the top bar, in steps of 0.05.
    static func progress(bar: CGFloat, title: CGFloat) -> CGFloat {
        // At rest the title shares the bar's row, so its bottom sits a little above the bar's.
        let p = min(max((bar - 8 - title) / 24, 0), 1)
        return (p * 20).rounded() / 20
    }

    /// The bar at the top of Today. Clear at rest; once the date title scrolls out of view,
    /// it blurs what passes beneath and fades in its own compact date title without overlap.
    private func topBar(_ day: MonthDay, canSelect: Bool, compactOpacity: CGFloat, barOpacity: CGFloat) -> some View {
        // One size at rest and when scrolled. The bar's height places the date title, so a
        // bar that shrank as the title went under it moved the title back out, grew again,
        // and kept flipping until the app froze.
        let size: CGFloat = 40
        let showSelect = canSelect || selecting
        return HStack(spacing: 8) {
            Button { calendar = true } label: {
                HStack(spacing: 6) {
                    Text(day.label).font(.nunito(17, .bold)).foregroundStyle(Palette.ink)
                        .lineLimit(1).minimumScaleFactor(0.8)
                    Image(systemName: "chevron.down").font(.system(size: 13, weight: .bold)).foregroundStyle(Palette.accent)
                }
            }
            .opacity(compactOpacity)
            .allowsHitTesting(compactOpacity > 0.5)
            .accessibilityHidden(compactOpacity < 0.5)
            .accessibilityLabel(day.label)
            .accessibilityHint(L.s("pick_another_day"))
            Spacer()
            // Select and ••• share one capsule when selecting is available; otherwise just •••.
            HStack(spacing: 0) {
                if showSelect {
                    Button(action: toggleSelect) {
                        Text(L.s(selecting ? "cancel" : "select")).font(.nunito(15, .semibold))
                            .padding(.horizontal, 16).frame(minHeight: size).contentShape(Rectangle())
                    }
                    Palette.line.frame(width: 1, height: 20)
                }
                optionsMenu(day, size: size)
            }
            .foregroundStyle(Palette.ink)
            .background(Palette.glass, in: Capsule())
            .overlay(Capsule().stroke(Palette.line, lineWidth: 1))
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { capsuleWidth = $0 }
        }
        .padding(.leading, 20).padding(.trailing, 16)
        .padding(.vertical, 8)
        .background {
            Rectangle().fill(.bar).opacity(barOpacity).allowsHitTesting(false).ignoresSafeArea(edges: .top)
        }
        .overlay(alignment: .bottom) {
            // One hairline only: a pinned year header draws its own.
            Palette.line.frame(height: 1 / 3).opacity(pinnedYears.isEmpty ? barOpacity : 0)
        }
        .onGeometryChange(for: CGFloat.self) { $0.frame(in: .global).maxY } action: { barBottom = $0 }
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { barHeight = $0 }
    }

    /// The ••• menu: creation and layout first, settings next, destructive Hide date last.
    private func optionsMenu(_ day: MonthDay, size: CGFloat) -> some View {
        let hidden = store.isHidden(day)
        return Menu {
            if let photos = result?.day == day ? result?.groups.flatMap(\.photos) : nil, photos.count >= 2, !hidden {
                Button { startReel(photos) } label: { Label(L.s("make_reel"), systemImage: "play.rectangle") }
            }
            Picker(L.s("layout"), selection: Binding(get: { store.saved.layout }, set: { store.saved.layout = $0 })) {
                Text(L.s("layout_all_years")).tag(YearLayout.mosaic)
                Text(L.s("layout_one_year")).tag(YearLayout.strip)
                Text(L.s("layout_cards")).tag(YearLayout.cards)
            }
            .pickerStyle(.inline)
            Button { settings = true } label: { Label(L.s("settings"), systemImage: "gearshape") }
            Divider()
            if hidden {
                Button {
                    store.setHidden(day, false)
                } label: {
                    Label(L.s("show_day_again", ["day": day.label]), systemImage: "eye")
                }
            } else {
                Button(role: .destructive) {
                    store.setHidden(day, true)
                    store.showToast(
                        L.s("toast_day_hidden", ["day": day.shortLabel]),
                        kind: .info,
                        undoLabel: L.s("undo"),
                        undo: { store.setHidden(day, false) }
                    )
                } label: {
                    Label(L.s("hide_date"), systemImage: "eye.slash")
                }
            }
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
                if target(dx) == nil {
                    swipeX = dx / 4
                    if !boundaryArmed && abs(dx) > 40 {
                        boundaryArmed = true
                        boundaryHit &+= 1
                    }
                } else {
                    boundaryArmed = false
                    swipeX = dx
                }
            }
            .onEnded { v in
                boundaryArmed = false
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
        // Ignore screen-edge drags to avoid conflicting with system gestures.
        if v.startLocation.x < 20 || v.startLocation.x > pageWidth - 20 { return false }
        if store.saved.layout == .strip, pillsFrame.contains(v.startLocation) { return false }
        if store.saved.layout == .cards, cardsFrame.contains(v.startLocation) { return false }
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
        if t.skipped > 0 { store.showToast(L.p("skipped_days", t.skipped), kind: .info, undoLabel: nil, undo: nil) }
    }

    /// Opens the reel editor with these photos, two to `ReelPlan.maxPhotos`.
    private func startReel(_ photos: [Photo]) {
        guard photos.count >= 2 else { return store.showToast(L.s("reel_need_two"), kind: .info, undoLabel: nil, undo: nil) }
        if photos.count > ReelPlan.maxPhotos { store.showToast(L.p("reel_too_many", ReelPlan.maxPhotos), kind: .info, undoLabel: nil, undo: nil) }
        store.reel = ReelRequest(photos: photos)
        stopSelecting()
    }

    private func toggleSelect() {
        if selecting { stopSelecting() } else { selecting = true }
    }

    private func stopSelecting() {
        selecting = false
        picked = []
        drag.cancel()
    }

    private func go(_ md: MonthDay) {
        calendar = false
        stopSelecting()
        store.day = md
    }
}

private struct Skeleton: View {
    let layout: YearLayout
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulse = false

    var body: some View {
        Group {
            switch layout {
            case .cards:
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(Palette.fill2)
                    .frame(height: 440)
            case .strip:
                VStack(alignment: .leading, spacing: 14) {
                    RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill2).frame(width: 180, height: 76)
                    HStack(spacing: 6) {
                        VStack(spacing: 6) {
                            RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill2).frame(height: 200)
                            RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill).frame(height: 140)
                        }
                        VStack(spacing: 6) {
                            RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill).frame(height: 150)
                            RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill2).frame(height: 190)
                        }
                    }
                }
            case .mosaic:
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
            }
        }
        .opacity(pulse ? 0.55 : 1)
        .padding(16).padding(.top, 8)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                pulse = true
            }
        }
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
            Pill(label: L.s("show_it_again"), height: 44, action: show).fixedSize().padding(.top, 4)
        }
        .padding(.horizontal, 32)
        .padding(.top, 64)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct EmptyDay: View {
    let day: MonthDay
    let noSources: Bool
    let isLimited: Bool
    let near: (before: MonthDay?, after: MonthDay?)
    let go: (MonthDay) -> Void
    let connect: () -> Void
    let manageLimited: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: "camera.badge.ellipsis").font(.system(size: 26)).foregroundStyle(Palette.muted)
                .frame(width: 56, height: 56).background(Palette.fill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            Text(noSources ? L.s("empty_no_sources_title") : L.s("empty_title", ["day": day.label])).font(.nunito(24, .bold)).tight(24).foregroundStyle(Palette.ink)
            Text(noSources ? L.s("empty_no_sources_ios")
                 : isLimited ? L.s("limited_access_note")
                 : near.before != nil || near.after != nil ? L.s("empty_nearby")
                 : L.s("empty_none"))
                .font(.nunito(15)).foregroundStyle(Palette.muted).frame(maxWidth: 300, alignment: .leading)
            HStack(spacing: 8) {
                if noSources { Pill(label: L.s("connect_photos"), height: 44, action: connect).fixedSize() }
                else if isLimited { Pill(label: L.s("manage_photos"), kind: .secondary, height: 44, action: manageLimited).fixedSize() }
                if let b = near.before { Pill(label: b.shortLabel, systemImage: "arrow.backward", kind: .secondary, height: 44) { go(b) }.fixedSize() }
                if let a = near.after { Pill(label: a.shortLabel, systemImage: "arrow.forward", kind: .secondary, height: 44) { go(a) }.fixedSize() }
            }
            .padding(.top, 4)
        }
        .padding(.horizontal, 32)
        .padding(.top, 64)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
