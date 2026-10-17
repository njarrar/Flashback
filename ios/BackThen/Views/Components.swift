import SwiftUI

/// Loads a photo from PhotoKit or app storage and fills its frame.
struct PhotoImage: View {
    let photo: Photo
    var contentMode: ContentMode = .fill
    @State private var image: UIImage?
    @Environment(\.displayScale) private var scale

    var body: some View {
        GeometryReader { geo in
            let px = contentMode == .fit
                ? CGSize(width: 2048, height: 2048)
                : ImageCache.bucket(CGSize(width: geo.size.width * scale, height: geo.size.height * scale))
            // A tile scrolled back into view shows its cached image at once, with no gray flash.
            let cached = ImageCache.get(cacheKey(px)) ?? image
            ZStack {
                // Gray placeholder for grid tiles only; the viewer shows photos on black.
                if contentMode == .fill { Palette.fill }
                if let shown = cached {
                    Image(uiImage: shown)
                        .resizable()
                        .aspectRatio(contentMode: contentMode)
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()
                }
            }
            .task(id: "\(photo.key)-\(Int(px.width))x\(Int(px.height))") {
                // The first layout pass can be zero by zero; wait for the real size.
                guard contentMode == .fit || (geo.size.width > 0 && geo.size.height > 0) else { return }
                if let hit = ImageCache.get(cacheKey(px)) {
                    image = hit
                    return
                }
                if let loaded = await loadImage(photo, size: px), !Task.isCancelled { image = loaded }
            }
        }
        .accessibilityLabel(L.s("photo_from", ["date": L.date(photo.time, "yMMMMd")]))
    }

    /// The same key `loadImage` stores under: PhotoKit id for phone photos, app key for samples.
    private func cacheKey(_ px: CGSize) -> NSString {
        ImageCache.key(photo.source == .device ? photo.ref : photo.key, px)
    }
}

/// Loads a photo from PhotoKit or app storage at about the given pixel size.
func loadImage(_ photo: Photo, size px: CGSize) async -> UIImage? {
    switch photo.source {
    case .device:
        return await PhotoLibrary.image(id: photo.ref, size: px)
    case .sample:
        let key = ImageCache.key(photo.key, px)
        if let hit = ImageCache.get(key) { return hit }
        let url = SamplePhotos.fileURL(photo.ref)
        let loaded = await Task.detached(priority: .userInitiated) {
            ImageCache.downsampledImage(at: url, size: px)
        }.value
        if let loaded { ImageCache.set(loaded, key) }
        return loaded
    }
}

enum PillKind { case primary, secondary, ghost, accent, tint }

struct Pill: View {
    let label: String
    var systemImage: String?
    var kind: PillKind = .primary
    var height: CGFloat = 54
    /// Stretch to the full width given. Defaults to ghost pills and tall pills.
    var wide: Bool?
    var iconAfter = false
    var action: () -> Void

    @Environment(\.isEnabled) private var enabled

    var body: some View {
        let (bg, fg): (Color, Color) = switch kind {
        case .primary: (Palette.ink, Palette.bg)
        case .secondary: (Palette.surface, Palette.ink)
        case .ghost: (.clear, Palette.ink)
        case .accent: (Palette.accent, .white)
        case .tint: (Palette.accTint, Palette.accInk)
        }
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage, !iconAfter { Image(systemName: systemImage).font(.system(size: height < 48 ? 14 : 18, weight: .semibold)) }
                Text(label).font(.nunito(height < 48 ? 15 : 16, .semibold))
                if let systemImage, iconAfter { Image(systemName: systemImage).font(.system(size: height < 48 ? 14 : 18, weight: .semibold)) }
            }
            .foregroundStyle(fg)
            .padding(.horizontal, height < 48 ? 16 : 20)
            .frame(height: height)
            .frame(maxWidth: wide ?? (kind == .ghost || height >= 54) ? .infinity : nil)
            .background(bg, in: Capsule())
            .overlay(Capsule().stroke(kind == .secondary ? Palette.line : .clear, lineWidth: 1))
            .opacity(enabled ? 1 : 0.4)
        }
        .buttonStyle(PressStyle())
    }
}

struct PressStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1).animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

