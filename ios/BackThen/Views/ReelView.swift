import SwiftUI
import UIKit

/// Photos to make a reel from.
struct ReelRequest: Identifiable {
    let id = UUID()
    let photos: [Photo]
    var tab: ReelView.ReelTab = .stamp
}

/// The reel editor: the reel plays right away, the options below change it as it plays,
/// and Save or Share makes the video on the phone.
struct ReelView: View {
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var direction
    let request: ReelRequest

    @AppStorage("reel.options") private var storedOptions = Data()
    @State private var options = ReelOptions()
    @State private var curatedPhotos: [Photo]
    @State private var small: [String: UIImage] = [:]
    @State private var blurred: [String: UIImage] = [:]
    @State private var renderer: ReelRenderer?
    @State private var looks: [ReelLook: UIImage] = [:]
    @State private var looksTask: Task<Void, Never>?
    /// Playback runs from this moment; while paused, `pausedAt` holds the time shown.
    @State private var origin = Date()
    @State private var pausedAt: Double?
    @State private var scrubbing = false
    @State private var resumeAfterScrub = false
    @State private var tab: ReelTab
    @State private var making: Double?
    @State private var makeTask: Task<Void, Never>?
    @State private var note: String?
    @State private var noteKind: AppStore.ToastKind = .info
    private let ink = Palette.viewerInk

    enum ReelTab { case stamp, move, photos, more }

