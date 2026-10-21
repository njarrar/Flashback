import SwiftUI
import UIKit

/// Drag a finger across photos to pick or unpick them in one move, as in the Photos app.
/// In select mode a sideways drag that starts on a photo picks; up and down still scrolls.
/// After a long press starts selecting, moving the same finger picks in any direction.
/// Everything between the first photo and the one under the finger follows the first
/// photo: picked if it was not, unpicked if it was. Near the top or bottom edge the list
/// scrolls on its own.
final class DragSelect: NSObject, UIGestureRecognizerDelegate {
    /// Each photo tile's frame in window points, kept up to date by the tiles.
    private var tiles: [String: (photo: Photo, frame: CGRect)] = [:]
    var order: () -> [Photo] = { [] }
    var selecting: Binding<Bool> = .constant(false)
    var picked: Binding<[Photo]> = .constant([])
    /// Set when a long press starts selecting, so the same finger can carry on picking.
    var armed = false

    private weak var scrollView: UIScrollView?
    private var first: Photo?
    private var last: String?
    private var before: [Photo] = []
    private var adding = true
    private var finger: CGPoint = .zero
    private var link: CADisplayLink?

    func place(_ photo: Photo, _ frame: CGRect) { tiles[photo.key] = (photo, frame) }
    func forget(_ key: String) { tiles.removeValue(forKey: key) }

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
        scrollView = scroll
        let pan = UIPanGestureRecognizer(target: self, action: #selector(pan(_:)))
        pan.delegate = self
        pan.maximumNumberOfTouches = 1
        scroll.addGestureRecognizer(pan)
        // The list waits for this drag to say no, which it does as soon as the finger moves.
        scroll.panGestureRecognizer.require(toFail: pan)
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        armed = false
        return true
    }

    func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
        guard let pan = g as? UIPanGestureRecognizer, selecting.wrappedValue,
              hit(startPoint(pan)) != nil else { return false }
        if armed { return true }
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
            first = hit(startPoint(g))
            before = picked.wrappedValue
            adding = armed || !before.contains { $0.key == first?.key }
            last = nil
            update()
            link = CADisplayLink(target: self, selector: #selector(tick))
            link?.add(to: .main, forMode: .common)
        case .changed:
            update()
        default:
            link?.invalidate()
            link = nil
            first = nil
            armed = false
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
        picked.wrappedValue = adding
            ? before + range.filter { r in !before.contains { $0.key == r.key } }
            : before.filter { !keys.contains($0.key) }
    }

    /// Scrolls while the finger rests near the top or bottom edge.
    @objc private func tick() {
        guard let scroll = scrollView, let window = scroll.window else { return }
        let box = scroll.convert(scroll.bounds, to: window)
        let zone: CGFloat = 64
        let step: CGFloat = finger.y < box.minY + zone ? -(box.minY + zone - finger.y) / 4
            : finger.y > box.maxY - zone ? (finger.y - box.maxY + zone) / 4 : 0
        guard step != 0 else { return }
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

    func makeUIView(context: Context) -> Hook { Hook(drag: drag) }
    func updateUIView(_ view: Hook, context: Context) {}

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
