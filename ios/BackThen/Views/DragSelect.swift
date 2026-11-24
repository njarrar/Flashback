import SwiftUI
import UIKit

/// Weak proxy so `CADisplayLink` never retains `DragSelect`.
@MainActor
private final class DisplayLinkProxy: NSObject {
    weak var target: DragSelect?
    init(_ target: DragSelect) { self.target = target }
    @objc func tick(_ link: CADisplayLink) { target?.tick(link) }
}

/// Drag a finger across photos to pick or unpick them in one move, as in the Photos app.
/// In select mode a sideways drag that starts on a photo picks; up and down still scrolls.
/// After a long press starts selecting, moving the same finger picks in any direction.
/// Everything between the first photo and the one under the finger follows the first
/// photo: picked if it was not, unpicked if it was. Near the top or bottom edge the list
/// scrolls on its own.
@MainActor
final class DragSelect: NSObject, UIGestureRecognizerDelegate {
    /// Each photo tile's frame in window points, kept up to date by the tiles.
    private var tiles: [String: (photo: Photo, frame: CGRect)] = [:]
    var order: () -> [Photo] = { [] }
    var selecting: Binding<Bool> = .constant(false)
    var picked: Binding<[Photo]> = .constant([])
    /// Set when a long press starts selecting, so the same finger can carry on picking.
    var armed = false
    private var armedPhoto: Photo?

    private weak var scrollView: UIScrollView?
    private weak var panRecognizer: UIPanGestureRecognizer?
    /// Whether this screen currently accepts drag-select (e.g. false in Year Cards).
    private var active = false
    /// Whether select mode is currently on.
    private var enabled = false
    private var first: Photo?
    private var last: String?
    private var before: [Photo] = []
    private var adding = true
    private var finger: CGPoint = .zero
    nonisolated(unsafe) private var link: CADisplayLink?

    deinit {
        link?.invalidate()
    }

    func place(_ photo: Photo, _ frame: CGRect) { tiles[photo.key] = (photo, frame) }
    func forget(_ key: String) { tiles.removeValue(forKey: key) }

    /// Arms continuous drag-selection from a long-press on `photo` while the same finger stays down.
    func arm(_ photo: Photo? = nil) {
        armed = true
        armedPhoto = photo
        enabled = true
    }

    func setEnabled(_ on: Bool, active: Bool = true) {
        self.active = active
        enabled = on
        // Keep the pan recognizer enabled whenever the screen supports drag-select so it receives
        // touchesBegan on the long-press touch; gestureRecognizerShouldBegin gates activation.
        panRecognizer?.isEnabled = active
        if !active || (!on && !armed) { cancel() }
    }

    func cancel() {
        link?.invalidate()
        link = nil
        first = nil
        last = nil
        armed = false
        armedPhoto = nil
    }

    func detach() {
        cancel()
        if let pan = panRecognizer, let scroll = scrollView {
            scroll.removeGestureRecognizer(pan)
        }
        panRecognizer = nil
        scrollView = nil
    }

    private func hit(_ point: CGPoint) -> Photo? {
        tiles.values.first { $0.frame.contains(point) }?.photo
    }

    /// Where the finger first touched.
    private func startPoint(_ pan: UIPanGestureRecognizer) -> CGPoint {
        let at = pan.location(in: nil), moved = pan.translation(in: nil)
        return CGPoint(x: at.x - moved.x, y: at.y - moved.y)
    }

    func attach(to scroll: UIScrollView) {
        guard scrollView !== scroll else { return }
        detach()
        scrollView = scroll
        let pan = UIPanGestureRecognizer(target: self, action: #selector(pan(_:)))
        pan.delegate = self
        pan.maximumNumberOfTouches = 1
        pan.isEnabled = active
        scroll.addGestureRecognizer(pan)
        panRecognizer = pan
        // Fails immediately on first move when not selecting and not armed, so vertical scroll is unaffected.
        scroll.panGestureRecognizer.require(toFail: pan)
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        // Only clear stale armed state when a brand-new touch begins while idle (never mid-gesture).
        if g.state == .possible && first == nil {
            armed = false
            armedPhoto = nil
        }
        return active
    }

    func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
        guard active, let pan = g as? UIPanGestureRecognizer else { return false }
        if armed {
            return armedPhoto != nil || hit(startPoint(pan)) != nil
        }
        guard enabled, selecting.wrappedValue, hit(startPoint(pan)) != nil else { return false }
        let v = pan.velocity(in: nil)
        return abs(v.x) > abs(v.y)
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        true
    }

