import SwiftUI

/// On Today, in a city you've been to before: how many trips and photos, a few of them,
/// and a button to the city page.
struct PlaceCardView: View {
    @Environment(AppStore.self) private var store
    let card: PlaceCard
    let open: () -> Void
    @State private var photos: [Photo] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "mappin.and.ellipse").font(.system(size: 18, weight: .semibold)).foregroundStyle(Palette.muted2)
                    .frame(width: 40, height: 40)
                    .background(Palette.fill, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(L.s("place_been_before", ["city": card.city.name])).font(.nunito(17, .heavy)).foregroundStyle(Palette.ink)
                    Text(tripsAndPhotos(card.trips)).font(.nunito(14)).foregroundStyle(Palette.muted)
                }
                Spacer(minLength: 0)
                Button { store.dismissPlaceCard() } label: {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).foregroundStyle(Palette.muted)
                        .frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .padding(.top, -10).padding(.trailing, -12)
                .accessibilityLabel(L.s("close"))
            }
            if !photos.isEmpty {
                HStack(spacing: 4) {
                    ForEach(photos) { p in
                        Color.clear.frame(height: 64)
                            .overlay { PhotoImage(photo: p) }
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }
                .accessibilityHidden(true)
            }
            Pill(label: L.s("see_city", ["city": card.city.name]), kind: .secondary, height: 44, wide: true, action: open)
        }
        .padding(16)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Palette.line, lineWidth: 1))
        .task(id: card.city.id) {
            guard let cities = CityList.shared, let trip = card.trips.first else { return }
            photos = Array(await PlaceLibrary.photos(city: card.city.id, trip: trip, cities: cities).prefix(4))
        }
    }
}

/// "3 trips · 214 photos".
func tripsAndPhotos(_ trips: [Trip]) -> String {
    L.s("trips_photos", ["trips": L.p("trips", trips.count), "photos": L.p("photos", trips.reduce(0) { $0 + $1.photos })])
}

/// Every visit to a city, newest first, each with a few of its photos.
struct PlacePage: View {
    @Environment(AppStore.self) private var store
    let city: City

    var body: some View {
        let all = trips(store.saved.placeIndex[city.id] ?? [:])
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(city.name).font(.nunito(34, .bold)).tight(34).foregroundStyle(Palette.ink)
                        .accessibilityAddTraits(.isHeader)
                    Text(tripsAndPhotos(all)).font(.nunito(15)).foregroundStyle(Palette.muted)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
                ForEach(all, id: \.self) { TripSection(city: city.id, trip: $0) }
            }
            .padding(.bottom, 40)
        }
        .background(Palette.bg)
        .toolbar(.visible, for: .navigationBar)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// One visit: its month, how long ago and how long, then up to seven photos in a mosaic.
/// The last tile says how many more there are; any tile opens the viewer on the whole visit.
struct TripSection: View {
    @Environment(AppStore.self) private var store
    @Environment(\.layoutDirection) private var direction
    let city: Int
    let trip: Trip
    @State private var photos: [Photo]?

    var body: some View {
        let all = photos ?? []
        let shown = min(all.count, 7)
        let more = all.count - shown
        let layout = layoutMosaic(shown)
        let rows = CGFloat(layout.rows)
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(L.date(trip.start, "yMMMM")).font(.nunito(30, .black)).tight(30).foregroundStyle(Palette.ink)
                    .accessibilityAddTraits(.isHeader)
                (Text(agoLabel(yearOf(trip.start))).foregroundStyle(Palette.ink).font(.nunito(14, .bold))
                 + Text(" · " + L.s("days_photos", ["days": L.p("days", trip.days), "photos": L.p("photos", trip.photos)])).foregroundStyle(Palette.muted))
                    .font(.nunito(14))
            }
            .padding(.leading, 4)
            if photos == nil {
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill).frame(height: 220)
            } else if shown > 0 {
                GeometryReader { geo in
                    let gap: CGFloat = 4, row: CGFloat = 54
                    let cell = (geo.size.width - gap * CGFloat(mosaicColumns - 1)) / CGFloat(mosaicColumns)
                    ZStack(alignment: .topLeading) {
                        ForEach(Array(all.prefix(shown).enumerated()), id: \.element.key) { i, photo in
                            let t = layout.tiles[i]
                            let col = direction == .rightToLeft ? mosaicColumns - t.col - t.cols : t.col
                            PhotoTile(photo: photo, radius: 12) {
                                store.viewer = Viewer(photos: all, start: i)
                            }
                            .overlay {
                                if more > 0, i == shown - 1 {
                                    ZStack {
                                        Palette.chip
                                        Text("+" + L.num(more)).font(.nunito(18, .heavy)).foregroundStyle(Palette.chipInk)
                                    }
                                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                    .allowsHitTesting(false)
                                }
                            }
                            .environment(\.layoutDirection, direction)
                            .frame(width: cell * CGFloat(t.cols) + gap * CGFloat(t.cols - 1), height: row * CGFloat(t.rows) + gap * CGFloat(t.rows - 1))
                            .offset(x: (cell + gap) * CGFloat(col), y: (row + gap) * CGFloat(t.row))
                        }
                    }
                    .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
                }
                // Tiles are placed from the left edge; the column above mirrors them.
                .environment(\.layoutDirection, .leftToRight)
                .frame(height: rows * 54 + (rows - 1) * 4)
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 28)
        .task {
            guard photos == nil, let cities = CityList.shared else { return }
            photos = await PlaceLibrary.photos(city: city, trip: trip, cities: cities)
        }
    }
}

/// Asked once on Today: shows what places does and that the location stays on the phone.
struct PlaceAskSheet: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Image(systemName: "mappin.and.ellipse").font(.system(size: 26, weight: .semibold)).foregroundStyle(Palette.muted2)
                .frame(width: 56, height: 56)
                .background(Palette.fill, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 8) {
                Text(L.s("places_ask_title")).font(.nunito(26, .heavy)).foregroundStyle(Palette.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Text(L.s("places_ask_body")).font(.nunito(16)).foregroundStyle(Palette.muted2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            VStack(alignment: .leading, spacing: 12) {
                point("lock", L.s("places_ask_private"))
                point("clock", L.s("places_ask_open_only"))
                point("gearshape", L.s("places_ask_settings"))
            }
            Spacer(minLength: 0)
            VStack(spacing: 4) {
                Pill(label: L.s("turn_on"), kind: .primary, height: 54) {
                    dismiss()
                    Task { await store.setPlaces(true) }
                }
                Pill(label: L.s("not_now"), kind: .ghost, height: 48) {
                    store.saved.places.asked = true
                    dismiss()
                }
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, 28)
        .padding(.bottom, 16)
        .background(Palette.bg)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        // Swiping it away counts as Not now.
        .onDisappear { store.saved.places.asked = true }
    }

    private func point(_ icon: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon).font(.system(size: 17)).foregroundStyle(Palette.muted2).frame(width: 22)
                .accessibilityHidden(true)
            Text(text).font(.nunito(15)).foregroundStyle(Palette.muted2).fixedSize(horizontal: false, vertical: true)
        }
    }
}
