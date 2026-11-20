import Photos
import SwiftUI
import UIKit

/// Full-screen photo viewer: swipe between photos, pinch or double tap to zoom, swipe down
/// to close, tap to hide the controls. Heart, share, album, and info at the bottom.
struct PhotoViewer: View {
    @Environment(AppStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let viewer: Viewer
    @State private var index: Int
    @State private var position: Int?
    @State private var albumFor: [Photo]?
    @State private var infoPhoto: Photo?
    @State private var zoomed = false
    @State private var chrome = true
    /// How far the photo has been pulled down, in points.
    @State private var drag: CGFloat = 0
    @State private var dragging = false
    /// The photo is being got ready to share.
    @State private var sharing = false
    @State private var shareTask: Task<Void, Never>?
    private let ink = Palette.viewerInk

    init(viewer: Viewer) {
        self.viewer = viewer
        _index = State(initialValue: viewer.start)
        _position = State(initialValue: viewer.start)
    }

    var body: some View {
        let photos = viewer.photos
        if photos.isEmpty {
            Color.clear.onAppear { store.viewer = nil }
        } else {
            viewerBody(photos: photos)
        }
    }

    private func viewerBody(photos: [Photo]) -> some View {
        let safeIndex = min(max(index, 0), photos.count - 1)
        let photo = photos[safeIndex]
        let fav = store.favoriteKeys.contains(photo.key)
        // 0 at rest, 1 once the photo is pulled 300 pt down.
        let pull = min(max(drag, 0) / 300, 1)
        let chromeOpacity = chrome ? Double(1 - pull) : 0

        ZStack {
            Palette.viewerBg.opacity(1 - pull).ignoresSafeArea()

            ScrollViewReader { proxy in
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 0) {
                        ForEach(Array(photos.enumerated()), id: \.element.key) { i, p in
                            let active = i == safeIndex
                            ZoomPage(
                                photo: p,
                                active: active,
                                onZoom: { if active { zoomed = $0 } },
                                onTap: { withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.2)) { chrome.toggle() } },
                                onClose: { store.viewer = nil },
                                onDrag: { dy in
                                    dragging = true
                                    drag = max(0, dy)
                                },
                                onDragEnd: endDrag
                            )
                            .containerRelativeFrame([.horizontal, .vertical])
                            .scaleEffect(active ? 1 - 0.15 * pull : 1)
                            .offset(y: active ? drag : 0)
                            .id(i)
                        }
                    }
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.paging)
                .scrollPosition(id: $position)
                .scrollIndicators(.hidden)
                .scrollDisabled(zoomed || dragging)
                .ignoresSafeArea()
                .onChange(of: position) { _, p in
                    if let p, p != index {
                        index = p
                        zoomed = false
                    }
                }
                // Open on the tapped photo.
                .onAppear { proxy.scrollTo(viewer.start) }
            }

            // Protective top and bottom scrims keep white chrome readable over bright photos.
            VStack(spacing: 0) {
                LinearGradient(colors: [.black.opacity(0.68), .clear], startPoint: .top, endPoint: .bottom)
                    .frame(height: 130)
                    .ignoresSafeArea(edges: .top)
                Spacer()
                LinearGradient(colors: [.clear, .black.opacity(0.76)], startPoint: .top, endPoint: .bottom)
                    .frame(height: 200)
                    .ignoresSafeArea(edges: .bottom)
            }
            .opacity(chromeOpacity)
            .allowsHitTesting(false)
            .accessibilityHidden(true)

            VStack(spacing: 0) {
                header(photo, count: photos.count, current: safeIndex)
                Spacer()
                if photos.count > 1 {
                    Filmstrip(photos: photos, index: safeIndex) { i in
                        withAnimation(reduceMotion ? nil : .easeOut(duration: 0.22)) {
                            position = i
                        }
                    }
                    .padding(.bottom, 20)
                }
                HStack(spacing: 4) {
                    action(fav ? "heart.fill" : "heart", L.s("favorite_short"), L.s(fav ? "unfavorite" : "favorite"), tint: fav ? Palette.viewerHeart : ink) {
                        store.toggleFavorite(photo)
                    }
                    Spacer()
                    action("square.and.arrow.up", L.s("share"), L.s("share"), tint: ink, busy: sharing) {
                        sharePhoto(photo)
                    }
                    Spacer()
                    action("rectangle.stack.badge.plus", L.s("add_to_album"), L.s("add_to_album"), tint: ink) {
                        albumFor = [photo]
                    }
                    Spacer()
                    action("info.circle", L.s("info"), L.s("info"), tint: ink) {
                        infoPhoto = photo
                    }
                }
                .dynamicTypeSize(...DynamicTypeSize.accessibility2)
                .padding(.horizontal, 16).padding(.vertical, 8)
            }
            .opacity(chromeOpacity)
            .allowsHitTesting(chrome && !dragging)
            .accessibilityHidden(!chrome)
        }
        .presentationBackground(.clear)
        .overlay(alignment: .top) {
            if let toast = store.toast {
                ToastView(
                    message: toast,
                    kind: store.toastKind,
                    undoLabel: store.toastUndoLabel,
                    onUndo: store.toastUndoLabel != nil ? { store.triggerToastUndo() } : nil
                )
                .padding(.top, 8)
                .padding(.horizontal, 16)
            }
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: store.toast)
        .sheet(isPresented: Binding(get: { albumFor != nil }, set: { if !$0 { albumFor = nil } })) {
            AlbumSheet(photos: albumFor ?? [], created: { album in
                store.viewer = nil
                store.openAlbumID = album.id
            })
        }
        .sheet(item: $infoPhoto) { p in
            PhotoInfoSheet(photo: p) {
                infoPhoto = nil
                store.viewer = nil
                store.day = MonthDay.of(p.time)
                store.tab = .today
            }
        }
        .statusBarHidden(!chrome)
        .sensoryFeedback(.impact(weight: .light), trigger: store.saved.favorites.count) { old, new in new > old }
        .sensoryFeedback(.selection, trigger: index)
        .onDisappear { shareTask?.cancel() }
    }

    private func sharePhoto(_ photo: Photo) {
        guard !sharing else { return }
        sharing = true
        shareTask?.cancel()
        shareTask = Task {
            let ok = await Share.photos([photo], title: L.date(photo.time, "yMMMMd"))
            guard !Task.isCancelled else { return }
            sharing = false
            if !ok {
                store.showToast(L.s("share_failed"), kind: .error, undoLabel: nil, undo: nil)
            }
        }
    }

    private func header(_ photo: Photo, count: Int, current: Int) -> some View {
        let sub = L.s("ago_weekday", ["ago": agoLabel(yearOf(photo.time)), "weekday": L.weekdayTime(photo.time)])
        // Close on one side, "3 of 5" on the other, the date between them.
        return HStack(spacing: 8) {
            Button { store.viewer = nil } label: {
                Image(systemName: "xmark").font(.system(size: 17, weight: .bold)).foregroundStyle(ink)
                    .frame(width: 44, height: 44).background(ink.opacity(0.18), in: Circle())
            }
            .accessibilityLabel(L.s("close"))
            VStack(spacing: 1) {
                Text(L.date(photo.time, "yMMMMd")).font(.nunito(16, .bold, maxSize: 20)).foregroundStyle(ink)
                    .lineLimit(1).minimumScaleFactor(0.8)
                Text(sub).font(.nunito(13, .regular, maxSize: 16)).foregroundStyle(ink.opacity(0.75)).lineLimit(2)
            }
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            // Where you are in the set: "3 of 5". Never cut short.
            if count > 1 {
                Text(L.s("n_of_total", ["n": current + 1, "total": count]))
                    .font(.nunito(13, .bold, maxSize: 16)).monospacedDigit().foregroundStyle(ink)
                    .fixedSize()
                    .padding(.horizontal, 10).frame(minWidth: 44, minHeight: 28)
                    .background(ink.opacity(0.18), in: Capsule())
            } else {
                Color.clear.frame(width: 44, height: 44)
            }
        }
        .dynamicTypeSize(...DynamicTypeSize.accessibility2)
        .padding(.horizontal, 12).padding(.vertical, 8)
    }

    /// Close on a long enough pull or a fast flick down; otherwise spring back.
    private func endDrag(_ dy: CGFloat, _ velocity: CGFloat) {
        if dy > 0 && (dy > 120 || velocity > 800) {
            dragging = false
            store.viewer = nil
            return
        }
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .spring(duration: 0.4, bounce: 0)) {
            drag = 0
        } completion: {
            dragging = false
        }
    }

    /// An icon with a short word under it.
    private func action(_ icon: String, _ word: String, _ label: String, tint: Color, busy: Bool = false, run: @escaping () -> Void) -> some View {
        Button(action: run) {
            VStack(spacing: 4) {
                Group {
                    if busy { ProgressView().tint(tint) } else { Image(systemName: icon).font(.system(size: 22)).foregroundStyle(tint) }
                }
                .frame(height: 28)
                Text(word)
                    .font(.nunito(11, .semibold, maxSize: 14))
                    .foregroundStyle(ink.opacity(0.85))
                    .lineLimit(2)
                    .minimumScaleFactor(0.75)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(minWidth: 64, minHeight: 56)
            .contentShape(Rectangle())
        }
        .disabled(busy)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.isButton)
    }
}

