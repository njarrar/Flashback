import SwiftUI
import UIKit

/// Full-screen photo viewer: swipe between photos, pinch or double tap to zoom, swipe down
/// to close, tap to hide the controls. Heart, share and add to album at the bottom.
struct PhotoViewer: View {
    @Environment(AppStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let viewer: Viewer
    @State private var index: Int
    @State private var position: Int?
    @State private var albumFor: [Photo]?
    @State private var zoomed = false
    @State private var chrome = true
    /// How far the photo has been pulled down, in points.
    @State private var drag: CGFloat = 0
    @State private var dragging = false
    private let ink = Palette.viewerInk

    init(viewer: Viewer) {
        self.viewer = viewer
        _index = State(initialValue: viewer.start)
        _position = State(initialValue: viewer.start)
    }

    var body: some View {
        let photos = viewer.photos
        let photo = photos[min(index, photos.count - 1)]
        let fav = store.favoriteKeys.contains(photo.key)
        // 0 at rest, 1 once the photo is pulled 300 pt down.
        let pull = min(max(drag, 0) / 300, 1)
        ZStack {
            Palette.viewerBg.opacity(1 - pull).ignoresSafeArea()

            ScrollViewReader { proxy in
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 0) {
                        ForEach(Array(photos.enumerated()), id: \.element.key) { i, p in
                            let active = i == index
                            ZoomPage(
                                photo: p, active: active,
                                onZoom: { if active { zoomed = $0 } },
                                onTap: { withAnimation(.easeInOut(duration: 0.2)) { chrome.toggle() } },
                                onDrag: { dy in
                                    dragging = true
                                    drag = dy
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

            VStack(spacing: 0) {
                header(photo)
                Spacer()
                if photos.count > 1 {
                    Filmstrip(photos: photos, index: index) { i in position = i }
                        .padding(.bottom, 24)
                }
                HStack {
                    action(fav ? "heart.fill" : "heart", fav ? "Remove from favorites" : "Favorite", tint: fav ? Palette.viewerHeart : ink) {
                        store.toggleFavorite(photo)
                    }
                    Spacer()
                    action("square.and.arrow.up", "Share", tint: ink) {
                        Task { await Share.photos([photo], title: photo.time.formatted(date: .long, time: .omitted)) }
                    }
                    Spacer()
                    action("rectangle.stack.badge.plus", "Add to album", tint: ink) { albumFor = [photo] }
                }
                .padding(.horizontal, 48).padding(.vertical, 8)
            }
            .opacity(chrome ? Double(1 - pull) : 0)
            .allowsHitTesting(chrome && !dragging)
            .accessibilityHidden(!chrome)
        }
        .presentationBackground(.clear)
        .overlay(alignment: .bottom) {
            if let toast = store.toast { ToastView(message: toast).padding(.bottom, 96) }
        }
        .sheet(isPresented: Binding(get: { albumFor != nil }, set: { if !$0 { albumFor = nil } })) {
            AlbumSheet(photos: albumFor ?? [], created: { album in
                store.viewer = nil
                store.openAlbumID = album.id
            })
        }
        .statusBarHidden()
    }

    private func header(_ photo: Photo) -> some View {
        HStack(spacing: 8) {
            Button { store.viewer = nil } label: {
                Image(systemName: "xmark").font(.system(size: 17, weight: .bold)).foregroundStyle(ink)
                    .frame(width: 44, height: 44).background(ink.opacity(0.14), in: Circle())
            }
            .accessibilityLabel("Close")
            VStack(spacing: 1) {
                Text(photo.time.formatted(.dateTime.month(.wide).day().year())).font(.nunito(16, .bold)).foregroundStyle(ink)
                Text("\(agoLabel(yearOf(photo.time))) · \(photo.time.formatted(.dateTime.weekday(.wide)))")
                    .font(.nunito(13)).foregroundStyle(ink.opacity(0.62))
            }
            .frame(maxWidth: .infinity)
            Color.clear.frame(width: 44, height: 44)
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
    }

    /// Close on a long enough pull or a fast flick down; otherwise spring back.
    private func endDrag(_ dy: CGFloat, _ velocity: CGFloat) {
        if dy > 120 || velocity > 800 {
            store.viewer = nil
            return
        }
        withAnimation(reduceMotion ? .easeOut(duration: 0.2) : .spring(duration: 0.4, bounce: 0)) { drag = 0 }
        dragging = false
    }

    private func action(_ icon: String, _ label: String, tint: Color, run: @escaping () -> Void) -> some View {
        Button(action: run) {
            Image(systemName: icon).font(.system(size: 26)).foregroundStyle(tint).frame(width: 56, height: 56)
        }
        .accessibilityLabel(label)
    }
}

/// Small thumbnails of every photo, with the current one wider and outlined.
private struct Filmstrip: View {
    let photos: [Photo]
    let index: Int
    let pick: (Int) -> Void

    var body: some View {
        GeometryReader { geo in
            ScrollViewReader { proxy in
                ScrollView(.horizontal) {
                    HStack(spacing: 2) {
                        ForEach(Array(photos.enumerated()), id: \.element.key) { i, p in
                            let on = i == index
                            Button { pick(i) } label: {
                                PhotoImage(photo: p)
                                    .frame(width: on ? 60 : 30, height: 44)
                                    .clipShape(RoundedRectangle(cornerRadius: 2, style: .continuous))
                                    .opacity(on ? 1 : 0.7)
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
                            .accessibilityLabel("Photo \(i + 1) of \(photos.count)")
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
                    withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(i, anchor: .center) }
                }
            }
        }
        .frame(height: 52)
    }
}

/// One page of the viewer: loads the photo large and hands it to a zooming scroll view.
private struct ZoomPage: View {
    let photo: Photo
    let active: Bool
    let onZoom: (Bool) -> Void
    let onTap: () -> Void
    let onDrag: (CGFloat) -> Void
    let onDragEnd: (CGFloat, CGFloat) -> Void
    @State private var image: UIImage?

    var body: some View {
        ZoomView(image: image, active: active, onZoom: onZoom, onTap: onTap, onDrag: onDrag, onDragEnd: onDragEnd)
            .task(id: photo.key) { image = await loadImage(photo, size: CGSize(width: 2048, height: 2048)) }
            .accessibilityElement()
            .accessibilityLabel("Photo from \(photo.time.formatted(date: .long, time: .omitted))")
            .accessibilityAddTraits(.isImage)
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
        let p = g.location(in: imageView)
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