/// List rows: a soft fill behind the row while it is pressed, no scaling.
struct RowPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Palette.fill)
                    .opacity(configuration.isPressed ? 1 : 0)
            )
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

/// The app icon from the design: two tilted prints on blue, the front one smiling.
struct Logo: View {
    var size: CGFloat = 36

    var body: some View {
        let s = size / 180
        ZStack(alignment: .topLeading) {
            Palette.hex(0x2E7BFF)
            RadialGradient(colors: [.white.opacity(0.22), .clear], center: UnitPoint(x: 0.3, y: 0.18), startRadius: 0, endRadius: 100 * s)
            RoundedRectangle(cornerRadius: 14 * s, style: .continuous)
                .fill(.white)
                .frame(width: 86 * s, height: 102 * s)
                .overlay(alignment: .top) {
                    RoundedRectangle(cornerRadius: 7 * s, style: .continuous).fill(Palette.hex(0xFFC93C))
                        .padding(EdgeInsets(top: 8 * s, leading: 8 * s, bottom: 22 * s, trailing: 8 * s))
                }
                .rotationEffect(.degrees(12))
                .offset(x: 62 * s, y: 30 * s)
            RoundedRectangle(cornerRadius: 15 * s, style: .continuous)
                .fill(.white)
                .frame(width: 96 * s, height: 114 * s)
                .overlay(alignment: .topLeading) {
                    ZStack(alignment: .topLeading) {
                        RoundedRectangle(cornerRadius: 8 * s, style: .continuous).fill(Palette.hex(0xFF4B3E))
                        dot(21, 29.6, 9.5, 10.5, Palette.hex(0x1D1B2E), s)
                        dot(47.4, 29.6, 9.5, 10.5, Palette.hex(0x1D1B2E), s)
                        UnevenRoundedRectangle(bottomLeadingRadius: 11.5 * s, bottomTrailingRadius: 11.5 * s, style: .continuous)
                            .fill(Palette.hex(0x1D1B2E)).frame(width: 23 * s, height: 11.5 * s).offset(x: 27.5 * s, y: 44.3 * s)
                        dot(10.6, 41, 10.5, 7.4, Palette.hex(0xFF9EC0, 0.9), s)
                        dot(56.8, 41, 10.5, 7.4, Palette.hex(0xFF9EC0, 0.9), s)
                    }
                    .frame(width: 78 * s, height: 78 * s)
                    .offset(x: 9 * s, y: 9 * s)
                }
                .rotationEffect(.degrees(-7))
                .offset(x: 34 * s, y: 44 * s)
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.24, style: .continuous))
        // A drawing, not text: it stays the same way round in right to left languages.
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
    }

    private func dot(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ c: Color, _ s: CGFloat) -> some View {
        Capsule().fill(c).frame(width: w * s, height: h * s).offset(x: x * s, y: y * s)
    }
}

struct ToastView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let message: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.toastCheck)
            Text(message).font(.nunito(15, .semibold)).foregroundStyle(Palette.bg)
        }
        .padding(.horizontal, 16)
        .frame(height: 44)
        .background(Palette.ink, in: Capsule())
        .transition(reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .bottom)))
    }
}

/// Selection state shared by the photo grids.
struct Selection {
    var on: Bool
    var keys: Set<String>
    var toggle: (Photo) -> Void
}

struct PhotoTile: View {
    @Environment(AppStore.self) private var store
    @Environment(\.layoutDirection) private var direction
    let photo: Photo
    let radius: CGFloat
    var selection: Selection?
    var showHeart = true
    let open: () -> Void