/// Metadata & location inspector sheet for a photo.
private struct PhotoInfoSheet: View {
    let photo: Photo
    let showThisDay: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var placeLabel: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Text(L.s("info")).font(.nunito(22, .bold)).foregroundStyle(Palette.ink)
                        .accessibilityAddTraits(.isHeader)
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark").font(.system(size: 14, weight: .bold)).foregroundStyle(Palette.muted2)
                            .frame(width: 36, height: 36).background(Palette.fill, in: Circle())
                            .frame(width: 44, height: 44).contentShape(Rectangle())
                    }
                    .accessibilityLabel(L.s("close"))
                }

                VStack(alignment: .leading, spacing: 12) {
                    infoRow(
                        icon: "calendar",
                        title: L.date(photo.time, "yMMMMd"),
                        detail: L.s("ago_weekday", ["ago": agoLabel(yearOf(photo.time)), "weekday": L.weekdayTime(photo.time)])
                    )
                    if let placeLabel {
                        infoRow(icon: "mappin.and.ellipse", title: placeLabel, detail: nil)
                    }
                    if photo.width > 0 && photo.height > 0 {
                        let mp = Double(photo.width * photo.height) / 1_000_000.0
                        let mpStr = mp >= 1 ? String(format: "%.1f MP", mp) : nil
                        infoRow(
                            icon: "photo",
                            title: "\(L.num(photo.width)) × \(L.num(photo.height))",
                            detail: mpStr
                        )
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Palette.bg, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                Pill(label: L.s("show_this_day"), systemImage: "calendar", kind: .primary, height: 50, action: showThisDay)
            }
            .padding(20)
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.medium])
        .presentationCornerRadius(32)
        .presentationBackground(Palette.surface)
        .presentationDragIndicator(.visible)
        .task(id: photo.key) {
            guard photo.source == .device else { return }
            let ref = photo.ref
            let coord: (Double, Double)? = await Task.detached(priority: .utility) {
                guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [ref], options: nil).firstObject,
                      let loc = asset.location?.coordinate else { return nil }
                return (loc.latitude, loc.longitude)
            }.value
            guard let (lat, lon) = coord, let cities = await CityList.load(),
                  let cid = cities.city(lat: lat, lon: lon), cid < cities.cities.count else { return }
            let city = cities.cities[cid]
            let country = L.locale.localizedString(forRegionCode: city.country) ?? city.country
            placeLabel = "\(city.name), \(country)"
        }
    }

    private func infoRow(icon: String, title: String, detail: String?) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Palette.muted2)
                .frame(width: 24, height: 24)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.nunito(16, .semibold)).foregroundStyle(Palette.ink)
                if let detail {
                    Text(detail).font(.nunito(13)).foregroundStyle(Palette.muted)
                }
            }
        }
    }
}