    @objc private func pan(_ g: UIPanGestureRecognizer) {
        finger = g.location(in: nil)
        switch g.state {
        case .began:
            first = armed ? (armedPhoto ?? hit(startPoint(g))) : hit(startPoint(g))
            before = picked.wrappedValue
            adding = armed || !before.contains { $0.key == first?.key }
            last = nil
            update()
            link?.invalidate()
            let proxy = DisplayLinkProxy(self)
            let dl = CADisplayLink(target: proxy, selector: #selector(DisplayLinkProxy.tick(_:)))
            dl.add(to: .main, forMode: .common)
            link = dl
        case .changed:
            update()
        default:
            cancel()
        }
    }

    private func update() {
        guard let first, let now = hit(finger), now.key != last else { return }
        last = now.key
        let all = order()
        guard let i = all.firstIndex(where: { $0.key == first.key }),
              let j = all.firstIndex(where: { $0.key == now.key }) else { return }
        let range = all[min(i, j)...max(i, j)]
        let keys = Set(range.map(\.key))
        if adding {
            let beforeKeys = Set(before.map(\.key))
            picked.wrappedValue = before + range.filter { !beforeKeys.contains($0.key) }
        } else {
            picked.wrappedValue = before.filter { !keys.contains($0.key) }
        }
    }

    /// Scrolls while the finger rests near the top or bottom edge, normalized to 60Hz so 120Hz displays match speed.
    fileprivate func tick(_ link: CADisplayLink) {
        guard let scroll = scrollView, let window = scroll.window else {
            cancel()
            return
        }
        let box = scroll.convert(scroll.bounds, to: window)
        let zone: CGFloat = 64
        let rawStep: CGFloat = finger.y < box.minY + zone ? -(box.minY + zone - finger.y) / 4
            : finger.y > box.maxY - zone ? (finger.y - box.maxY + zone) / 4 : 0
        guard rawStep != 0 else { return }
        let dt = min(max(link.targetTimestamp - link.timestamp, 1.0 / 240.0), 1.0 / 20.0)
        let step = rawStep * CGFloat(dt * 60.0)
        let top = -scroll.adjustedContentInset.top
        let bottom = max(top, scroll.contentSize.height + scroll.adjustedContentInset.bottom - scroll.bounds.height)
        scroll.contentOffset.y = min(max(scroll.contentOffset.y + step, top), bottom)
        last = nil
        update()
    }
}

/// Finds the scroll view around it and gives it the drag-to-pick gesture.
struct DragSelectHook: UIViewRepresentable {
    let drag: DragSelect
    let enabled: Bool
    var active: Bool = true
    let order: () -> [Photo]
    let selecting: Binding<Bool>
    let picked: Binding<[Photo]>

    func makeUIView(context: Context) -> Hook { Hook(drag: drag) }

    func updateUIView(_ view: Hook, context: Context) {
        drag.order = order
        drag.selecting = selecting
        drag.picked = picked
        drag.setEnabled(enabled, active: active)
    }

    static func dismantleUIView(_ uiView: Hook, coordinator: ()) {
        uiView.drag.detach()
    }

    final class Hook: UIView {
        let drag: DragSelect
        init(drag: DragSelect) {
            self.drag = drag
            super.init(frame: .zero)
            isUserInteractionEnabled = false
        }
        required init?(coder: NSCoder) { nil }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            guard window != nil else {
                drag.cancel()
                return
            }
            var view = superview
            while let v = view, !(v is UIScrollView) { view = v.superview }
            if let scroll = view as? UIScrollView { drag.attach(to: scroll) }
        }
    }
}

private struct DragSelectKey: EnvironmentKey {
    static let defaultValue: DragSelect? = nil
}

extension EnvironmentValues {
    /// Set by a screen that lets a finger drag across its photos; tiles report where they are.
    var dragSelect: DragSelect? {
        get { self[DragSelectKey.self] }
        set { self[DragSelectKey.self] = newValue }
    }
}
