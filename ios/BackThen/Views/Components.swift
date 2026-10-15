import SwiftUI

/// Loads a photo from PhotoKit or app storage and fills its frame.
struct PhotoImage: View {
    let photo: Photo
    var contentMode: ContentMode = .fill
    @State private var image: UIImage?
    @Environment(\.displayScale) private var scale

    var body: some View {
        GeometryReader { geo in
            ZStack {
                // Gray placeholder for grid tiles only; the viewer shows photos on black.
                if contentMode == .fill { Palette.fill }
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: contentMode)
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()
                }
            }
            .task(id: "\(photo.key)-\(Int(geo.size.width))") {
                let px = CGSize(width: geo.size.width * scale, height: geo.size.height * scale)
                switch photo.source {
                case .device:
                    image = await PhotoLibrary.image(id: photo.ref, size: contentMode == .fit ? CGSize(width: 2048, height: 2048) : px)
                case .google:
                    let url = GooglePhotos.fileURL(photo.ref)
                    image = await Task.detached { UIImage(contentsOfFile: url.path)?.preparingThumbnail(of: px) ?? UIImage(contentsOfFile: url.path) }.value
                }
            }
        }
        .accessibilityLabel("Photo from \(photo.time.formatted(date: .long, time: .omitted))")
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
                if let systemImage { Image(systemName: systemImage).font(.system(size: height < 48 ? 14 : 18, weight: .semibold)) }
                Text(label).font(.nunito(height < 48 ? 15 : 16, .semibold))
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
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.98 : 1).animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

/// Round glass button used in the iPhone header.
struct GlassButton<Label: View>: View {
    let accessibility: String
    let action: () -> Void
    @ViewBuilder let label: () -> Label

    var body: some View {
        Button(action: action) {
            label()
                .foregroundStyle(Palette.ink)
                .frame(minWidth: 40, minHeight: 40)
                .padding(.horizontal, Label.self == Text.self ? 16 : 0)
                .background(Palette.glass, in: Capsule())
                .overlay(Capsule().stroke(Palette.line, lineWidth: 1))
        }
        .buttonStyle(PressStyle())
        .accessibilityLabel(accessibility)
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
            RoundedRectangle(cornerRadius: 14 * s)
                .fill(.white)
                .frame(width: 86 * s, height: 102 * s)
                .overlay(alignment: .top) {
                    RoundedRectangle(cornerRadius: 7 * s).fill(Palette.hex(0xFFC93C))
                        .padding(EdgeInsets(top: 8 * s, leading: 8 * s, bottom: 22 * s, trailing: 8 * s))
                }
                .rotationEffect(.degrees(12))
                .offset(x: 62 * s, y: 30 * s)
            RoundedRectangle(cornerRadius: 15 * s)
                .fill(.white)
                .frame(width: 96 * s, height: 114 * s)
                .overlay(alignment: .topLeading) {
                    ZStack(alignment: .topLeading) {
                        RoundedRectangle(cornerRadius: 8 * s).fill(Palette.hex(0xFF4B3E))
                        dot(21, 29.6, 9.5, 10.5, Palette.hex(0x1D1B2E), s)
                        dot(47.4, 29.6, 9.5, 10.5, Palette.hex(0x1D1B2E), s)
                        UnevenRoundedRectangle(bottomLeadingRadius: 11.5 * s, bottomTrailingRadius: 11.5 * s)
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
        .accessibilityHidden(true)
    }

    private func dot(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ c: Color, _ s: CGFloat) -> some View {
        Capsule().fill(c).frame(width: w * s, height: h * s).offset(x: x * s, y: y * s)
    }
}

struct ToastView: View {
    let message: String

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.hex(0xFF8B76))
            Text(message).font(.nunito(15, .semibold)).foregroundStyle(Palette.bg)
        }
        .padding(.horizontal, 16)
        .frame(height: 44)
        .background(Palette.ink, in: Capsule())
        .transition(.opacity.combined(with: .move(edge: .bottom)))
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
    let photo: Photo
    let radius: CGFloat
    var selection: Selection?
    var showHeart = true
    let open: () -> Void

