import SwiftUI
import UIKit

/// Loads a photo from PhotoKit or app storage and fills its frame.
struct PhotoImage: View {
    let photo: Photo
    var contentMode: ContentMode = .fill
    @State private var image: UIImage?
    @State private var asyncLoaded = false
    @State private var failed = false
    /// Within a screen of the visible part of its scroll view. Only then does it load its
    /// photo, so a day with hundreds of photos doesn't load them all at once, and a photo
    /// scrolled far away lets its picture go.
    @State private var near = false
    @Environment(\.displayScale) private var scale
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            let validSize = contentMode == .fit || (geo.size.width > 0 && geo.size.height > 0)
            let px = contentMode == .fit
                ? CGSize(width: 2048, height: 2048)
                : validSize
                    ? ImageCache.bucket(CGSize(width: geo.size.width * scale, height: geo.size.height * scale))
                    : .zero
            // Only query the cache once geometry is non-zero, avoiding bogus 64x64 lookups.
            let cacheHit = validSize ? ImageCache.get(cacheKey(px)) : nil
            let shown = cacheHit ?? image
            ZStack {
                // Gray placeholder for grid tiles only; the viewer shows photos on black.
                if contentMode == .fill { Palette.fill }
                if let shown {
                    Image(uiImage: shown)
                        .resizable()
                        .aspectRatio(contentMode: contentMode)
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()
                        .transition(reduceMotion || !asyncLoaded ? .identity : .opacity)
                } else if failed {
                    Image(systemName: "exclamationmark.icloud")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Palette.muted)
                        .accessibilityLabel(L.s("photo_unavailable"))
                }
            }
            .animation(reduceMotion || !asyncLoaded ? nil : .easeOut(duration: 0.15), value: shown != nil)
            .task(id: "\(photo.key)-\(Int(px.width))x\(Int(px.height))-\(near)") {
                guard near else {
                    image = nil
                    asyncLoaded = false
                    failed = false
                    return
                }
                // The first layout pass can be zero by zero; wait for the real size.
                guard validSize else { return }
                failed = false
                if let hit = ImageCache.get(cacheKey(px)) {
                    asyncLoaded = false
                    image = hit
                    return
                }
                // Grid tiles show a quick copy while the sharp one loads.
                let show: @MainActor (UIImage) -> Void = { quick in
                    if image == nil {
                        asyncLoaded = true
                        image = quick
                    }
                }
                var preview: (@Sendable (UIImage) -> Void)?
                if contentMode == .fill {
                    preview = { quick in
                        Task<Void, Never> { @MainActor in show(quick) }
                    }
                }
                let loaded = await loadImage(photo, size: px, fit: contentMode == .fit, preview: preview)
                guard !Task.isCancelled else { return }
                if let loaded {
                    asyncLoaded = true
                    image = loaded
                    failed = false
                } else if image == nil {
                    failed = true
                }
            }
        }
        .onGeometryChange(for: Bool.self) { proxy in
            // Not in a scroll view: always shown.
            guard let shown = proxy.bounds(of: .scrollView) else { return true }
            return isNear(CGRect(origin: .zero, size: proxy.size), shown)
        } action: { near = $0 }
        .accessibilityLabel(L.s("photo_from", ["date": L.date(photo.time, "yMMMMd")]))
    }

    /// The same key `loadImage` stores under: PhotoKit id for phone photos, app key for samples.
    private func cacheKey(_ px: CGSize) -> NSString {
        ImageCache.key(photo.source == .device ? photo.ref : photo.key, px, fit: contentMode == .fit)
    }
}

/// True when `tile` is within one screen of `shown`, the visible part of its scroll view,
/// before or after it, in either direction.
func isNear(_ tile: CGRect, _ shown: CGRect) -> Bool {
    shown.insetBy(dx: -max(shown.width, 1), dy: -max(shown.height, 1)).intersects(tile)
}

