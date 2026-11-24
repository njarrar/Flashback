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
                    Text(tripsRange(card.trips)).font(.nunito(14)).foregroundStyle(Palette.muted)
                }
                .padding(.trailing, 32)
                Spacer(minLength: 0)
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
        .overlay(alignment: .topTrailing) {
            Button {
                store.dismissPlaceCard()
            } label: {
                Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).foregroundStyle(Palette.muted)
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .padding(4)
            .accessibilityLabel(L.s("close"))
        }
        .task(id: card.city.id) {
            guard let trip = card.trips.first, let cities = await CityList.load() else { return }
            photos = Array(await PlaceLibrary.photos(city: card.city.id, trip: trip, cities: cities).prefix(4))
        }
    }
}

/// "3 trips, from May 2019 to June 2023. 214 photos." Trips come newest first.
func tripsRange(_ trips: [Trip]) -> String {
    guard let newest = trips.first, let oldest = trips.last else { return "" }
    let first = L.date(oldest.start, "yMMMM"), last = L.date(newest.start, "yMMMM")
    let key = first == last ? "place_card_one" : "place_card_range"
    return L.s(key, ["trips": L.p("trips", trips.count), "photos": L.p("photos", trips.reduce(0) { $0 + $1.photos }), "first": first, "last": last])
}

/// "3 trips · 214 photos".
func tripsAndPhotos(_ trips: [Trip]) -> String {
    L.s("trips_photos", ["trips": L.p("trips", trips.count), "photos": L.p("photos", trips.reduce(0) { $0 + $1.photos })])
}

/// Every visit to a city, newest first, each with a few of its photos.
struct PlacePage: View {
    @Environment(AppStore.self) private var store
    let city: City
    @State private var showNavTitle = false

    var body: some View {
        let all = trips(store.saved.placeIndex[city.id] ?? [:])
        let country = L.locale.localizedString(forRegionCode: city.country) ?? city.country
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(city.name).font(.nunito(34, .bold)).tight(34).foregroundStyle(Palette.ink)
                        .accessibilityAddTraits(.isHeader)
                        .onGeometryChange(for: Bool.self) { $0.frame(in: .global).maxY < 96 } action: { showNavTitle = $0 }
                    if !country.isEmpty {
                        Text(country).font(.nunito(15, .semibold)).foregroundStyle(Palette.muted2)
                    }
                    Text(tripsAndPhotos(all)).font(.nunito(15)).foregroundStyle(Palette.muted)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
                ForEach(all, id: \.self) { TripSection(city: city, trip: $0) }
            }
            .padding(.bottom, 40)
        }
        .background(Palette.bg)
        .toolbar(.visible, for: .navigationBar)
        .navigationTitle(showNavTitle ? city.name : "")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// One visit: its month, how long ago and how long, then up to seven photos in a mosaic.
/// The last tile says how many more there are; any tile opens the viewer on the whole visit.
struct TripSection: View {
    @Environment(AppStore.self) private var store
    @Environment(\.layoutDirection) private var direction
    let city: City
    let trip: Trip
    @State private var photos: [Photo]?
    @State private var shareFor: ShareRequest?

    private var tripSubtitle: AttributedString {
        let ago = agoLabel(yearOf(trip.start))
        let details = L.s("days_photos", ["days": L.p("days", trip.days), "photos": L.p("photos", trip.photos)])
        let full = L.s("ago_weekday", ["ago": ago, "weekday": details])
        var attr = AttributedString(full)
        attr.font = .nunito(14)
        attr.foregroundColor = Palette.muted
        if let range = attr.range(of: ago) {
            attr[range].font = .nunito(14, .bold)
            attr[range].foregroundColor = Palette.ink
        }
        return attr
    }

