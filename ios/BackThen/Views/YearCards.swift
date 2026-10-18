import SwiftUI

/// Today's years as a deck of cards, one per year, newest first. Swipe up and down to move
/// between years; sideways still moves between days. Tap a card to see that year's photos.
struct YearCards: View {
    @Environment(AppStore.self) private var store
    let groups: [YearGroup]
    let day: MonthDay
    let selection: Selection
    let height: CGFloat
    let share: (YearGroup) -> Void
    @State private var at: Int?

    var body: some View {
        VStack(spacing: 14) {
            ScrollView(.vertical) {
                LazyVStack(spacing: 0) {
                    ForEach(groups) { g in
                        YearCard(group: g, day: day, selection: yearSelection(g), share: { share(g) })
                            .padding(.horizontal, 16)
                            .padding(.vertical, 6)
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
                let current = groups.firstIndex { $0.year == at } ?? 0
                HStack(spacing: 6) {
                    ForEach(groups.indices, id: \.self) { i in
                        Capsule().fill(i == current ? Palette.ink : Palette.faint)
                            .frame(width: i == current ? 18 : 6, height: 6)
                    }
                }
                .animation(.easeOut(duration: 0.2), value: current)
                .accessibilityHidden(true)
            }
        }
        .onChange(of: day) { _, _ in at = groups.first?.year }
    }

    /// A card picks or unpicks its whole year at once.
    private func yearSelection(_ g: YearGroup) -> Selection {
        let all = g.photos.allSatisfy { selection.keys.contains($0.key) }
        let base = selection
        return Selection(
            on: base.on,
            keys: all ? Set(g.photos.map(\.key)) : [],
            start: base.start.map { start in
                { _ in
                    start(g.photos[0])
                    g.photos.dropFirst().forEach(base.toggle)
                }
            },
            toggle: { _ in
                for p in g.photos where base.keys.contains(p.key) == all { base.toggle(p) }
            }
        )
    }
}

/// One year: a favorite from that year (or its first photo) filling the card, with the year
/// and "6 years ago · Friday, 4 photos" over a dark wash at the bottom.
struct YearCard: View {
    @Environment(AppStore.self) private var store
    let group: YearGroup
    let day: MonthDay
    let selection: Selection
    let share: () -> Void

    var body: some View {
        let cover = group.photos.firstIndex { store.favoriteKeys.contains($0.key) } ?? 0
        let facts = L.s("ago_weekday_photos", ["ago": agoLabel(group.year), "weekday": weekday(year: group.year, day), "photos": L.p("photos", group.photos.count)])
        PhotoTile(photo: group.photos[cover], radius: 24, selection: selection, showHeart: false) {
            store.viewer = Viewer(photos: group.photos, start: cover)
        }
        .overlay(alignment: .bottomLeading) {
            VStack(alignment: .leading, spacing: 2) {
                Text(L.num(group.year)).font(.nunito(52, .black)).tight(52)
                Text(facts).font(.nunito(15, .semibold))
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
                        .background(Palette.chip, in: Circle())
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