    var body: some View {
        let picked = selection?.keys.contains(photo.key) ?? false
        let heart = showHeart && store.favoriteKeys.contains(photo.key)
        Button {
            if let selection, selection.on { selection.toggle(photo) } else { open() }
        } label: {
            PhotoImage(photo: photo)
                .overlay(alignment: .bottomLeading) {
                    if heart {
                        // A faint dark wash in the corner keeps the white heart readable on snow or sky.
                        RadialGradient(colors: [.black.opacity(0.32), .clear], center: direction == .rightToLeft ? .bottomTrailing : .bottomLeading,
                                       startRadius: 0, endRadius: 56)
                            .frame(width: 64, height: 64)
                            .allowsHitTesting(false)
                            .overlay(alignment: .bottomLeading) {
                                Image(systemName: "heart.fill").font(.system(size: 14)).foregroundStyle(.white)
                                    .shadow(color: .black.opacity(0.45), radius: 2).padding(.leading, 8).padding(.bottom, 7)
                            }
                    }
                }
                .overlay {
                    if selection?.on == true {
                        ZStack(alignment: .topTrailing) {
                            RoundedRectangle(cornerRadius: radius, style: .continuous)
                                .fill(picked ? Palette.accent.opacity(0.18) : Color.white.opacity(0.12))
                                .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(picked ? Palette.accent : .clear, lineWidth: 3))
                            Image(systemName: picked ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 21)).foregroundStyle(.white).shadow(color: .black.opacity(0.35), radius: 2).padding(7)
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        }
        .buttonStyle(PressStyle())
        .accessibilityLabel(L.s(heart ? "favorite_photo_from" : "photo_from", ["date": L.date(photo.time, "yMMMMd")]))
        .accessibilityAddTraits(picked ? .isSelected : [])
    }
}

/// One year's photos for the chosen day: a big year header, then a mosaic.
/// Sits under its `PinnedYear` header, drawn over the header's space while that is hidden.
struct YearSection: View {
    @Environment(AppStore.self) private var store
    @Environment(\.layoutDirection) private var direction
    let group: YearGroup
    let day: MonthDay
    let selection: Selection
    let share: () -> Void

    private var year: some View {
        Text(L.num(group.year)).font(.nunito(56, .black)).tight(56).foregroundStyle(Palette.ink)
            .accessibilityAddTraits(.isHeader)
    }

    var body: some View {
        let layout = layoutMosaic(group.photos.count)
        let rows = CGFloat(layout.rows)
        VStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .center, spacing: 10) {
                    year
                    Spacer(minLength: 0)
                    Button(action: share) {
                        Image(systemName: "square.and.arrow.up").font(.system(size: 19)).foregroundStyle(Palette.ink)
                            .frame(width: 44, height: 44).contentShape(Rectangle())
                    }
                    .buttonStyle(PressStyle())
                    .accessibilityLabel(L.s("share_year", ["year": group.year]))
                }
                // One line under the year: "1 year ago · Friday, 5 photos".
                (Text(agoLabel(group.year)).foregroundStyle(Palette.ink).font(.nunito(14, .bold))
                 + Text(" · " + L.s("weekday_photos", ["weekday": weekday(year: group.year, day), "photos": L.p("photos", group.photos.count)])).foregroundStyle(Palette.muted))
                    .font(.nunito(14))
            }
            .padding(.leading, 4)

            GeometryReader { geo in
                let gap: CGFloat = 4, row: CGFloat = 54
                let cell = (geo.size.width - gap * CGFloat(mosaicColumns - 1)) / CGFloat(mosaicColumns)
                ZStack(alignment: .topLeading) {
                    ForEach(Array(group.photos.enumerated()), id: \.element.key) { i, photo in
                        let t = layout.tiles[i]
                        // Right to left, the first photo sits on the right.
                        let col = direction == .rightToLeft ? mosaicColumns - t.col - t.cols : t.col
                        PhotoTile(photo: photo, radius: 12, selection: selection) {
                            store.viewer = Viewer(photos: group.photos, start: i)
                        }
                        .environment(\.layoutDirection, direction)
                        .frame(width: cell * CGFloat(t.cols) + gap * CGFloat(t.cols - 1), height: row * CGFloat(t.rows) + gap * CGFloat(t.rows - 1))
                        .offset(x: (cell + gap) * CGFloat(col), y: (row + gap) * CGFloat(t.row))
                    }
                }
                .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
            }
            // Tiles are placed from the left edge, in a box as wide as the screen; the column above mirrors them.
            .environment(\.layoutDirection, .leftToRight)
            .frame(height: rows * 54 + (rows - 1) * 4)
        }
        .padding(.horizontal, 16)
        .dynamicTypeSize(...DynamicTypeSize.accessibility2)
        // Start where the pinned header sits; that header stays hidden until it pins.
        .padding(.top, -PinnedYear.height)
        // Space before the next year.
        .padding(.bottom, 28)
    }
}