    var body: some View {
        let picked = selection?.keys.contains(photo.key) ?? false
        Button {
            if let selection, selection.on { selection.toggle(photo) } else { open() }
        } label: {
            PhotoImage(photo: photo)
                .overlay(alignment: .bottomLeading) {
                    if showHeart && store.favoriteKeys.contains(photo.key) {
                        Image(systemName: "heart.fill").font(.system(size: 14)).foregroundStyle(.white)
                            .shadow(color: .black.opacity(0.45), radius: 2).padding(.leading, 8).padding(.bottom, 7)
                    }
                }
                .overlay {
                    if selection?.on == true {
                        ZStack(alignment: .topTrailing) {
                            RoundedRectangle(cornerRadius: radius)
                                .fill(picked ? Palette.accent.opacity(0.18) : Color.white.opacity(0.12))
                                .overlay(RoundedRectangle(cornerRadius: radius).strokeBorder(picked ? Palette.accent : .clear, lineWidth: 3))
                            Image(systemName: picked ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 21)).foregroundStyle(.white).shadow(color: .black.opacity(0.35), radius: 2).padding(7)
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        }
        .buttonStyle(PressStyle())
        .accessibilityAddTraits(picked ? .isSelected : [])
    }
}

/// One year's photos for the chosen day: a big year number, then a mosaic.
struct YearSection: View {
    @Environment(AppStore.self) private var store
    let group: YearGroup
    let day: MonthDay
    let selection: Selection
    let share: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            HStack(alignment: .bottom) {
                Text(String(group.year)).font(.nunito(56, .black)).tight(56).foregroundStyle(Palette.ink)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(agoLabel(group.year)).font(.nunito(14, .semibold)).foregroundStyle(Palette.ink)
                    Text("\(weekday(year: group.year, day)), \(plural(group.photos.count, "photo"))").font(.nunito(13)).foregroundStyle(Palette.muted)
                }
                .padding(.bottom, 8)
            }
            .padding(.horizontal, 4)

            GeometryReader { geo in
                let gap: CGFloat = 4, row: CGFloat = 54
                let cell = (geo.size.width - gap * CGFloat(mosaicColumns - 1)) / CGFloat(mosaicColumns)
                let layout = layoutMosaic(group.photos.count)
                ZStack(alignment: .topLeading) {
                    ForEach(Array(group.photos.enumerated()), id: \.element.key) { i, photo in
                        let t = layout.tiles[i]
                        PhotoTile(photo: photo, radius: 10, selection: selection) {
                            store.viewer = Viewer(photos: group.photos, start: i)
                        }
                        .frame(width: cell * CGFloat(t.cols) + gap * CGFloat(t.cols - 1), height: row * CGFloat(t.rows) + gap * CGFloat(t.rows - 1))
                        .offset(x: (cell + gap) * CGFloat(t.col), y: (row + gap) * CGFloat(t.row))
                    }
                }
            }
            .frame(height: {
                let rows = CGFloat(layoutMosaic(group.photos.count).rows)
                return rows * 54 + (rows - 1) * 4
            }())

            HStack {
                Spacer()
                Button(action: share) {
                    Label("Share \(String(group.year))", systemImage: "square.and.arrow.up")
                        .font(.nunito(13, .semibold)).foregroundStyle(Palette.muted)
                        .padding(.horizontal, 10).frame(height: 32)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 28)
    }
}

/// A plain 3-column grid, used for Favorites and album pages.
struct SquareGrid: View {
    @Environment(AppStore.self) private var store
    let photos: [Photo]
    var showYear = false

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 3), count: 3), spacing: 3) {
            ForEach(Array(photos.enumerated()), id: \.element.key) { i, photo in
                PhotoTile(photo: photo, radius: 4, showHeart: !showYear) {
                    store.viewer = Viewer(photos: photos, start: i)
                }
                .aspectRatio(1, contentMode: .fit)
                .overlay(alignment: .bottomLeading) {
                    if showYear {
                        Text(String(yearOf(photo.time)))
                            .font(.nunito(13, .black)).foregroundStyle(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 7).padding(.top, 16).padding(.bottom, 5)
                            .background(LinearGradient(colors: [.clear, .black.opacity(0.5)], startPoint: .top, endPoint: .bottom))
                            .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 4, bottomTrailingRadius: 4))
                            .allowsHitTesting(false)
                    }
                }
            }
        }
        .padding(.horizontal, 3)
    }
}

/// Dark bar shown while picking photos, with Share and Album.
struct SelectBar: View {
    let count: Int
    let share: () -> Void
    let album: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Text(count == 0 ? "Tap photos to select" : "\(plural(count, "photo")) selected")
                .font(.nunito(15, .semibold)).foregroundStyle(Palette.bg)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: share) {
                Label("Share", systemImage: "square.and.arrow.up").font(.nunito(15, .semibold)).foregroundStyle(Palette.bg)
                    .padding(.horizontal, 14).frame(height: 44)
            }
            .disabled(count == 0).opacity(count == 0 ? 0.4 : 1)
            Pill(label: "Album", systemImage: "plus", kind: .accent, height: 44, action: album).disabled(count == 0).fixedSize()
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
    let color: Color
    let width: CGFloat
    let angle: Double

    var body: some View {
        VStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 6).fill(color.opacity(0.85)).aspectRatio(4 / 5, contentMode: .fit)
            HStack(alignment: .lastTextBaseline) {
                Text(String(year)).font(.nunito(width > 160 ? 26 : 22, .black)).tight(22).foregroundStyle(Palette.ink)
                Spacer()
                Text(label).font(.nunito(11, .medium)).foregroundStyle(Palette.muted)
            }
            .padding(.horizontal, 2).padding(.top, 8).padding(.bottom, 10)
        }
        .padding([.horizontal, .top], 8)
        .frame(width: width)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 10))
        .shadow(color: Palette.hex(0x141416, 0.14), radius: 15, y: 10)
        .rotationEffect(.degrees(angle))
    }
}