/// Small thumbnails of every photo, with the current one wider and outlined.
private struct Filmstrip: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let photos: [Photo]
    let index: Int
    let pick: (Int) -> Void

    var body: some View {
        GeometryReader { geo in
            ScrollViewReader { proxy in
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 2) {
                        ForEach(Array(photos.enumerated()), id: \.element.key) { i, p in
                            let on = i == index
                            if i > 0 && yearOf(p.time) != yearOf(photos[i - 1].time) {
                                Rectangle()
                                    .fill(Palette.viewerInk.opacity(0.35))
                                    .frame(width: 1, height: 28)
                                    .padding(.horizontal, 4)
                                    .accessibilityHidden(true)
                            }
                            Button { pick(i) } label: {
                                PhotoImage(photo: p)
                                    .frame(width: on ? 60 : 44, height: 44)
                                    .clipShape(RoundedRectangle(cornerRadius: 2, style: .continuous))
                                    .opacity(on ? 1 : 0.72)
                                    .overlay {
                                        if on {
                                            RoundedRectangle(cornerRadius: 4, style: .continuous)
                                                .stroke(Palette.viewerInk, lineWidth: 2)
                                                .padding(-2)
                                        }
                                    }
                                    .padding(.horizontal, on ? 4 : 0)
                            }
                            .buttonStyle(.plain)
                            .id(i)
                            .accessibilityLabel(L.s("photo_n_of", ["n": i + 1, "total": photos.count]))
                            .accessibilityAddTraits(on ? .isSelected : [])
                        }
                    }
                    // Room on both sides so the first and last photos can sit in the middle.
                    .padding(.horizontal, max(0, geo.size.width / 2 - 34))
                    .frame(height: 52)
                }
                .scrollIndicators(.hidden)
                .onAppear { proxy.scrollTo(index, anchor: .center) }
                .onChange(of: index) { _, i in
                    if reduceMotion {
                        proxy.scrollTo(i, anchor: .center)
                    } else {
                        withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(i, anchor: .center) }
                    }
                }
            }
        }
        .frame(height: 52)
    }
}