/// Loads a photo from PhotoKit or app storage at about the given pixel size. `fit` keeps the
/// whole photo inside that size; otherwise it covers it.
func loadImage(_ photo: Photo, size px: CGSize, fit: Bool = false, preview: (@Sendable (UIImage) -> Void)? = nil) async -> UIImage? {
    switch photo.source {
    case .device:
        return await PhotoLibrary.image(id: photo.ref, size: px, fit: fit, preview: preview)
    case .sample:
        let key = ImageCache.key(photo.key, px, fit: fit)
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
    /// Shows a spinner in place of the icon and turns the button off, while its work runs.
    var busy = false
    var action: () -> Void

    @Environment(\.isEnabled) private var enabled

    var body: some View {
        let (bg, fg): (Color, Color) = switch kind {
        case .primary: (Palette.ink, Palette.bg)
        case .secondary: (Palette.surface, Palette.ink)
        case .ghost: (.clear, Palette.ink)
        case .accent: (Palette.accentFill, .white)
        case .tint: (Palette.accTint, Palette.accInk)
        }
        Button(action: action) {
            HStack(spacing: 8) {
                if busy { ProgressView().tint(fg) }
                else if let systemImage, !iconAfter { Image(systemName: systemImage).font(.system(size: height < 48 ? 14 : 18, weight: .semibold)) }
                Text(label).font(.nunito(height < 48 ? 15 : 16, .semibold))
                    .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                if let systemImage, iconAfter { Image(systemName: systemImage).font(.system(size: height < 48 ? 14 : 18, weight: .semibold)) }
            }
            .foregroundStyle(fg)
            .padding(.horizontal, height < 48 ? 16 : 20).padding(.vertical, 6)
            // Grows past its usual height when large text wraps the label.
            .frame(minHeight: height)
            .frame(maxWidth: wide ?? (kind == .ghost || height >= 54) ? .infinity : nil)
            .background(bg, in: Capsule())
            .overlay(Capsule().stroke(kind == .secondary ? Palette.line : .clear, lineWidth: 1))
            .opacity(enabled ? 1 : 0.4)
        }
        .buttonStyle(PressStyle())
        .allowsHitTesting(!busy)
    }
}

struct PressStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

/// List rows: a soft fill behind the row while it is pressed, no scaling.
struct RowPressStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Palette.fill)
                    .opacity(configuration.isPressed ? 1 : 0)
            )
            .animation(reduceMotion ? nil : .easeOut(duration: 0.1), value: configuration.isPressed)
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
    var kind: AppStore.ToastKind = .success
    var undoLabel: String? = nil
    var onUndo: (() -> Void)? = nil

    private var iconName: String {
        switch kind {
        case .success: "checkmark"
        case .info: "info.circle.fill"
        case .error: "exclamationmark.triangle.fill"
        }
    }

    private var iconColor: Color {
        switch kind {
        case .success: Palette.toastCheck
        case .info: Palette.toastInfo
        case .error: Palette.toastError
        }
    }

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: iconName)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(iconColor)
                .accessibilityHidden(true)
            Text(message)
                .font(.nunito(15, .semibold))
                .foregroundStyle(Palette.bg)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
            if let undoLabel, let onUndo {
                Button(action: onUndo) {
                    Text(undoLabel)
                        .font(.nunito(14, .bold))
                        .foregroundStyle(Palette.toastCheck)
                        .padding(.horizontal, 10)
                        .frame(minHeight: 32)
                        .background(Palette.bg.opacity(0.14), in: Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .frame(minHeight: 44)
        .background(Palette.ink, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: .black.opacity(0.22), radius: 12, y: 6)
        .transition(reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
        .onAppear {
            AccessibilityNotification.Announcement(message).post()
        }
        .onChange(of: message) { _, new in
            AccessibilityNotification.Announcement(new).post()
        }
    }
}

/// Selection state shared by the photo grids.
struct Selection {
    var on: Bool
    var keys: Set<String>
    /// Long press on a photo while not selecting: start selecting with that photo picked.
    var start: ((Photo) -> Void)? = nil
    /// Batch-updates a group of photos in a single assignment (1 haptic, 1 redraw).
    var batch: (([Photo], Bool) -> Void)? = nil
    var toggle: (Photo) -> Void
}

struct PhotoTile: View {
    @Environment(AppStore.self) private var store
    @Environment(\.layoutDirection) private var direction
    let photo: Photo
    let radius: CGFloat
    var selection: Selection?
    var showHeart = true
    var showSelectionBadge = true
    let open: () -> Void
    /// Set by a long press, so the tap that ends it doesn't also open or unpick the photo.
    @State private var longPressed = false
    @Environment(\.dragSelect) private var drag

    var body: some View {
        let picked = selection?.keys.contains(photo.key) ?? false
        let heart = showHeart && store.favoriteKeys.contains(photo.key)
        let startSelecting = selection.flatMap { $0.on ? nil : $0.start }
        let dateWithTime = "\(L.date(photo.time, "yMMMMd")), \(L.weekdayTime(photo.time))"
        Button {
            if longPressed { longPressed = false; return }
            if let selection, selection.on { selection.toggle(photo) } else { open() }
        } label: {
            PhotoImage(photo: photo)
                .overlay(alignment: .bottomLeading) {
                    if heart {
                        // A faint dark wash in the corner keeps the white heart readable on snow or sky.
                        RadialGradient(colors: [.black.opacity(0.42), .clear], center: direction == .rightToLeft ? .bottomTrailing : .bottomLeading,
                                       startRadius: 0, endRadius: 56)
                            .frame(width: 64, height: 64)
                            .allowsHitTesting(false)
                            .overlay(alignment: .bottomLeading) {
                                Image(systemName: "heart.fill").font(.system(size: 14)).foregroundStyle(.white)
                                    .shadow(color: .black.opacity(0.55), radius: 2).padding(.leading, 8).padding(.bottom, 7)
                            }
                    }
                }
                .overlay {
                    if selection?.on == true && showSelectionBadge {
                        ZStack(alignment: .topTrailing) {
                            RoundedRectangle(cornerRadius: radius, style: .continuous)
                                .fill(picked ? Palette.accentFill.opacity(0.18) : Color.black.opacity(0.08))
                                .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(picked ? Palette.accent : .clear, lineWidth: 3))
                            if picked {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 22))
                                    .symbolRenderingMode(.palette)
                                    .foregroundStyle(.white, Palette.accentFill)
                                    .shadow(color: .black.opacity(0.45), radius: 2)
                                    .padding(7)
                            } else {
                                Image(systemName: "circle")
                                    .font(.system(size: 22, weight: .semibold))
                                    .foregroundStyle(.white)
                                    .background(Circle().fill(Color.black.opacity(0.38)).padding(1))
                                    .shadow(color: .black.opacity(0.45), radius: 2)
                                    .padding(7)
                            }
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
                // A filled photo spills past its box. The spill is clipped from view but still
                // takes taps, so a tall photo would steal taps from the tiles around it.
                .contentShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        }
        .buttonStyle(PressStyle())
        .simultaneousGesture(LongPressGesture(minimumDuration: 0.4).onEnded { _ in
            guard let startSelecting else { return }
            longPressed = true
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            startSelecting(photo)
        }, isEnabled: startSelecting != nil)
        .accessibilityActions {
            if let startSelecting { Button(L.s("select")) { startSelecting(photo) } }
        }
        .accessibilityLabel(L.s(heart ? "favorite_photo_from" : "photo_from", ["date": dateWithTime]))
        .accessibilityAddTraits(picked ? .isSelected : [])
        // Tells a drag across the photos where this one sits.
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frame in
            if selection != nil { drag?.place(photo, frame) }
        }
        .onDisappear { drag?.forget(photo.key) }
    }
}

/// Builds a localized subtitle highlighting the leading "N years ago" phrase without hardcoding punctuation.
func styledYearSubtitle(year: Int, day: MonthDay, count: Int) -> AttributedString {
    let ago = agoLabel(year)
    let full = L.s("ago_weekday_photos", ["ago": ago, "weekday": weekday(year: year, day), "photos": L.p("photos", count)])
    var attr = AttributedString(full)
    attr.font = .nunito(14)
    attr.foregroundColor = Palette.muted
    if let range = attr.range(of: ago) {
        attr[range].font = .nunito(14, .bold)
        attr[range].foregroundColor = Palette.ink
    }
    return attr
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
        Text(L.num(group.year)).font(.nunito(40, .black, maxSize: 52)).tight(40).foregroundStyle(Palette.ink)
            .accessibilityAddTraits(.isHeader)
    }

    private func toggleWholeYear(_ selectAll: Bool) {
        if let batch = selection.batch {
            batch(group.photos, selectAll)
        } else {
            for p in group.photos where selection.keys.contains(p.key) != selectAll {
                selection.toggle(p)
            }
        }
    }

    var body: some View {
        let allSelected = !group.photos.isEmpty && group.photos.allSatisfy { selection.keys.contains($0.key) }
        // Keep years with >15 photos lazy by putting the hero 6 in a mosaic and the rest in LazyVGrid.
        let useLazyTail = group.photos.count > 15
        let mosaicPhotos = useLazyTail ? Array(group.photos.prefix(6)) : group.photos
        let tailPhotos = useLazyTail ? Array(group.photos.dropFirst(6)) : []
        let layout = layoutMosaic(mosaicPhotos.count)
        let rows = CGFloat(layout.rows)
        VStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(alignment: .center, spacing: 10) {
                    year
                    Spacer(minLength: 0)
                    if selection.on {
                        Button { toggleWholeYear(!allSelected) } label: {
                            Text(L.s(allSelected ? "deselect_year" : "select_year"))
                                .font(.nunito(13, .bold, maxSize: 16))
                                .foregroundStyle(allSelected ? Palette.bg : Palette.ink)
                                .padding(.horizontal, 12)
                                .frame(minHeight: 36)
                                .background(allSelected ? Palette.ink : Palette.fill, in: Capsule())
                                .frame(minHeight: 44).contentShape(Rectangle())
                        }
                        .buttonStyle(PressStyle())
                    } else {
                        Button(action: share) {
                            Image(systemName: "square.and.arrow.up").font(.system(size: 19)).foregroundStyle(Palette.ink)
                                .frame(width: 44, height: 44).contentShape(Rectangle())
                        }
                        .buttonStyle(PressStyle())
                        .accessibilityLabel(L.s("share_year", ["year": group.year]))
                    }
                }
                Text(styledYearSubtitle(year: group.year, day: day, count: group.photos.count))
            }
            .padding(.leading, 4)

            VStack(spacing: 4) {
                GeometryReader { geo in
                    let gap: CGFloat = 4, row: CGFloat = 54
                    let cell = (geo.size.width - gap * CGFloat(mosaicColumns - 1)) / CGFloat(mosaicColumns)
                    ZStack(alignment: .topLeading) {
                        ForEach(Array(mosaicPhotos.enumerated()), id: \.element.key) { i, photo in
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

                if !tailPhotos.isEmpty {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 3), spacing: 4) {
                        ForEach(Array(tailPhotos.enumerated()), id: \.element.key) { offset, photo in
                            PhotoTile(photo: photo, radius: 12, selection: selection) {
                                store.viewer = Viewer(photos: group.photos, start: 6 + offset)
                            }
                            .aspectRatio(1, contentMode: .fit)
                        }
                    }
                }
            }
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
    var selection: Selection? = nil
    let share: () -> Void
    /// Pinned right now. Only this, not the header's place, is kept, so scrolling doesn't
    /// redraw the header on every frame.
    @State private var on = false
    @Environment(\.displayScale) private var scale
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let allSelected = selection?.on == true && !group.photos.isEmpty && group.photos.allSatisfy { selection?.keys.contains($0.key) == true }
        HStack(spacing: 8) {
            Text(L.num(group.year)).font(.nunito(26, .black, maxSize: 30)).tight(26).foregroundStyle(Palette.ink)
                .accessibilityAddTraits(.isHeader)
            Text(L.s("ago_weekday_photos", ["ago": agoLabel(group.year), "weekday": weekday(year: group.year, day), "photos": L.p("photos", group.photos.count)]))
                .font(.nunito(14, .medium, maxSize: 17)).foregroundStyle(Palette.muted).lineLimit(1)
            Spacer(minLength: 0)
            if let selection, selection.on {
                Button {
                    if let batch = selection.batch {
                        batch(group.photos, !allSelected)
                    } else {
                        for p in group.photos where selection.keys.contains(p.key) == allSelected { selection.toggle(p) }
                    }
                } label: {
                    Text(L.s(allSelected ? "deselect_year" : "select_year"))
                        .font(.nunito(12, .bold, maxSize: 15))
                        .foregroundStyle(allSelected ? Palette.bg : Palette.ink)
                        .padding(.horizontal, 10).frame(height: 30)
                        .background(allSelected ? Palette.ink : Palette.fill, in: Capsule())
                        .frame(minHeight: Self.height).contentShape(Rectangle())
                }
                .padding(.trailing, 8)
            } else {
                Button(action: share) {
                    Image(systemName: "square.and.arrow.up").font(.system(size: 17, weight: .medium)).foregroundStyle(Palette.ink)
                        .frame(width: 44, height: Self.height)
                }
                .accessibilityLabel(L.s("share_year", ["year": group.year]))
            }
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
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.15), value: on)
        .onGeometryChange(for: Bool.self) { $0.frame(in: .global).minY <= barBottom + 0.5 } action: { on = $0 }
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
    var showDate = false
    var selection: Selection? = nil
    var removeLabel: String? = nil
    var onRemove: ((Photo) -> Void)? = nil

    var body: some View {
        // Same margin, gap and corners as the photos on Today.
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 3), spacing: 4) {
            ForEach(Array(photos.enumerated()), id: \.element.key) { i, photo in
                PhotoTile(photo: photo, radius: 12, selection: selection, showHeart: !showDate) {
                    store.viewer = Viewer(photos: photos, start: i)
                }
                .aspectRatio(1, contentMode: .fit)
                .overlay(alignment: .bottomLeading) {
                    // Favorites come from many days, so the chip names the whole date.
                    if showDate { DateChip(text: L.date(photo.time, "yMMMd")).padding(6) }
                }
                .contextMenu {
                    if let onRemove {
                        Button(role: .destructive) { onRemove(photo) } label: {
                            Label(removeLabel ?? L.s("remove_from_album"), systemImage: "minus.circle")
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 16)
    }
}

/// Small date label on a photo tile.
struct DateChip: View {
    let text: String

    var body: some View {
        Text(text)
            .lineLimit(1).minimumScaleFactor(0.7)
            .font(.nunito(11, .heavy, maxSize: 14)).tracking(L.lang.arabicScript ? 0 : 0.2).foregroundStyle(Palette.chipInk)
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

/// Dark bar shown while picking photos, with Select All / Clear, Share, Reel and Album.
struct SelectBar: View {
    let count: Int
    var allSelected = false
    var onToggleAll: (() -> Void)? = nil
    let share: () -> Void
    let album: () -> Void
    /// Makes a reel of the picked photos. Needs two or more.
    var reel: (() -> Void)? = nil
    @Environment(\.dynamicTypeSize) private var textSize

    var body: some View {
        VStack(spacing: 4) {
            if let onToggleAll {
                HStack(spacing: 8) {
                    Text(count == 0 ? L.s("select_hint") : L.s("n_selected", ["photos": L.p("photos", count)]))
                        .font(.nunito(14, .semibold)).foregroundStyle(Palette.bg)
                        .lineLimit(1).minimumScaleFactor(0.8)
                    Spacer(minLength: 4)
                    Button(action: onToggleAll) {
                        Text(L.s(allSelected ? "clear_selection" : "select_all"))
                            .font(.nunito(13, .bold)).foregroundStyle(Palette.toastCheck)
                            .padding(.horizontal, 10).frame(minHeight: 32)
                            .background(Palette.bg.opacity(0.14), in: Capsule())
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 12)
                .padding(.top, 4)
            }
            HStack(spacing: 2) {
                if onToggleAll == nil {
                    Text(count == 0 ? L.s("select_hint") : L.s("n_selected", ["photos": L.p("photos", count)]))
                        .font(.nunito(15, .semibold)).foregroundStyle(Palette.bg)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                Button(action: share) {
                    Image(systemName: "square.and.arrow.up").font(.system(size: 18, weight: .semibold)).foregroundStyle(Palette.bg)
                        .frame(width: 44, height: 44).contentShape(Rectangle())
                }
                .accessibilityLabel(L.s("share"))
                .disabled(count == 0).opacity(count == 0 ? 0.4 : 1)
                if let reel {
                    Button(action: reel) {
                        // With large text the word gives way to the icon, so the count keeps its room.
                        Label(L.s("reel"), systemImage: "play.rectangle").font(.nunito(15, .semibold)).foregroundStyle(Palette.bg)
                            .labelStyle(textSize >= .xxLarge ? AnyLabelStyle(.iconOnly) : AnyLabelStyle(.titleAndIcon))
                            .padding(.horizontal, 10).frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel(L.s("make_reel"))
                    .disabled(count < 2).opacity(count < 2 ? 0.4 : 1)
                }
                if onToggleAll != nil { Spacer(minLength: 4) }
                Pill(label: L.s("album"), systemImage: "plus", kind: .accent, height: 44, action: album).disabled(count == 0).fixedSize()
            }
        }
        .padding(.leading, onToggleAll != nil ? 12 : 20).padding(.trailing, 8).padding(.vertical, 6)
        .frame(minHeight: 62)
        .background(Palette.ink, in: RoundedRectangle(cornerRadius: onToggleAll != nil ? 26 : 31, style: .continuous))
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
        let shape = RoundedRectangle(cornerRadius: 10, style: .continuous)
        VStack(spacing: 0) {
            Image(image).resizable().aspectRatio(4 / 5, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            HStack(alignment: .lastTextBaseline) {
                Text(L.num(year)).font(.nunito(width > 160 ? 26 : 22, .black, fixed: true)).tight(22).foregroundStyle(Palette.ink)
                Spacer()
                Text(label).font(.nunito(11, .medium, fixed: true)).foregroundStyle(Palette.muted)
            }
            .padding(.horizontal, 2).padding(.top, 8).padding(.bottom, 10)
        }
        .padding([.horizontal, .top], 8)
        .frame(width: width)
        .background(Palette.surface, in: shape)
        .overlay(shape.stroke(Palette.line, lineWidth: 1))
        .shadow(color: Palette.hex(0x141416, 0.16), radius: 15, y: 10)
        .rotationEffect(.degrees(angle))
    }
}

/// Strip layout: a row of year buttons that stays at the top while scrolling.
struct YearPills: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let years: [Int]
    let selected: Int
    let pick: (Int) -> Void

    var body: some View {
        ScrollViewReader { proxy in
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
                        .id(y)
                    }
                }
                .padding(.horizontal, 16)
            }
            .onAppear {
                proxy.scrollTo(selected, anchor: .center)
            }
            .onChange(of: selected) { _, y in
                withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) {
                    proxy.scrollTo(y, anchor: .center)
                }
            }
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
        let allSelected = !photos.isEmpty && photos.allSatisfy { selection.keys.contains($0.key) }
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .lastTextBaseline, spacing: 8) {
                    Text(L.num(group.year))
                        .font(.nunito(96, .black, maxSize: 112))
                        .tight(96)
                        .foregroundStyle(Palette.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.45)
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 0)
                    if selection.on {
                        Button {
                            if let batch = selection.batch {
                                batch(photos, !allSelected)
                            } else {
                                for p in photos where selection.keys.contains(p.key) == allSelected { selection.toggle(p) }
                            }
                        } label: {
                            Text(L.s(allSelected ? "deselect_year" : "select_year"))
                                .font(.nunito(13, .bold))
                                .foregroundStyle(allSelected ? Palette.bg : Palette.ink)
                                .padding(.horizontal, 12).frame(minHeight: 36)
                                .background(allSelected ? Palette.ink : Palette.fill, in: Capsule())
                                .frame(minHeight: 44).contentShape(Rectangle())
                        }
                        .buttonStyle(PressStyle())
                    }
                }
                Text(styledYearSubtitle(year: group.year, day: day, count: photos.count))
            }
            .dynamicTypeSize(...DynamicTypeSize.accessibility2)
            .padding(.horizontal, 20).padding(.top, 12)

            HStack(alignment: .top, spacing: 6) {
                ForEach(Array(masonryColumns(photos.map { tileAspect(width: $0.width, height: $0.height) }).enumerated()), id: \.offset) { _, col in
                    LazyVStack(spacing: 6) {
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

            let newerButton = Pill(
                label: newer.map(L.num) ?? L.s("newest"),
                systemImage: newer != nil ? "arrow.backward" : nil,
                kind: .secondary,
                height: 44
            ) {
                if let newer { pick(newer) }
            }
            .fixedSize()
            .disabled(newer == nil)

            let shareButton = Button(action: share) {
                Label(L.s("share"), systemImage: "square.and.arrow.up").font(.nunito(15, .semibold)).foregroundStyle(Palette.muted)
                    .padding(.horizontal, 14).frame(minHeight: 44)
            }

            let olderButton = Pill(
                label: older.map(L.num) ?? L.s("oldest"),
                systemImage: older != nil ? "arrow.forward" : nil,
                kind: .secondary,
                height: 44,
                iconAfter: true
            ) {
                if let older { pick(older) }
            }
            .fixedSize()
            .disabled(older == nil)

            ViewThatFits(in: .horizontal) {
                HStack {
                    newerButton
                    Spacer()
                    shareButton
                    Spacer()
                    olderButton
                }
                VStack(spacing: 8) {
                    HStack {
                        newerButton
                        Spacer()
                        olderButton
                    }
                    shareButton
                }
            }
            .padding(.horizontal, 16).padding(.top, 10)
        }
    }
}

/// Lets a view pick a label style at run time.
struct AnyLabelStyle: LabelStyle {
    private let make: (Configuration) -> AnyView
    init<S: LabelStyle>(_ style: S) { make = { AnyView(style.makeBody(configuration: $0)) } }
    func makeBody(configuration: Configuration) -> some View { make(configuration) }
}