    var body: some View {
        let all = photos ?? []
        let shown = min(all.count, 7)
        let more = all.count - shown
        let layout = layoutMosaic(shown)
        let rows = CGFloat(layout.rows)
        let tripTitle = L.date(trip.start, "yMMMM")
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .center, spacing: 4) {
                    Text(tripTitle).font(.nunito(30, .black)).tight(30).foregroundStyle(Palette.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 4)
                    if all.count >= 2 {
                        Button {
                            store.reel = ReelRequest(photos: all)
                        } label: {
                            Image(systemName: "play.rectangle").font(.system(size: 18)).foregroundStyle(Palette.ink)
                                .frame(width: 44, height: 44).contentShape(Rectangle())
                        }
                        .buttonStyle(PressStyle())
                        .accessibilityLabel(L.s("make_reel"))
                    }
                    if !all.isEmpty {
                        Button {
                            shareFor = ShareRequest(title: "\(city.name) · \(tripTitle)", photos: all)
                        } label: {
                            Image(systemName: "square.and.arrow.up").font(.system(size: 18)).foregroundStyle(Palette.ink)
                                .frame(width: 44, height: 44).contentShape(Rectangle())
                        }
                        .buttonStyle(PressStyle())
                        .accessibilityLabel(L.s("share"))
                    }
                }
                Text(tripSubtitle)
            }
            .padding(.leading, 4)
            if photos == nil {
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Palette.fill).frame(height: 220)
                    .accessibilityLabel(L.s("loading"))
            } else if shown > 0 {
                GeometryReader { geo in
                    let gap: CGFloat = 4, row: CGFloat = 54
                    let cell = (geo.size.width - gap * CGFloat(mosaicColumns - 1)) / CGFloat(mosaicColumns)
                    ZStack(alignment: .topLeading) {
                        ForEach(Array(all.prefix(shown).enumerated()), id: \.element.key) { i, photo in
                            let t = layout.tiles[i]
                            let col = direction == .rightToLeft ? mosaicColumns - t.col - t.cols : t.col
                            let isOverflow = more > 0 && i == shown - 1
                            PhotoTile(photo: photo, radius: 12) {
                                store.viewer = Viewer(photos: all, start: i)
                            }
                            .overlay {
                                if isOverflow {
                                    ZStack {
                                        Palette.chip
                                        Text("+" + L.num(more)).font(.nunito(18, .heavy)).foregroundStyle(Palette.chipInk)
                                    }
                                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                    .allowsHitTesting(false)
                                    .accessibilityHidden(true)
                                }
                            }
                            .accessibilityLabel(
                                isOverflow
                                    ? "\(L.s("photo_from", ["date": L.date(photo.time, "yMMMMd")])), +\(L.p("photos", more))"
                                    : L.s("photo_from", ["date": L.date(photo.time, "yMMMMd")])
                            )
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
        .sheet(item: $shareFor) { ShareSheetView(request: $0) }
        .task {
            guard photos == nil, let cities = await CityList.load() else { return }
            photos = await PlaceLibrary.photos(city: city.id, trip: trip, cities: cities)
        }
    }
}

/// Asked once on Today: shows what places does and that the location stays on the phone.
struct PlaceAskSheet: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Image(systemName: "mappin.and.ellipse").font(.system(size: 26, weight: .semibold)).foregroundStyle(Palette.muted2)
                        .frame(width: 56, height: 56)
                        .background(Palette.fill, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L.s("places_ask_title")).font(.nunito(26, .heavy)).foregroundStyle(Palette.ink)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                        Text(L.s("places_ask_body")).font(.nunito(16)).foregroundStyle(Palette.muted2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        point("lock", L.s("places_ask_private"))
                        point("clock", L.s("places_ask_open_only"))
                        point("gearshape", L.s("places_ask_settings"))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 28)
                .padding(.bottom, 20)
            }
            .scrollBounceBehavior(.basedOnSize)

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
            .padding(.top, 8)
            .padding(.bottom, 16)
        }
        .padding(.horizontal, 24)
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