/// One page of the viewer: shows any cached preview immediately, upgrades to full 2048px,
/// displays a frosted HD loading pill while fetching, and offers Retry if unavailable.
private struct ZoomPage: View {
    let photo: Photo
    let active: Bool
    let onZoom: (Bool) -> Void
    let onTap: () -> Void
    let onClose: () -> Void
    let onDrag: (CGFloat) -> Void
    let onDragEnd: (CGFloat, CGFloat) -> Void
    @State private var image: UIImage?
    @State private var loadingHD = false
    @State private var failed = false
    @State private var attempt = 0

    var body: some View {
        ZStack {
            ZoomView(image: image, active: active, onZoom: onZoom, onTap: onTap, onDrag: onDrag, onDragEnd: onDragEnd)

            if failed && image == nil {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.icloud")
                        .font(.system(size: 32, weight: .medium))
                        .foregroundStyle(Palette.viewerInk.opacity(0.75))
                    Text(L.s("photo_unavailable"))
                        .font(.nunito(16, .semibold))
                        .foregroundStyle(Palette.viewerInk)
                        .multilineTextAlignment(.center)
                    Pill(label: L.s("retry"), systemImage: "arrow.clockwise", kind: .secondary, height: 44) {
                        attempt &+= 1
                    }
                    .fixedSize()
                }
                .padding(24)
            } else if loadingHD {
                VStack {
                    Spacer()
                    HStack(spacing: 8) {
                        ProgressView().tint(Palette.viewerInk)
                        Text(L.s("loading_hd"))
                            .font(.nunito(13, .semibold))
                            .foregroundStyle(Palette.viewerInk)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(.ultraThinMaterial, in: Capsule())
                    .background(Color.black.opacity(0.45), in: Capsule())
                    .padding(.bottom, 150)
                }
                .allowsHitTesting(false)
            }
        }
        // The whole photo inside 2048 px. Asking to fill 2048 px gave a panorama about
        // 8,000 px wide. A page swiped far away lets its photo go, and loads it again on return.
        .task(id: "\(photo.key)-\(attempt)") {
            failed = false
            loadingHD = true
            // Try common grid cache sizes for an instant frame-1 placeholder.
            if image == nil {
                let id = photo.source == .device ? photo.ref : photo.key
                let candidates: [CGFloat] = [256, 384, 512, 192, 320, 640]
                for side in candidates {
                    if let hit = ImageCache.get(ImageCache.key(id, CGSize(width: side, height: side), fit: false)) {
                        image = hit
                        break
                    }
                }
            }
            let preview: @Sendable (UIImage) -> Void = { quick in
                Task<Void, Never> { @MainActor in
                    if image == nil { image = quick }
                }
            }
            let loaded = await loadImage(photo, size: CGSize(width: 2048, height: 2048), fit: true, preview: preview)
            guard !Task.isCancelled else { return }
            loadingHD = false
            if let loaded {
                image = loaded
                failed = false
            } else if image == nil {
                failed = true
            }
        }
        .onDisappear {
            image = nil
            loadingHD = false
        }
        .accessibilityElement(children: failed && image == nil ? .contain : .ignore)
        .accessibilityLabel(L.s("photo_from", ["date": "\(L.date(photo.time, "yMMMMd")), \(L.weekdayTime(photo.time))"]))
        .accessibilityAddTraits(.isImage)
        .accessibilityHint(L.s("options"))
        .accessibilityAction(.escape, onClose)
        .accessibilityAction(named: L.s("close"), onClose)
    }
}

private struct ZoomView: UIViewRepresentable {
    let image: UIImage?
    let active: Bool
    let onZoom: (Bool) -> Void
    let onTap: () -> Void
    let onDrag: (CGFloat) -> Void
    let onDragEnd: (CGFloat, CGFloat) -> Void

    func makeUIView(context: Context) -> ZoomScrollView { ZoomScrollView() }

    func updateUIView(_ view: ZoomScrollView, context: Context) {
        view.image = image
        view.onZoom = onZoom
        view.onTap = onTap
        view.onDrag = onDrag
        view.onDragEnd = onDragEnd
        // A page that is swiped away goes back to fit.
        if !active && view.zoomScale != 1 { view.setZoomScale(1, animated: false) }
    }
}