/// Compact year bar that pins under Today's top bar while that year scrolls past.
/// Hidden while the year's big header is in its place.
struct PinnedYear: View {
    static let height: CGFloat = 44
    let group: YearGroup
    let day: MonthDay
    let barBottom: CGFloat
    @Binding var pinned: Set<Int>
    let share: () -> Void
    @State private var top: CGFloat = .infinity
    @Environment(\.displayScale) private var scale

    var body: some View {
        let on = top <= barBottom + 0.5
        HStack(spacing: 8) {
            Text(L.num(group.year)).font(.nunito(26, .black)).tight(26).foregroundStyle(Palette.ink)
            Text(L.s("ago_weekday_photos", ["ago": agoLabel(group.year), "weekday": weekday(year: group.year, day), "photos": L.p("photos", group.photos.count)]))
                .font(.nunito(14, .medium)).foregroundStyle(Palette.muted).lineLimit(1)
            Spacer(minLength: 0)
            Button(action: share) {
                Image(systemName: "square.and.arrow.up").font(.system(size: 17, weight: .medium)).foregroundStyle(Palette.ink)
                    .frame(width: 44, height: Self.height)
            }
            .accessibilityLabel(L.s("share_year", ["year": group.year]))
        }
        .padding(.leading, 20).padding(.trailing, 6)
        // Fixed 44 pt bar, so it stops growing before the accessibility sizes.
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
        .frame(height: Self.height)
        .background(.bar)
        .overlay(alignment: .bottom) { Palette.line.frame(height: 1 / scale) }
        .opacity(on ? 1 : 0)
        .allowsHitTesting(on)
        .accessibilityHidden(!on)
        .animation(.easeInOut(duration: 0.15), value: on)
        .onGeometryChange(for: CGFloat.self) { $0.frame(in: .global).minY } action: { top = $0 }
        .onChange(of: on) { _, now in
            if now { pinned.insert(group.year) } else { pinned.remove(group.year) }
        }
        .onDisappear { pinned.remove(group.year) }
    }
}

/// A browse grid, used for Favorites and album pages: 3 columns, edge to edge, square tiles.
struct SquareGrid: View {
    @Environment(AppStore.self) private var store
    let photos: [Photo]
    var showYear = false

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 3), spacing: 2) {
            ForEach(Array(photos.enumerated()), id: \.element.key) { i, photo in
                PhotoTile(photo: photo, radius: 0, showHeart: !showYear) {
                    store.viewer = Viewer(photos: photos, start: i)
                }
                .aspectRatio(1, contentMode: .fit)
                .overlay(alignment: .bottomLeading) {
                    if showYear { YearChip(year: yearOf(photo.time)).padding(6) }
                }
            }
        }
    }
}

/// Small year label on a photo tile.
struct YearChip: View {
    let year: Int

    var body: some View {
        Text(L.num(year))
            .font(.nunito(11, .heavy)).tracking(L.lang.arabicScript ? 0 : 0.2).foregroundStyle(Palette.chipInk)
            .padding(.vertical, 2).padding(.horizontal, 7)
            .background {
                ZStack {
                    Capsule().fill(.ultraThinMaterial)
                    Capsule().fill(Palette.chip)
                }
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

/// Dark bar shown while picking photos, with Share and Album.
struct SelectBar: View {
    let count: Int
    let share: () -> Void
    let album: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Text(count == 0 ? L.s("select_hint") : L.s("n_selected", ["photos": L.p("photos", count)]))
                .font(.nunito(15, .semibold)).foregroundStyle(Palette.bg)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: share) {
                Label(L.s("share"), systemImage: "square.and.arrow.up").font(.nunito(15, .semibold)).foregroundStyle(Palette.bg)
                    .padding(.horizontal, 14).frame(height: 44)
            }
            .disabled(count == 0).opacity(count == 0 ? 0.4 : 1)
            Pill(label: L.s("album"), systemImage: "plus", kind: .accent, height: 44, action: album).disabled(count == 0).fixedSize()
        }
        .padding(.leading, 20).padding(.trailing, 8)
        .frame(height: 62)
        .background(Palette.ink, in: Capsule())
        .shadow(color: .black.opacity(0.25), radius: 15, y: 10)
        .padding(.horizontal, 16)
    }
}

