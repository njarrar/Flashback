import SwiftUI

/// Today's years as a non-nested horizontal deck of cards, one per year, newest first.
/// Swiping the deck pages between years without fighting TodayView's vertical scroll or pull-to-refresh.
struct YearCards: View {
    @Environment(AppStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let groups: [YearGroup]
    let day: MonthDay
    let selection: Selection
    let height: CGFloat
    let share: (YearGroup) -> Void
    @State private var at: Int?

    var body: some View {
        let yearIDs = groups.map(\.year)
        let current = groups.firstIndex { $0.year == at } ?? 0
        VStack(spacing: 14) {
            ScrollView(.horizontal) {
                LazyHStack(spacing: 0) {
                    ForEach(groups) { g in
                        YearCard(
                            group: g,
                            day: day,
                            selectedCount: g.photos.filter { selection.keys.contains($0.key) }.count,
                            selection: yearSelection(g),
                            share: { share(g) }
                        )
                        .padding(.horizontal, 16)
                        .padding(.vertical, 6)
                        .containerRelativeFrame(.horizontal)
                        .frame(height: height)
                        .id(g.year)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollIndicators(.hidden)
            .scrollPosition(id: $at)
            .frame(height: height)

            if groups.count > 1 {
                deckIndicator(current: current)
            }
        }
        .onAppear {
            if at == nil { at = groups.first?.year }
        }
        .onChange(of: yearIDs) { _, ids in
            if let at, ids.contains(at) { return }
            self.at = ids.first
        }
    }

    @ViewBuilder
    private func deckIndicator(current: Int) -> some View {
        let activeYear = groups[min(current, groups.count - 1)].year
        let summary = "\(L.num(activeYear)) · \(L.num(current + 1)) / \(L.num(groups.count))"
        Group {
            if groups.count > 7 {
                Text(summary)
                    .font(.nunito(13, .bold))
                    .monospacedDigit()
                    .foregroundStyle(Palette.ink)
                    .padding(.horizontal, 14)
                    .frame(minHeight: 32)
                    .background(Palette.fill, in: Capsule())
            } else {
                HStack(spacing: 6) {
                    ForEach(groups.indices, id: \.self) { i in
                        Capsule().fill(i == current ? Palette.ink : Palette.faint)
                            .frame(width: i == current ? 18 : 6, height: 6)
                    }
                }
                .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: current)
            }
        }
        .frame(minHeight: 44)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L.s("layout_cards"))
        .accessibilityValue(summary)
        .accessibilityAdjustableAction { dir in
            switch dir {
            case .increment:
                if current + 1 < groups.count {
                    withAnimation(reduceMotion ? nil : .easeOut(duration: 0.25)) { at = groups[current + 1].year }
                }
            case .decrement:
                if current > 0 {
                    withAnimation(reduceMotion ? nil : .easeOut(duration: 0.25)) { at = groups[current - 1].year }
                }
            @unknown default: break
            }
        }
    }

    /// A card picks or unpicks its whole year in one batch update (1 haptic, 1 redraw).
    private func yearSelection(_ g: YearGroup) -> Selection {
        let all = !g.photos.isEmpty && g.photos.allSatisfy { selection.keys.contains($0.key) }
        let base = selection
        let applyBatch: (Bool) -> Void = { select in
            if let batch = base.batch {
                batch(g.photos, select)
            } else {
                for p in g.photos where base.keys.contains(p.key) != select { base.toggle(p) }
            }
        }
        return Selection(
            on: base.on,
            keys: all ? Set(g.photos.map(\.key)) : [],
            start: base.start.map { start in
                { _ in
                    guard let first = g.photos.first else { return }
                    if let batch = base.batch {
                        start(first)
                        batch(g.photos, true)
                    } else {
                        start(first)
                        g.photos.dropFirst().forEach(base.toggle)
                    }
                }
            },
            batch: base.batch,
            toggle: { _ in applyBatch(!all) }
        )
    }
}

/// One year: a favorite from that year (or its first photo) filling the card, with the year
/// and "6 years ago · Friday, 4 photos" over a dark wash at the bottom.
struct YearCard: View {
    @Environment(AppStore.self) private var store
    let group: YearGroup
    let day: MonthDay
    let selectedCount: Int
    let selection: Selection
    let share: () -> Void

    var body: some View {
        let cover = group.photos.firstIndex { store.favoriteKeys.contains($0.key) } ?? 0
        let facts = L.s("ago_weekday_photos", ["ago": agoLabel(group.year), "weekday": weekday(year: group.year, day), "photos": L.p("photos", group.photos.count)])
        let isFull = selectedCount == group.photos.count && !group.photos.isEmpty
        let isPartial = selectedCount > 0 && selectedCount < group.photos.count
        PhotoTile(photo: group.photos[cover], radius: 24, selection: selection, showHeart: false, showSelectionBadge: false) {
            store.viewer = Viewer(photos: group.photos, start: cover)
        }
        .overlay {
            if selection.on && selectedCount > 0 {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .strokeBorder(Palette.accent, lineWidth: isFull ? 3 : 2)
                    .allowsHitTesting(false)
            }
        }
        .overlay(alignment: .topLeading) {
            if selection.on {
                HStack(spacing: 6) {
                    Image(systemName: isFull ? "checkmark.circle.fill" : isPartial ? "minus.circle.fill" : "circle")
                        .font(.system(size: 18, weight: .semibold))
                    if isFull {
                        Text(L.num(selectedCount)).font(.nunito(13, .bold)).monospacedDigit()
                    } else if isPartial {
                        Text("\(L.num(selectedCount)) / \(L.num(group.photos.count))").font(.nunito(13, .bold)).monospacedDigit()
                    }
                }
                .foregroundStyle(.white)
                .padding(.horizontal, selectedCount > 0 ? 12 : 8)
                .frame(minWidth: 36, minHeight: 36)
                .background {
                    if isFull {
                        Capsule().fill(Palette.accentFill)
                    } else {
                        ZStack {
                            Capsule().fill(.ultraThinMaterial)
                            Capsule().fill(Palette.chip)
                        }
                    }
                }
                .padding(14)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
        }
        .overlay(alignment: .bottomLeading) {
            VStack(alignment: .leading, spacing: 2) {
                Text(L.num(group.year)).font(.nunito(52, .black, maxSize: 64)).tight(52)
                Text(facts).font(.nunito(15, .semibold, maxSize: 19))
            }
            .foregroundStyle(Palette.chipInk)
            .padding(.horizontal, 20).padding(.top, 48).padding(.bottom, 20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(LinearGradient(colors: [.clear, Palette.cardScrim], startPoint: .top, endPoint: .bottom))
            .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 24, bottomTrailingRadius: 24, style: .continuous))
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .overlay(alignment: .topTrailing) {
            if !selection.on {
                Button(action: share) {
                    Image(systemName: "square.and.arrow.up").font(.system(size: 17, weight: .semibold)).foregroundStyle(Palette.chipInk)
                        .frame(width: 40, height: 40)
                        .background {
                            ZStack {
                                Circle().fill(.ultraThinMaterial)
                                Circle().fill(Palette.chip)
                            }
                        }
                        .frame(width: 48, height: 48).contentShape(Rectangle())
                }
                .padding(8)
                .accessibilityLabel(L.s("share_year", ["year": group.year]))
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("\(L.num(group.year)), \(facts)")
    }
}