    init(request: ReelRequest) {
        self.request = request
        _tab = State(initialValue: request.tab)
        _curatedPhotos = State(initialValue: reelOrder(request.photos, newestFirst: true))
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            // Single shared TimelineView drives both the live preview canvas and the scrubber clock.
            TimelineView(.animation(minimumInterval: 1 / 30, paused: pausedAt != nil || making != nil)) { tl in
                let t = time(at: tl.date)
                VStack(spacing: 0) {
                    player(at: t)
                        .frame(maxHeight: .infinity)
                        .padding(.horizontal, 16)
                        .padding(.top, 4)
                    scrubber(at: t)
                }
            }
            tabs
            panel.frame(height: 216)
        }
        .background(Palette.viewerBg.ignoresSafeArea())
        .overlay { if let making { makingOverlay(making) } }
        .overlay(alignment: .top) {
            if let note {
                ToastView(message: note, kind: noteKind)
                    .padding(.top, 8)
                    .padding(.horizontal, 16)
            }
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: note)
        .preferredColorScheme(.dark)
        .task { await load() }
        .onChange(of: options) { old, new in
            storedOptions = (try? JSONEncoder().encode(new)) ?? Data()
            if old.newestFirst != new.newestFirst {
                curatedPhotos = curatedPhotos.sorted { new.newestFirst ? $0.time > $1.time : $0.time < $1.time }
                rebuildLooks()
            }
            rebuildRenderer()
        }
        .onDisappear {
            makeTask?.cancel()
            looksTask?.cancel()
        }
    }

    // MARK: Parts

    private var topBar: some View {
        HStack(spacing: 6) {
            Button { dismiss() } label: {
                Image(systemName: "chevron.backward").font(.system(size: 18, weight: .semibold))
                    .frame(width: 44, height: 44).contentShape(Rectangle())
            }
            .accessibilityLabel(L.s("close"))
            Spacer(minLength: 4)
            Text(L.s("reel_title")).font(.nunito(17, .bold)).lineLimit(1).minimumScaleFactor(0.7)
            Spacer(minLength: 4)
            Button { exportReel(saveDirectly: true) } label: {
                Image(systemName: "square.and.arrow.down")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(ink)
                    .frame(width: 40, height: 36)
                    .background(Color.white.opacity(0.14), in: Capsule())
                    .frame(minWidth: 44, minHeight: 44).contentShape(Rectangle())
            }
            .disabled(renderer == nil || making != nil)
            .accessibilityLabel(L.s("reel_save"))

            Button { exportReel(saveDirectly: false) } label: {
                Text(L.s("share")).font(.nunito(15, .bold)).foregroundStyle(Palette.viewerBg)
                    .lineLimit(1).padding(.horizontal, 16).frame(minHeight: 36)
                    .background(ink, in: Capsule())
                    .frame(minHeight: 44).contentShape(Rectangle())
            }
            .disabled(renderer == nil || making != nil)
        }
        .foregroundStyle(ink)
        .padding(.horizontal, 8)
    }

    private func player(at t: Double) -> some View {
        GeometryReader { geo in
            let (pw, ph) = options.size.pixels
            let s = min(geo.size.width / CGFloat(pw), geo.size.height / CGFloat(ph))
            let size = CGSize(width: CGFloat(pw) * s, height: CGFloat(ph) * s)
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
                if pausedAt != nil && !scrubbing {
                    Image(systemName: "play.fill").font(.system(size: 22)).foregroundStyle(.white)
                        .frame(width: 56, height: 56).background(.black.opacity(0.5), in: Circle())
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { togglePlay() }
            .accessibilityElement()
            .accessibilityLabel(L.s("reel_preview"))
            .accessibilityValue(L.s(pausedAt == nil ? "reel_playing" : "reel_paused"))
            .accessibilityAddTraits(.isButton)
            .accessibilityHint(L.s(pausedAt == nil ? "reel_pause" : "reel_play"))
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
    }

    private func scrubber(at t: Double) -> some View {
        let total = max(renderer?.plan.total ?? 1, 0.01)
        let fraction = CGFloat(min(max(t / total, 0), 1))
        let thumb: CGFloat = scrubbing ? 16 : 12
        return HStack(spacing: 10) {
            Text(clock(t)).monospacedDigit()
            GeometryReader { geo in
                let trackW = max(geo.size.width, 1)
                let fillW = trackW * fraction
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.2)).frame(height: 4)
                    Capsule().fill(ink).frame(width: fillW, height: 4)
                    Circle()
                        .fill(ink)
                        .shadow(color: .black.opacity(0.4), radius: 2, y: 1)
                        .frame(width: thumb, height: thumb)
                        .offset(x: min(max(fillW - thumb / 2, 0), max(trackW - thumb, 0)))
                }
                .frame(maxHeight: .infinity)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            if !scrubbing {
                                scrubbing = true
                                resumeAfterScrub = (pausedAt == nil)
                            }
                            let x = direction == .rightToLeft ? geo.size.width - v.location.x : v.location.x
                            pausedAt = min(max(x / trackW, 0), 1) * total
                        }
                        .onEnded { _ in
                            scrubbing = false
                            if resumeAfterScrub, let at = pausedAt {
                                origin = Date().addingTimeInterval(-at)
                                pausedAt = nil
                            }
                            resumeAfterScrub = false
                        }
                )
            }
            .frame(height: 44)
            .accessibilityElement()
            .accessibilityLabel(L.s("reel_preview"))
            .accessibilityValue("\(clock(t)) / \(clock(total))")
            .accessibilityAdjustableAction { dir in
                let step = 1.0
                let current = time(at: Date())
                let next: Double = switch dir {
                case .increment: min(current + step, total)
                case .decrement: max(current - step, 0)
                @unknown default: current
                }
                if pausedAt != nil {
                    pausedAt = next
                } else {
                    origin = Date().addingTimeInterval(-next)
                }
            }
            Text(clock(total)).monospacedDigit()
        }
        .font(.nunito(12, .bold)).foregroundStyle(ink.opacity(0.75))
        .padding(.horizontal, 20).padding(.top, 2)
    }

    private var tabs: some View {
        HStack(spacing: 4) {
            tabButton(.stamp, "reel_tab_stamp")
            tabButton(.move, "reel_tab_transition")
            tabButton(.photos, "reel_photo")
            tabButton(.more, "more")
        }
        .padding(.horizontal, 12).padding(.vertical, 4)
    }

    private func tabButton(_ t: ReelTab, _ key: String) -> some View {
        Button { tab = t } label: {
            Text(L.s(key)).font(.nunito(13, .bold)).lineLimit(1).minimumScaleFactor(0.7)
                .foregroundStyle(tab == t ? ink : ink.opacity(0.65))
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(tab == t ? Color.white.opacity(0.14) : .clear, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
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
                .padding(.horizontal, 16).padding(.top, 6).padding(.bottom, 12)
            case .move:
                VStack(alignment: .leading, spacing: 12) {
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 8) { moveChips }
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(spacing: 8) { chip(.fade, "reel_move_fade"); chip(.slide, "reel_move_slide") }
                            HStack(spacing: 8) { chip(.zoom, "reel_move_zoom"); chip(.flash, "reel_move_flash") }
                        }
                    }
                    Text(L.s("reel_move_\(options.move.rawValue)_note")).font(.nunito(14)).foregroundStyle(ink.opacity(0.72))
                        .fixedSize(horizontal: false, vertical: true)
                    if reduceMotion && options.move == .flash {
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: "info.circle.fill").font(.system(size: 14)).foregroundStyle(Palette.toastInfo)
                            Text(L.s("reel_reduce_motion_note")).font(.nunito(13, .medium)).foregroundStyle(ink.opacity(0.85))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.top, 6).padding(.bottom, 12)
            case .photos:
                VStack(alignment: .leading, spacing: 10) {
                    Text(L.p("photos", curatedPhotos.count))
                        .font(.nunito(13, .bold))
                        .foregroundStyle(ink.opacity(0.72))
                        .padding(.horizontal, 16)
                    ScrollView(.horizontal, showsIndicators: true) {
                        LazyHStack(spacing: 10) {
                            ForEach(Array(curatedPhotos.enumerated()), id: \.element.key) { i, photo in
                                VStack(spacing: 6) {
                                    ZStack(alignment: .topTrailing) {
                                        PhotoImage(photo: photo)
                                            .frame(width: 82, height: 96)
                                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                        if curatedPhotos.count > 2 {
                                            Button {
                                                if let idx = curatedPhotos.firstIndex(where: { $0.key == photo.key }) {
                                                    removePhoto(at: idx)
                                                }
                                            } label: {
                                                Image(systemName: "xmark.circle.fill")
                                                    .font(.system(size: 20))
                                                    .symbolRenderingMode(.palette)
                                                    .foregroundStyle(.white, Color.black.opacity(0.65))
                                                    .frame(width: 36, height: 36).contentShape(Rectangle())
                                            }
                                            .accessibilityLabel(L.s("remove_from_album"))
                                        }
                                    }
                                    HStack(spacing: 2) {
                                        Button {
                                            if let idx = curatedPhotos.firstIndex(where: { $0.key == photo.key }) {
                                                movePhoto(from: idx, to: idx - 1)
                                            }
                                        } label: {
                                            Image(systemName: "chevron.backward")
                                                .font(.system(size: 12, weight: .bold))
                                                .frame(width: 38, height: 36)
                                                .background(Color.white.opacity(0.1), in: Capsule())
                                        }
                                        .disabled(i == 0)
                                        .opacity(i == 0 ? 0.3 : 1)
                                        Button {
                                            if let idx = curatedPhotos.firstIndex(where: { $0.key == photo.key }) {
                                                movePhoto(from: idx, to: idx + 1)
                                            }
                                        } label: {
                                            Image(systemName: "chevron.forward")
                                                .font(.system(size: 12, weight: .bold))
                                                .frame(width: 38, height: 36)
                                                .background(Color.white.opacity(0.1), in: Capsule())
                                        }
                                        .disabled(i == curatedPhotos.count - 1)
                                        .opacity(i == curatedPhotos.count - 1 ? 0.3 : 1)
                                    }
                                    .foregroundStyle(ink)
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }
                .padding(.top, 6).padding(.bottom, 12)
            case .more:
                VStack(spacing: 12) {
                    row("reel_order", [(true, L.s("reel_newest_first")), (false, L.s("reel_oldest_first"))], \.newestFirst)
                    row("reel_speed", ReelSpeed.allCases.map { ($0, L.s("reel_speed_\($0.rawValue)")) }, \.speed)
                    // Wrap aspect ratios with LRI/PDI so Arabic RTL never flips 9:16 to 16:9.
                    row("reel_size", ReelSize.allCases.map { ($0, "\u{2066}\($0.label)\u{2069}") }, \.size)
                    row("reel_photo", [(false, L.s("reel_whole")), (true, L.s("reel_fill"))], \.fill)
                    row("reel_end_card", [(true, L.s("reel_on")), (false, L.s("reel_off"))], \.endCard)
                }
                .padding(.horizontal, 16).padding(.top, 6).padding(.bottom, 16)
            }
        }
        .scrollIndicators(.visible)
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
                Text(L.s(key)).font(.nunito(13, .bold)).foregroundStyle(on ? ink : ink.opacity(0.7))
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
                .padding(.horizontal, 16).frame(minHeight: 44)
                .background(on ? ink : .clear, in: Capsule())
                .overlay(Capsule().stroke(on ? .clear : Color.white.opacity(0.22), lineWidth: 1))
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
                        .foregroundStyle(on ? Palette.viewerBg : ink.opacity(0.8))
                        .padding(.horizontal, 10).frame(minHeight: 44)
                        .background(on ? ink : .clear, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(2)
        .background(Color.white.opacity(0.09), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        let label = Text(L.s(key)).font(.nunito(14, .bold)).foregroundStyle(ink.opacity(0.85))
        return ViewThatFits(in: .horizontal) {
            // Both keep their full width here, so a long name in a big text size picks the stacked layout.
            HStack { label.fixedSize(); Spacer(minLength: 8); seg.fixedSize() }
            VStack(alignment: .leading, spacing: 6) { label; seg }
        }
        .accessibilityElement(children: .contain)
    }

    private func makingOverlay(_ p: Double) -> some View {
        let pct = min(max(Int((p * 100).rounded()), 0), 100)
        return ZStack {
            Color.black.opacity(0.78).ignoresSafeArea()
            VStack(spacing: 14) {
                Text(L.s("reel_making")).font(.nunito(19, .heavy))
                ProgressView(value: p).tint(ink).frame(width: 220)
                Text("\(L.num(pct))%")
                    .font(.nunito(15, .bold))
                    .monospacedDigit()
                    .foregroundStyle(ink)
                Text(L.s("reel_making_note")).font(.nunito(14)).foregroundStyle(ink.opacity(0.72))
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

    private func movePhoto(from: Int, to: Int) {
        guard curatedPhotos.indices.contains(from), curatedPhotos.indices.contains(to), from != to else { return }
        curatedPhotos.swapAt(from, to)
        rebuildRenderer()
        if from == 0 || to == 0 { rebuildLooks() }
    }

    private func removePhoto(at i: Int) {
        guard curatedPhotos.count > 2, curatedPhotos.indices.contains(i) else { return }
        curatedPhotos.remove(at: i)
        rebuildRenderer()
        if i == 0 { rebuildLooks() }
    }

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

    private func show(_ message: String, kind: AppStore.ToastKind = .info) {
        noteKind = kind
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
        if let saved = try? JSONDecoder().decode(ReelOptions.self, from: storedOptions) {
            options = saved
            curatedPhotos = reelOrder(request.photos, newestFirst: saved.newestFirst)
        }
        let photos = curatedPhotos
        // Load preview copies progressively so playback starts as soon as the first 2 arrive.
        await withTaskGroup(of: (String, UIImage?, UIImage?).self) { group in
            for p in photos {
                group.addTask {
                    let img = await loadImage(p, size: CGSize(width: 800, height: 800), fit: true)
                    return (p.key, img, img.flatMap(ReelRenderer.blur))
                }
            }
            var arrived = 0
            for await (key, img, blur) in group {
                if let img { small[key] = img }
                if let blur { blurred[key] = blur }
                arrived += 1
                if renderer == nil && small.count >= min(2, photos.count) {
                    rebuildRenderer()
                    origin = Date()
                    rebuildLooks()
                } else if arrived % 4 == 0 && renderer != nil {
                    rebuildRenderer()
                }
            }
        }
        rebuildRenderer()
        if renderer != nil && looks.isEmpty { rebuildLooks() }
        if pausedAt == nil && renderer == nil { origin = Date() }
    }

    private func makeRenderer(_ opts: ReelOptions, photos: [Photo]) -> ReelRenderer {
        ReelRenderer(
            photos: photos,
            options: opts,
            small: photos.map { small[$0.key] },
            blurred: photos.map { blurred[$0.key] },
            reduceMotion: reduceMotion
        )
    }

    private func rebuildRenderer() {
        guard !small.isEmpty else { return }
        let old: Double? = renderer == nil ? nil : time(at: Date())
        renderer = makeRenderer(options, photos: curatedPhotos)
        if let old, pausedAt == nil { origin = Date().addingTimeInterval(-old) }
    }

    /// Generates the 3 Date stamp preview cards off the main thread, sampled cleanly mid-photo.
    private func rebuildLooks() {
        guard !small.isEmpty else { return }
        looksTask?.cancel()
        let photos = curatedPhotos
        let smallList = photos.map { small[$0.key] }
        let blurredList = photos.map { blurred[$0.key] }
        let baseOptions = options
        let rm = reduceMotion
        looksTask = Task.detached(priority: .userInitiated) {
            var rendered: [ReelLook: UIImage] = [:]
            let size = CGSize(width: 216, height: 384)
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            for look in ReelLook.allCases {
                if Task.isCancelled { return }
                var o = baseOptions
                o.look = look
                o.fill = false
                let r = ReelRenderer(photos: photos, options: o, small: smallList, blurred: blurredList, reduceMotion: rm)
                guard r.plan.items.count > 1 else { continue }
                let sampleTime = r.plan.items[1].start + r.plan.items[1].length * 0.5
                rendered[look] = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
                    r.draw(in: ctx.cgContext, size: size, time: sampleTime)
                }
            }
            guard !Task.isCancelled else { return }
            let result = rendered
            await MainActor.run { looks = result }
        }
    }

    private func exportReel(saveDirectly: Bool) {
        guard making == nil else { return }
        let opts = options
        let photos = curatedPhotos
        let r = makeRenderer(opts, photos: photos)
        pausedAt = time(at: Date())
        making = 0
        makeTask = Task {
            do {
                let url = try await ReelExport.make(
                    renderer: r,
                    name: ReelExport.fileName(),
                    load: { i in
                        guard i < photos.count else { return nil }
                        return await loadImage(photos[i], size: CGSize(width: ReelExport.photoSide, height: ReelExport.photoSide), fit: true)
                    },
                    progress: { p in Task { @MainActor in if making != nil { making = p } } }
                )
                making = nil
                if saveDirectly {
                    let ok = await store.saveReelToPhotos(url)
                    if ok {
                        show(L.s("toast_saved_photos"), kind: .success)
                    } else {
                        show(L.s("toast_save_failed"), kind: .error)
                    }
                } else {
                    Share.file(url, title: L.s("reel_title"))
                }
            } catch is CancellationError {
                making = nil
            } catch {
                making = nil
                show(L.s("reel_failed"), kind: .error)
            }
        }
    }
}