/// A tilted print for the welcome screen.
struct Print: View {
    let year: Int
    let label: String
    /// A 4:5 drawing from the asset catalog.
    let image: String
    let width: CGFloat
    let angle: Double

    var body: some View {
        VStack(spacing: 0) {
            Image(image).resizable().aspectRatio(4 / 5, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            HStack(alignment: .lastTextBaseline) {
                Text(L.num(year)).font(.nunito(width > 160 ? 26 : 22, .black)).tight(22).foregroundStyle(Palette.ink)
                Spacer()
                Text(label).font(.nunito(11, .medium)).foregroundStyle(Palette.muted)
            }
            .padding(.horizontal, 2).padding(.top, 8).padding(.bottom, 10)
        }
        .padding([.horizontal, .top], 8)
        .frame(width: width)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        .shadow(color: Palette.hex(0x141416, 0.14), radius: 15, y: 10)
        .rotationEffect(.degrees(angle))
    }
}

/// Strip layout: a row of year buttons that stays at the top while scrolling.
struct YearPills: View {
    let years: [Int]
    let selected: Int
    let pick: (Int) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(years, id: \.self) { y in
                    let on = y == selected
                    Button { pick(y) } label: {
                        Text(L.num(y)).font(.nunito(14, .semibold))
                            .foregroundStyle(on ? Palette.bg : Palette.ink)
                            .padding(.horizontal, 14).frame(height: 44)
                            .background(on ? Palette.ink : Palette.surface, in: Capsule())
                            .overlay(Capsule().stroke(on ? Palette.ink : Palette.line, lineWidth: 1))
                    }
                    .buttonStyle(PressStyle())
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 10)
        .background(Palette.bg)
    }
}

/// Strip layout: one year, a huge year number, then two columns of photos and buttons to step between years.
struct StripYear: View {
    @Environment(AppStore.self) private var store
    let group: YearGroup
    let day: MonthDay
    let selection: Selection
    let newer: Int?
    let older: Int?
    let share: () -> Void
    let pick: (Int) -> Void

    var body: some View {
        let photos = group.photos
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 6) {
                Text(L.num(group.year)).font(.nunito(96, .black)).tight(96).foregroundStyle(Palette.ink)
                    .accessibilityAddTraits(.isHeader)
                HStack(spacing: 10) {
                    Text(agoLabel(group.year)).foregroundStyle(Palette.ink)
                    Text(L.s("weekday_photos", ["weekday": weekday(year: group.year, day), "photos": L.p("photos", photos.count)])).foregroundStyle(Palette.muted)
                }
                .font(.nunito(15, .medium))
            }
            .padding(.horizontal, 20).padding(.top, 12)

            HStack(alignment: .top, spacing: 6) {
                ForEach(Array(masonryColumns(photos.map { tileAspect(width: $0.width, height: $0.height) }).enumerated()), id: \.offset) { _, col in
                    VStack(spacing: 6) {
                        ForEach(col, id: \.self) { i in
                            let p = photos[i]
                            PhotoTile(photo: p, radius: 12, selection: selection) {
                                store.viewer = Viewer(photos: photos, start: i)
                            }
                            .aspectRatio(tileAspect(width: p.width, height: p.height), contentMode: .fit)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .top)
                }
            }
            .padding(.horizontal, 16).padding(.top, 16)

            HStack {
                Pill(label: newer.map(L.num) ?? L.s("newest"), systemImage: "arrow.backward", kind: .secondary, height: 44) {
                    if let newer { pick(newer) }
                }
                .fixedSize().disabled(newer == nil)
                Spacer()
                Button(action: share) {
                    Label(L.s("share"), systemImage: "square.and.arrow.up").font(.nunito(15, .semibold)).foregroundStyle(Palette.muted)
                        .padding(.horizontal, 14).frame(height: 44)
                }
                Spacer()
                Pill(label: older.map(L.num) ?? L.s("oldest"), systemImage: "arrow.forward", kind: .secondary, height: 44, iconAfter: true) {
                    if let older { pick(older) }
                }
                .fixedSize().disabled(older == nil)
            }
            .padding(.horizontal, 16).padding(.top, 10)
        }
    }
}