/// UIKit scroll view for native pinch, pan and bounce. 1x to 4x, double tap toggles 2.5x
/// at the tap, one tap toggles the controls, and a downward pull at 1x reports its distance.
private final class ZoomScrollView: UIScrollView, UIScrollViewDelegate {
    let imageView = UIImageView()
    var onZoom: ((Bool) -> Void)?
    var onTap: (() -> Void)?
    var onDrag: ((CGFloat) -> Void)?
    var onDragEnd: ((CGFloat, CGFloat) -> Void)?
    private let pullDelegate = PullDelegate()
    private var fittedTo = CGSize.zero
    private var zoomedIn = false

    var image: UIImage? {
        didSet {
            guard image !== oldValue else { return }
            imageView.image = image
            setZoomScale(1, animated: false)
            fit()
        }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        delegate = self
        minimumZoomScale = 1
        maximumZoomScale = 4
        bouncesZoom = true
        showsVerticalScrollIndicator = false
        showsHorizontalScrollIndicator = false
        contentInsetAdjustmentBehavior = .never
        decelerationRate = .fast
        backgroundColor = .clear
        imageView.contentMode = .scaleAspectFit
        addSubview(imageView)

        let double = UITapGestureRecognizer(target: self, action: #selector(doubleTap(_:)))
        double.numberOfTapsRequired = 2
        addGestureRecognizer(double)
        let single = UITapGestureRecognizer(target: self, action: #selector(singleTap))
        single.require(toFail: double)
        addGestureRecognizer(single)
        let pan = UIPanGestureRecognizer(target: self, action: #selector(pull(_:)))
        pullDelegate.view = self
        pan.delegate = pullDelegate
        addGestureRecognizer(pan)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    override func layoutSubviews() {
        super.layoutSubviews()
        if bounds.size != fittedTo {
            setZoomScale(1, animated: false)
            fit()
        }
        center()
    }

    /// Sizes the image to fit the page at 1x.
    private func fit() {
        fittedTo = bounds.size
        guard let image, bounds.width > 0, bounds.height > 0, image.size.width > 0, image.size.height > 0 else {
            imageView.frame = .zero
            contentSize = .zero
            return
        }
        let s = min(bounds.width / image.size.width, bounds.height / image.size.height)
        let size = CGSize(width: image.size.width * s, height: image.size.height * s)
        imageView.frame = CGRect(origin: .zero, size: size)
        contentSize = size
        center()
    }

    /// Keeps the image centered while it is smaller than the page.
    private func center() {
        let dx = max(0, (bounds.width - contentSize.width) / 2)
        let dy = max(0, (bounds.height - contentSize.height) / 2)
        let inset = UIEdgeInsets(top: dy, left: dx, bottom: dy, right: dx)
        if contentInset != inset { contentInset = inset }
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { imageView }

    func scrollViewDidZoom(_ scrollView: UIScrollView) {
        center()
        let now = zoomScale > 1.01
        if now != zoomedIn {
            zoomedIn = now
            onZoom?(now)
        }
    }

    @objc private func doubleTap(_ g: UITapGestureRecognizer) {
        if zoomScale > 1.01 {
            setZoomScale(1, animated: true)
            return
        }
        guard imageView.bounds.width > 0, imageView.bounds.height > 0 else { return }
        let raw = g.location(in: imageView)
        let p = CGPoint(
            x: min(max(raw.x, 0), imageView.bounds.width),
            y: min(max(raw.y, 0), imageView.bounds.height)
        )
        let scale: CGFloat = 2.5
        let w = bounds.width / scale, h = bounds.height / scale
        zoom(to: CGRect(x: p.x - w / 2, y: p.y - h / 2, width: w, height: h), animated: true)
    }

    @objc private func singleTap() { onTap?() }

    @objc private func pull(_ g: UIPanGestureRecognizer) {
        // Measured against the window, since the page itself moves with the finger.
        let dy = g.translation(in: window).y
        switch g.state {
        case .began, .changed: onDrag?(dy)
        case .ended, .cancelled, .failed: onDragEnd?(dy, g.velocity(in: window).y)
        default: break
        }
    }
}

/// Lets the swipe-down start only at 1x and only when it is mostly downward.
private final class PullDelegate: NSObject, UIGestureRecognizerDelegate {
    weak var view: ZoomScrollView?

    func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
        guard let view, let pan = g as? UIPanGestureRecognizer else { return false }
        let v = pan.velocity(in: view)
        return view.zoomScale <= 1.01 && v.y > 0 && v.y > abs(v.x)
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        other === view?.panGestureRecognizer
    }
}
