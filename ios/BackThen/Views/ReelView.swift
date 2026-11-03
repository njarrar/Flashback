import SwiftUI
import UIKit

/// Photos to make a reel from.
struct ReelRequest: Identifiable {
    let id = UUID()
    let photos: [Photo]
    var tab: ReelView.ReelTab = .stamp
}

/// The reel editor: the reel plays right away, the options below change it as it plays,
/// and Share makes the video on the phone and opens the share sheet.
struct ReelView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let request: ReelRequest

    @AppStorage("reel.options") private var storedOptions = Data()
    @State private var options = ReelOptions()
    @State private var small: [String: UIImage] = [:]
    @State private var blurred: [String: UIImage] = [:]
    @State private var renderer: ReelRenderer?
    @State private var looks: [ReelLook: UIImage] = [:]
    /// Playback runs from this moment; while paused, `pausedAt` holds the time shown.
    @State private var origin = Date()
    @State private var pausedAt: Double?
    @State private var tab: ReelTab
    @State private var making: Double?
    @State private var makeTask: Task<Void, Never>?
    @State private var note: String?
    private let ink = Palette.viewerInk

    enum ReelTab { case stamp, move, more }

    init(request: ReelRequest) {
        self.request = request
        _tab = State(initialValue: request.tab)
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            player.frame(maxHeight: .infinity).padding(.horizontal, 16).padding(.top, 4)
            scrubber
            tabs
            panel.frame(height: 196)
        }
        .background(Palette.viewerBg.ignoresSafeArea())
        .overlay { if let making { makingOverlay(making) } }
        .overlay(alignment: .bottom) {
            if let note { ToastView(message: note).padding(.bottom, 32) }
        }
        .animation(.easeOut(duration: 0.2), value: note)
        .preferredColorScheme(.dark)
        .task { await load() }
        .onChange(of: options) { _, new in
            storedOptions = (try? JSONEncoder().encode(new)) ?? Data()
            rebuild()
        }
        .onDisappear { makeTask?.cancel() }
    }

    // MARK: Parts

    private var topBar: some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "chevron.backward").font(.system(size: 18, weight: .semibold))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .accessibilityLabel(L.s("close"))
            Spacer()
            Text(L.s("reel_title")).font(.nunito(17, .bold)).lineLimit(1).minimumScaleFactor(0.7)
            Spacer()
            Button(action: make) {
                Text(L.s("share")).font(.nunito(15, .bold)).foregroundStyle(Palette.viewerBg)
                    .lineLimit(1).padding(.horizontal, 16).frame(minHeight: 36)
                    .background(ink, in: Capsule())
            }
            .disabled(renderer == nil || making != nil)
            .frame(minHeight: 44)
        }
        .foregroundStyle(ink)
        .padding(.horizontal, 8)
    }

    private var player: some View {
        GeometryReader { geo in
            let (pw, ph) = options.size.pixels
            let s = min(geo.size.width / CGFloat(pw), geo.size.height / CGFloat(ph))
            let size = CGSize(width: CGFloat(pw) * s, height: CGFloat(ph) * s)
            TimelineView(.animation(minimumInterval: 1 / 30, paused: pausedAt != nil || making != nil)) { tl in
                let t = time(at: tl.date)
                Canvas(rendersAsynchronously: false) { ctx, area in
                    guard let renderer else { return }
                    ctx.withCGContext { cg in
                        UIGraphicsPushContext(cg)
                        renderer.draw(in: cg, size: area, time: t)
                        UIGraphicsPopContext()
                    }
                }
                .frame(width: size.width, height: size.height)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay {
                    if renderer == nil { ProgressView().tint(ink) }
                    if pausedAt != nil {
                        Image(systemName: "play.fill").font(.system(size: 22)).foregroundStyle(.white)
                            .frame(width: 56, height: 56).background(.black.opacity(0.45), in: Circle())
                    }
                }
                .contentShape(Rectangle())
                .onTapGesture { togglePlay() }
                .accessibilityElement()
                .accessibilityLabel(L.s("reel_preview"))
                .accessibilityAddTraits(.isButton)
                .accessibilityHint(L.s(pausedAt == nil ? "reel_pause" : "reel_play"))
                .position(x: geo.size.width / 2, y: geo.size.height / 2)
            }
        }
    }

    private var scrubber: some View {
        TimelineView(.animation(minimumInterval: 1 / 15, paused: pausedAt != nil || making != nil)) { tl in
            let total = renderer?.plan.total ?? 1
            let t = time(at: tl.date)
            HStack(spacing: 10) {
                Text(clock(t)).monospacedDigit()
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(.white.opacity(0.18)).frame(height: 4)
                        Capsule().fill(ink).frame(width: geo.size.width * CGFloat(min(t / total, 1)), height: 4)
                    }
                    .frame(maxHeight: .infinity)
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0).onChanged { v in
                        let x = direction == .rightToLeft ? geo.size.width - v.location.x : v.location.x
                        pausedAt = min(max(x / max(geo.size.width, 1), 0), 1) * total
                    })
                }
                .frame(height: 28)
                .accessibilityElement()
                .accessibilityLabel(L.s("reel_preview"))
                .accessibilityValue(clock(t))
                Text(clock(total)).monospacedDigit()
            }
            .font(.nunito(12, .bold)).foregroundStyle(ink.opacity(0.7))
            .padding(.horizontal, 20).padding(.top, 8)
        }
    }

    @Environment(\.layoutDirection) private var direction

    private var tabs: some View {
        HStack(spacing: 4) {
            tabButton(.stamp, "reel_tab_stamp")
            tabButton(.move, "reel_tab_transition")
            tabButton(.more, "more")
        }
        .padding(.horizontal, 12).padding(.vertical, 6)
    }

    private func tabButton(_ t: ReelTab, _ key: String) -> some View {
        Button { tab = t } label: {
            Text(L.s(key)).font(.nunito(14, .bold)).lineLimit(1).minimumScaleFactor(0.7)
                .foregroundStyle(tab == t ? ink : ink.opacity(0.6))
                .frame(maxWidth: .infinity, minHeight: 36)
                .background(tab == t ? Color.white.opacity(0.12) : .clear, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .accessibilityAddTraits(tab == t ? .isSelected : [])
    }

    @ViewBuilder private var panel: some View {
        ScrollView {
            switch tab {
            case .stamp:
                HStack(alignment: .top, spacing: 10) {
                    lookCard(.year, "reel_look_year")
                    lookCard(.film, "reel_look_film")
                    lookCard(.clean, "reel_look_clean")
                }
                .padding(.horizontal, 16).padding(.top, 6)
            case .move:
                VStack(alignment: .leading, spacing: 12) {
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 8) { moveChips }
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) { chip(.fade, "reel_move_fade"); chip(.slide, "reel_move_slide") }
                            HStack(spacing: 8) { chip(.zoom, "reel_move_zoom"); chip(.flash, "reel_move_flash") }
                        }
                    }
                    Text(L.s("reel_move_\(options.move.rawValue)_note")).font(.nunito(14)).foregroundStyle(ink.opacity(0.65))
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.top, 6)
            case .more:
                VStack(spacing: 12) {
                    row("reel_order", [(true, L.s("reel_newest_first")), (false, L.s("reel_oldest_first"))], \.newestFirst)
                    row("reel_speed", ReelSpeed.allCases.map { ($0, L.s("reel_speed_\($0.rawValue)")) }, \.speed)
                    row("reel_size", ReelSize.allCases.map { ($0, $0.label) }, \.size)
                    row("reel_photo", [(false, L.s("reel_whole")), (true, L.s("reel_fill"))], \.fill)
                    row("reel_end_card", [(true, L.s("reel_on")), (false, L.s("reel_off"))], \.endCard)
                }
                .padding(.horizontal, 16).padding(.top, 6).padding(.bottom, 16)
            }
        }
        .scrollIndicators(.hidden)
    }

    @ViewBuilder private var moveChips: some View {
        chip(.fade, "reel_move_fade")
        chip(.slide, "reel_move_slide")
        chip(.zoom, "reel_move_zoom")
        chip(.flash, "reel_move_flash")
    }

    private func lookCard(_ look: ReelLook, _ key: String) -> some View {
        let on = options.look == look
        return Button { options.look = look } label: {
            VStack(spacing: 6) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Color.white.opacity(0.08))
                    if let img = looks[look] { Image(uiImage: img).resizable().scaledToFill() }
                }
                .aspectRatio(9 / 16, contentMode: .fit)
                .frame(maxHeight: 132)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(on ? ink : .clear, lineWidth: 2).padding(-4))
                .padding(4)
                Text(L.s(key)).font(.nunito(13, .bold)).foregroundStyle(on ? ink : ink.opacity(0.65))
                    .lineLimit(2).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
        }
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func chip(_ move: ReelMove, _ key: String) -> some View {
        let on = options.move == move
        return Button { options.move = move } label: {
            Text(L.s(key)).font(.nunito(14, .bold)).lineLimit(1)
                .foregroundStyle(on ? Palette.viewerBg : ink)
                .padding(.horizontal, 16).frame(minHeight: 40)
                .background(on ? ink : .clear, in: Capsule())
                .overlay(Capsule().stroke(on ? .clear : Color.white.opacity(0.18), lineWidth: 1))
        }
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    /// One option: its name, then its choices. The choices go under the name when they don't fit beside it.
    private func row<V: Equatable>(_ key: String, _ choices: [(V, String)], _ path: WritableKeyPath<ReelOptions, V>) -> some View {
        let seg = HStack(spacing: 2) {
            ForEach(Array(choices.enumerated()), id: \.offset) { _, choice in
                let on = options[keyPath: path] == choice.0
                Button { options[keyPath: path] = choice.0 } label: {
                    Text(choice.1).font(.nunito(13, .bold)).lineLimit(1)
                        .foregroundStyle(on ? Palette.viewerBg : ink.opacity(0.75))
                        .padding(.horizontal, 10).frame(minHeight: 32)
                        .background(on ? ink : .clear, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(2)
        .background(Color.white.opacity(0.09), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        let label = Text(L.s(key)).font(.nunito(14, .bold)).foregroundStyle(ink.opacity(0.8))
        return ViewThatFits(in: .horizontal) {
            HStack { label; Spacer(minLength: 8); seg.fixedSize() }
            VStack(alignment: .leading, spacing: 6) { label; seg }
        }
        .accessibilityElement(children: .contain)
    }

    private func makingOverlay(_ p: Double) -> some View {
        ZStack {
            Color.black.opacity(0.78).ignoresSafeArea()
            VStack(spacing: 14) {
                Text(L.s("reel_making")).font(.nunito(19, .heavy))
                ProgressView(value: p).tint(ink).frame(width: 200)
                Text(L.s("reel_making_note")).font(.nunito(14)).foregroundStyle(ink.opacity(0.7))
                    .multilineTextAlignment(.center).frame(maxWidth: 260)
                Button(L.s("cancel")) { makeTask?.cancel() }
                    .font(.nunito(15, .bold)).frame(minHeight: 44)
            }
            .foregroundStyle(ink)
            .padding(24)
        }
        .accessibilityElement(children: .contain)
    }

    // MARK: Logic

    private func time(at date: Date) -> Double {
        let total = renderer?.plan.total ?? 1
        if let pausedAt { return min(pausedAt, total) }
        // Holds the end card a moment, then starts over.
        let loop = total + 0.9
        let t = date.timeIntervalSince(origin).truncatingRemainder(dividingBy: loop)
        return min(max(t, 0), total)
    }

    private func clock(_ t: Double) -> String {
        let s = Int(t.rounded())
        return "\(L.num(s / 60)):\(s % 60 < 10 ? L.num(0) : "")\(L.num(s % 60))"
    }

    private func show(_ message: String) {
        note = message
        Task {
            try? await Task.sleep(for: .seconds(2.5))
            if note == message { note = nil }
        }
    }

    private func togglePlay() {
        if let at = pausedAt {
            origin = Date().addingTimeInterval(-at)
            pausedAt = nil
        } else {
            pausedAt = time(at: Date())
        }
    }

    private func load() async {
        if let saved = try? JSONDecoder().decode(ReelOptions.self, from: storedOptions) { options = saved }
        let photos = Array(reelOrder(request.photos, newestFirst: true))
        // Small copies for the preview, a few at a time so the first ones show fast.
        await withTaskGroup(of: (String, UIImage?, UIImage?).self) { group in
            for p in photos {
                group.addTask {
                    let img = await loadImage(p, size: CGSize(width: 640, height: 640))
                    return (p.key, img, img.flatMap(ReelRenderer.blur))
                }
            }
            for await (key, img, blur) in group {
                if let img { small[key] = img }
                if let blur { blurred[key] = blur }
            }
        }
        rebuild()
        origin = Date()
    }

    private func makeRenderer(_ opts: ReelOptions) -> ReelRenderer {
        let photos = reelOrder(request.photos, newestFirst: opts.newestFirst)
        return ReelRenderer(photos: photos, options: opts, small: photos.map { small[$0.key] },
                            blurred: photos.map { blurred[$0.key] }, reduceMotion: reduceMotion)
    }

    private func rebuild() {
        guard !small.isEmpty else { return }
        let old = renderer.map { time(at: Date()) }
        renderer = makeRenderer(options)
        if let old, pausedAt == nil { origin = Date().addingTimeInterval(-old) }
        // A still of the first photo in each look, for the Date stamp cards.
        for look in ReelLook.allCases {
            var o = options
            o.look = look
            o.fill = false
            let r = makeRenderer(o)
            guard r.plan.items.count > 1 else { continue }
            let size = CGSize(width: 216, height: 384)
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            looks[look] = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
                r.draw(in: ctx.cgContext, size: size, time: r.plan.items[1].start + 2)
            }
        }
    }

    private func make() {
        guard making == nil else { return }
        let opts = options
        let photos = reelOrder(request.photos, newestFirst: opts.newestFirst)
        let r = makeRenderer(opts)
        pausedAt = time(at: Date())
        making = 0
        makeTask = Task {
            do {
                let url = try await ReelExport.make(
                    renderer: r,
                    name: ReelExport.fileName(),
                    load: { i in
                        guard i < photos.count else { return nil }
                        return await loadImage(photos[i], size: CGSize(width: ReelExport.photoSide, height: ReelExport.photoSide))
                    },
                    progress: { p in Task { @MainActor in if making != nil { making = p } } }
                )
                making = nil
                Share.file(url, title: L.s("reel_title"))
            } catch is CancellationError {
                making = nil
            } catch {
                making = nil
                show(L.s("reel_failed"))
            }
        }
    }
}
