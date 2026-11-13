import UIKit

/// The words one photo shows in a reel, worked out once in the app's language.
struct ReelCard {
    /// "2021", in the language's digits.
    let year: String
    /// "November 3".
    let date: String
    /// "5 years ago".
    let ago: String
    /// "Nov 3, 2021", for the Clean pill.
    let short: String
    /// "'21 11 3", for the Film date.
    let film: String

    init(photo: Photo, now: Date = Date()) {
        let y = yearOf(photo.time)
        year = L.num(y)
        date = L.date(photo.time, "MMMMd")
        ago = agoLabel(y, thisYear: yearOf(now))
        short = L.date(photo.time, "yMMMd")
        film = filmStamp(photo.time)
    }
}

/// Draws one frame of a reel into a context with UIKit's coordinates (origin top left).
/// The editor uses it for the live preview, and ReelExport for every frame of the video.
final class ReelRenderer: @unchecked Sendable {
    let plan: ReelPlan
    let options: ReelOptions
    let cards: [ReelCard]
    let introTitle: String
    let introSubtitle: String
    let appName: String
    let rtl: Bool
    let arabic: Bool
    let reduceMotion: Bool
    /// Photo `i` at full size for this frame, or nil to use its small copy.
    var image: (Int) -> UIImage? = { _ in nil }
    /// Small copies, used for the intro prints and while a full one loads.
    let small: [UIImage?]
    /// Soft blurred copies for the background behind a whole photo.
    let blurred: [UIImage?]
    let icon: UIImage?

    private static let noise: CGImage? = {
        let side = 128
        var bytes = [UInt8](repeating: 0, count: side * side)
        for i in bytes.indices { bytes[i] = UInt8.random(in: 0...255) }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(width: side, height: side, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: side,
                       space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
                       provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
    }()

    init(photos: [Photo], options: ReelOptions, small: [UIImage?], blurred: [UIImage?]? = nil, reduceMotion: Bool, now: Date = Date()) {
        plan = ReelPlan(count: photos.count, options: options, reduceMotion: reduceMotion)
        self.options = options
        self.reduceMotion = reduceMotion
        let shown = Array(photos.prefix(ReelPlan.maxPhotos))
        cards = shown.map { ReelCard(photo: $0, now: now) }
        self.small = small
        self.blurred = blurred ?? small.map { $0.flatMap(Self.blur) }
        icon = UIImage(named: "reel-icon")
        appName = L.s("app_name")
        rtl = L.lang.rtl
        arabic = L.lang.arabicScript
        // The day when every photo shares one; otherwise the app name.
        let days = Set(shown.map { L.date($0.time, "MMMMd") })
        introTitle = days.count == 1 ? days.first! : L.s("app_name")
        let years = shown.map { yearOf($0.time) }
        if let lo = years.min(), let hi = years.max(), lo != hi {
            introSubtitle = L.s("reel_years_range", ["years": L.p("years", Set(years).count), "from": L.num(lo), "to": L.num(hi)])
        } else {
            introSubtitle = years.first.map(L.num) ?? ""
        }
    }

    /// One for every blur: setting one up is slow, and it is safe to share across threads.
    private static let blurContext = CIContext(options: [.useSoftwareRenderer: false])

    /// A soft, darkened copy for the background. Small on purpose: it is blurred anyway.
    static func blur(_ image: UIImage) -> UIImage? {
        guard let cg = image.cgImage else { return nil }
        let input = CIImage(cgImage: cg)
        let side: CGFloat = 96
        let s = side / max(input.extent.width, input.extent.height)
        let scaled = input.transformed(by: CGAffineTransform(scaleX: s, y: s)).clampedToExtent()
        guard let f = CIFilter(name: "CIGaussianBlur") else { return nil }
        f.setValue(scaled, forKey: kCIInputImageKey)
        f.setValue(6, forKey: kCIInputRadiusKey)
        let rect = CGRect(x: 0, y: 0, width: input.extent.width * s, height: input.extent.height * s)
        guard let out = f.outputImage?.cropped(to: rect),
              let made = blurContext.createCGImage(out, from: rect) else { return nil }
        return UIImage(cgImage: made)
    }

    // MARK: Frame

    func draw(in c: CGContext, size: CGSize, time: Double) {
        let W = size.width, H = size.height
        c.interpolationQuality = .high
        let m = plan.moment(at: time)
        if let b = m.b {
            move(c, W, H, m.a, b, m.progress, time)
        } else {
            item(c, W, H, m.a, time - m.a.start)
        }
    }

    private func move(_ c: CGContext, _ W: CGFloat, _ H: CGFloat, _ a: ReelPlan.Item, _ b: ReelPlan.Item, _ p: Double, _ t: Double) {
        let e = CGFloat(reelEase(p))
        let ta = t - a.start, tb = t - b.start
        switch plan.move {
        case .slide:
            // Photos come in from the side the reading goes toward.
            let dir: CGFloat = rtl ? -1 : 1
            c.saveGState(); c.translateBy(x: -e * W * dir, y: 0); item(c, W, H, a, ta); c.restoreGState()
            c.saveGState(); c.translateBy(x: (1 - e) * W * dir, y: 0); item(c, W, H, b, tb); c.restoreGState()
        case .zoom:
            item(c, W, H, b, tb)
            layer(c, 1 - e) {
                c.translateBy(x: W / 2, y: H / 2); c.scaleBy(x: 1 + 0.22 * e, y: 1 + 0.22 * e); c.translateBy(x: -W / 2, y: -H / 2)
                item(c, W, H, a, ta)
            }
        case .flash:
            if p < 0.5 { item(c, W, H, a, ta) } else { item(c, W, H, b, tb) }
            let white = pow(1 - abs(2 * p - 1), 1.4) * 0.96
            c.setFillColor(UIColor(white: 1, alpha: white).cgColor)
            c.fill(CGRect(x: 0, y: 0, width: W, height: H))
        case .fade:
            item(c, W, H, a, ta)
            layer(c, e) { item(c, W, H, b, tb) }
        }
    }

    private func item(_ c: CGContext, _ W: CGFloat, _ H: CGFloat, _ it: ReelPlan.Item, _ lt: Double) {
        c.saveGState()
        c.clip(to: CGRect(x: 0, y: 0, width: W, height: H))
        let u = min(max(lt / it.length, 0), 1)
        switch it.kind {
        case .intro: intro(c, W, H, u)
        case .outro: outro(c, W, H, u)
        case .photo(let i): photo(c, W, H, i, lt, u)
        }
        c.restoreGState()
    }

    /// Draws `body` as one layer at the given opacity.
    private func layer(_ c: CGContext, _ alpha: CGFloat, _ body: () -> Void) {
        guard alpha > 0.001 else { return }
        c.saveGState()
        c.setAlpha(alpha)
        c.beginTransparencyLayer(auxiliaryInfo: nil)
        body()
        c.endTransparencyLayer()
        c.restoreGState()
    }

    // MARK: Cards

    private func background(_ c: CGContext, _ W: CGFloat, _ H: CGFloat, _ i: Int, dark: CGFloat) {
        c.setFillColor(Self.ink.cgColor)
        c.fill(CGRect(x: 0, y: 0, width: W, height: H))
        if i < blurred.count, let b = blurred[i] { cover(b, CGRect(x: 0, y: 0, width: W, height: H), scale: 1.15) }
        c.setFillColor(Self.ink.withAlphaComponent(dark).cgColor)
        c.fill(CGRect(x: 0, y: 0, width: W, height: H))
    }

    private func intro(_ c: CGContext, _ W: CGFloat, _ H: CGFloat, _ u: Double) {
        background(c, W, H, 0, dark: 0.55)
        let a = CGFloat(reduceMotion ? 1 : reelEase(min(u / 0.45, 1)))
        let tall = W / H < 0.7
        let cx = W / 2, cy = H * (tall ? 0.36 : 0.34)
        let pw = W * (tall ? 0.34 : 0.28), ph = pw * 1.18
        let fan = Array(0..<min(3, small.count)).reversed()
        let n = CGFloat(fan.count)
        for (j, i) in fan.enumerated() {
            guard let img = image(i) ?? small[i] else { continue }
            let k = CGFloat(j) - (n - 1) / 2
            layer(c, CGFloat(min(u * 3, 1))) {
                c.translateBy(x: cx + k * pw * 0.42 * a, y: cy + abs(k) * W * 0.012)
                c.rotate(by: k * 9 * a * .pi / 180)
                let card = CGRect(x: -pw / 2, y: -ph / 2, width: pw, height: ph)
                c.saveGState()
                c.setShadow(offset: CGSize(width: 0, height: W * 0.012), blur: W * 0.04, color: UIColor(white: 0, alpha: 0.45).cgColor)
                c.setFillColor(Self.paper.cgColor)
                c.addPath(UIBezierPath(roundedRect: card, cornerRadius: W * 0.012).cgPath); c.fillPath()
                c.restoreGState()
                let m = pw * 0.06
                let pic = CGRect(x: card.minX + m, y: card.minY + m, width: pw - 2 * m, height: pw - 2 * m)
                c.saveGState()
                c.addPath(UIBezierPath(roundedRect: pic, cornerRadius: W * 0.006).cgPath); c.clip()
                cover(img, pic)
                c.restoreGState()
            }
        }
        let ta = CGFloat(reduceMotion ? min(u * 3, 1) : reelEase(min(max((u - 0.15) / 0.4, 0), 1)))
        layer(c, ta) {
            c.translateBy(x: 0, y: (1 - ta) * W * 0.03)
            let ts = W * 0.115
            var title = text(introTitle, font(.black, ts), .white, kern: -0.03 * ts)
            if title.size().width > W * 0.9 { title = text(introTitle, font(.black, ts * W * 0.9 / title.size().width), .white) }
            let ty = cy + pw * 0.75 + ts * 1.15
            drawCentered(title, cx, baseline: ty)
            drawCentered(text(introSubtitle, font(.bold, W * 0.045), UIColor(white: 1, alpha: 0.82)), cx, baseline: ty + W * 0.075)
        }
    }

    private func outro(_ c: CGContext, _ W: CGFloat, _ H: CGFloat, _ u: Double) {
        background(c, W, H, max(0, small.count - 1), dark: 0.72)
        let a = CGFloat(reduceMotion ? 1 : reelEase(min(u / 0.4, 1)))
        layer(c, a) {
            c.translateBy(x: 0, y: (1 - a) * W * 0.03)
            let s = W * 0.24, cx = W / 2, cy = H * 0.44
            let r = CGRect(x: cx - s / 2, y: cy - s / 2, width: s, height: s)
            c.saveGState()
            c.setShadow(offset: CGSize(width: 0, height: W * 0.015), blur: W * 0.05, color: UIColor(white: 0, alpha: 0.4).cgColor)
            c.setFillColor(UIColor.white.cgColor)
            c.addPath(UIBezierPath(roundedRect: r, cornerRadius: s * 0.23).cgPath); c.fillPath()
            c.restoreGState()
            if let icon {
                c.saveGState()
                c.addPath(UIBezierPath(roundedRect: r, cornerRadius: s * 0.23).cgPath); c.clip()
                icon.draw(in: r)
                c.restoreGState()
            }
            let fs = W * 0.07
            drawCentered(text(appName, font(.black, fs), .white, kern: -0.03 * fs), cx, baseline: cy + s / 2 + fs * 1.4)
        }
    }

    private func photo(_ c: CGContext, _ W: CGFloat, _ H: CGFloat, _ i: Int, _ lt: Double, _ u: Double) {
        guard let img = image(i) ?? (i < small.count ? small[i] : nil) else {
            background(c, W, H, i, dark: 0.45)
            return
        }
        let kb = CGFloat(reduceMotion ? 0.5 : u)
        let dir: CGFloat = i % 2 == 1 ? 1 : -1
        let tall = W / H < 0.7
        var R: CGRect
        var radius: CGFloat = 0
        if options.fill {
            c.setFillColor(Self.ink.cgColor); c.fill(CGRect(x: 0, y: 0, width: W, height: H))
            R = CGRect(x: 0, y: 0, width: W, height: H)
            cover(img, R, scale: 1.05 + 0.07 * kb, px: dir * (kb - 0.5) * 0.7)
        } else {
            background(c, W, H, i, dark: 0.45)
            let iw = max(img.size.width, 1), ih = max(img.size.height, 1)
            let s = min(W * 0.86 / iw, H * (tall ? 0.54 : 0.64) / ih)
            let w = iw * s, h = ih * s
            let cy = H * (tall ? 0.39 : 0.44)
            R = CGRect(x: (W - w) / 2, y: cy - h / 2, width: w, height: h)
            radius = W * 0.028
            let path = UIBezierPath(roundedRect: R, cornerRadius: radius).cgPath
            c.saveGState()
            c.setShadow(offset: CGSize(width: 0, height: W * 0.02), blur: W * 0.06, color: UIColor(white: 0, alpha: 0.5).cgColor)
            c.setFillColor(UIColor.black.cgColor); c.addPath(path); c.fillPath()
            c.restoreGState()
            c.saveGState()
            c.addPath(path); c.clip()
            cover(img, R, scale: 1.02 + 0.06 * kb, px: dir * (kb - 0.5) * 0.5)
            c.restoreGState()
        }
        let a = CGFloat(reduceMotion ? min(max((lt - 0.1) / 0.3, 0), 1) : reelEase(min(max((lt - 0.2) / 0.55, 0), 1)))
        guard i < cards.count else { return }
        switch options.look {
        case .year: bigYear(c, W, H, R, radius, cards[i], a)
        case .film: film(c, W, H, R, radius, cards[i], a)
        case .clean: clean(c, W, H, R, cards[i], a)
        }
    }

    // MARK: Date stamps

    /// Stamps stay in the top 80%, clear of the buttons social apps lay over the bottom.
    private func safeBottom(_ R: CGRect, _ H: CGFloat) -> CGFloat { min(R.maxY, H * 0.8) }

    private func bigYear(_ c: CGContext, _ W: CGFloat, _ H: CGFloat, _ R: CGRect, _ radius: CGFloat, _ card: ReelCard, _ a: CGFloat) {
        let ys = W * 0.2, ds = W * 0.046, pad = W * 0.065
        let yearText = text(card.year, font(.black, ys), .white, kern: -0.035 * ys)
        let line = text("\(card.date) · \(card.ago)", font(.bold, ds), UIColor(white: 1, alpha: 0.9))
        let below = !options.fill && H * 0.8 - R.maxY > ys * 1.1 + ds * 1.6
        var left: CGFloat, right: CGFloat, baseline: CGFloat
        if below {
            left = R.minX; right = R.maxX
            baseline = R.maxY + ys * 0.98
        } else {
            let bottom = safeBottom(R, H)
            c.saveGState()
            if radius > 0 { c.addPath(UIBezierPath(roundedRect: R, cornerRadius: radius).cgPath); c.clip() }
            let top = bottom - ys * 2.2
            let colors = [UIColor(white: 0, alpha: 0).cgColor, UIColor(white: 0, alpha: 0.6).cgColor] as CFArray
            if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
                c.drawLinearGradient(g, start: CGPoint(x: 0, y: top), end: CGPoint(x: 0, y: R.maxY), options: [.drawsAfterEndLocation])
            }
            c.restoreGState()
            left = R.minX + pad; right = R.maxX - pad
            baseline = bottom - pad - ds * 1.7
        }
        layer(c, a) {
            c.translateBy(x: 0, y: (1 - a) * W * 0.035)
            c.setShadow(offset: .zero, blur: W * 0.02, color: UIColor(white: 0, alpha: 0.25).cgColor)
            draw(yearText, edge: rtl ? right : left, baseline: baseline)
            draw(line, edge: rtl ? right : left, baseline: baseline + ds * 1.55)
        }
    }

    private func clean(_ c: CGContext, _ W: CGFloat, _ H: CGFloat, _ R: CGRect, _ card: ReelCard, _ a: CGFloat) {
        let fs = W * 0.037, pad = W * 0.04
        let label = NSMutableAttributedString(attributedString: text(card.short, font(.heavy, fs), .white))
        label.append(text("  ·  \(card.ago)", font(.semibold, fs), UIColor(white: 1, alpha: 0.82)))
        let size = label.size()
        let h = fs * 2.1, w = min(size.width + fs * 1.6, R.width - pad * 2)
        let y = max(R.minY + pad, options.fill ? H * 0.1 : 0)
        let x = rtl ? R.maxX - pad - w : R.minX + pad
        layer(c, a) {
            c.setFillColor(UIColor(white: 0, alpha: 0.42).cgColor)
            c.addPath(UIBezierPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: h / 2).cgPath); c.fillPath()
            label.draw(with: CGRect(x: x + fs * 0.8, y: y + (h - size.height) / 2, width: w - fs * 1.6, height: size.height),
                       options: [.usesLineFragmentOrigin, .truncatesLastVisibleLine], context: nil)
        }
    }

    private func film(_ c: CGContext, _ W: CGFloat, _ H: CGFloat, _ R: CGRect, _ radius: CGFloat, _ card: ReelCard, _ a: CGFloat) {
        // A warm tint, a dark edge and a little grain, like a print from film.
        c.saveGState()
        if radius > 0 { c.addPath(UIBezierPath(roundedRect: R, cornerRadius: radius).cgPath); c.clip() } else { c.clip(to: R) }
        c.setBlendMode(.multiply)
        c.setFillColor(UIColor(red: 1, green: 236 / 255, blue: 212 / 255, alpha: 1).cgColor)
        c.fill(R)
        c.setBlendMode(.normal)
        let colors = [UIColor(white: 0, alpha: 0).cgColor, UIColor(white: 0, alpha: 0.38).cgColor] as CFArray
        if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
            let mid = CGPoint(x: R.midX, y: R.midY)
            c.drawRadialGradient(g, startCenter: mid, startRadius: min(R.width, R.height) * 0.35,
                                 endCenter: mid, endRadius: hypot(R.width, R.height) * 0.62, options: [.drawsAfterEndLocation])
        }
        if let noise = Self.noise {
            c.setAlpha(0.07)
            c.setBlendMode(.overlay)
            let tile = 128 * max(1, W / 540)
            c.translateBy(x: -CGFloat.random(in: 0..<tile), y: -CGFloat.random(in: 0..<tile))
            c.draw(noise, in: CGRect(x: 0, y: 0, width: tile, height: tile), byTiling: true)
        }
        c.restoreGState()

        let h = W * 0.052, pad = W * 0.055
        let width = sevenWidth(card.film, h)
        let x = R.maxX - pad - width
        let y = safeBottom(R, H) - pad - h
        layer(c, a) {
            // Leans right like the real thing.
            c.concatenate(CGAffineTransform(a: 1, b: 0, c: -0.1, d: 1, tx: (y + h) * 0.1, ty: 0))
            c.setShadow(offset: .zero, blur: h * 0.5, color: UIColor(red: 1, green: 0.43, blue: 0.08, alpha: 0.9).cgColor)
            c.setStrokeColor(UIColor(red: 1, green: 0.47, blue: 0.08, alpha: 0.55).cgColor)
            seven(c, card.film, x, y, h)
            c.setShadow(offset: .zero, blur: 0, color: nil)
            c.setStrokeColor(UIColor(red: 1, green: 0.69, blue: 0.29, alpha: 1).cgColor)
            seven(c, card.film, x, y, h)
        }
    }

    private static let segments: [Character: String] = [
        "0": "abcdef", "1": "bc", "2": "abged", "3": "abgcd", "4": "fgbc",
        "5": "afgcd", "6": "afgedc", "7": "abc", "8": "abcdefg", "9": "abcdfg",
    ]

    private func sevenWidth(_ s: String, _ h: CGFloat) -> CGFloat {
        let w = h * 0.52, adv = w + h * 0.32
        var x: CGFloat = 0
        for ch in s { x += ch == " " ? adv * 0.8 : ch == "'" ? h * 0.32 : adv }
        return x - h * 0.32
    }

    /// Digits drawn as seven bars each, like a camera's date display.
    private func seven(_ c: CGContext, _ s: String, _ x0: CGFloat, _ y: CGFloat, _ h: CGFloat) {
        let w = h * 0.52, th = h * 0.13, gap = th * 0.55, adv = w + h * 0.32
        let ends: [Character: (CGPoint, CGPoint)] = [
            "a": (CGPoint(x: 0, y: 0), CGPoint(x: w, y: 0)), "b": (CGPoint(x: w, y: 0), CGPoint(x: w, y: h / 2)),
            "c": (CGPoint(x: w, y: h / 2), CGPoint(x: w, y: h)), "d": (CGPoint(x: 0, y: h), CGPoint(x: w, y: h)),
            "e": (CGPoint(x: 0, y: h / 2), CGPoint(x: 0, y: h)), "f": (CGPoint(x: 0, y: 0), CGPoint(x: 0, y: h / 2)),
            "g": (CGPoint(x: 0, y: h / 2), CGPoint(x: w, y: h / 2)),
        ]
        c.setLineWidth(th)
        c.setLineCap(.round)
        var x = x0
        for ch in s {
            if ch == " " { x += adv * 0.8; continue }
            if ch == "'" {
                c.move(to: CGPoint(x: x + th, y: y - h * 0.05)); c.addLine(to: CGPoint(x: x + th * 0.6, y: y + h * 0.22)); c.strokePath()
                x += h * 0.32; continue
            }
            for seg in Self.segments[ch] ?? "" {
                guard let (p1, p2) = ends[seg] else { continue }
                let dx = p2.x - p1.x, dy = p2.y - p1.y, len = max(hypot(dx, dy), 0.001)
                let ux = dx / len * gap, uy = dy / len * gap
                c.move(to: CGPoint(x: x + p1.x + ux, y: y + p1.y + uy))
                c.addLine(to: CGPoint(x: x + p2.x - ux, y: y + p2.y - uy))
            }
            c.strokePath()
            x += adv
        }
    }

    // MARK: Drawing helpers

    private static let ink = UIColor(red: 13 / 255, green: 13 / 255, blue: 14 / 255, alpha: 1)
    private static let paper = UIColor(red: 244 / 255, green: 244 / 255, blue: 242 / 255, alpha: 1)

    private func cover(_ img: UIImage, _ r: CGRect, scale: CGFloat = 1, px: CGFloat = 0) {
        let iw = img.size.width, ih = img.size.height
        guard iw > 0, ih > 0 else { return }
        let s = max(r.width / iw, r.height / ih) * scale
        let dw = iw * s, dh = ih * s
        img.draw(in: CGRect(x: r.minX + (r.width - dw) / 2 + px * (dw - r.width) / 2, y: r.minY + (r.height - dh) / 2, width: dw, height: dh))
    }

    private enum Weight { case black, heavy, bold, semibold }

    private func font(_ weight: Weight, _ size: CGFloat) -> UIFont {
        let name: String
        switch (weight, arabic) {
        case (.black, false): name = "Nunito-Black"
        case (.heavy, false): name = "Nunito-ExtraBold"
        case (.bold, false): name = "Nunito-Bold"
        case (.semibold, false): name = "Nunito-SemiBold"
        case (.black, true): name = "Tajawal-Black"
        case (.heavy, true): name = "Tajawal-ExtraBold"
        case (.bold, true), (.semibold, true): name = "Tajawal-Bold"
        }
        if let f = UIFont(name: name, size: size) { return f }
        let w: UIFont.Weight = switch weight {
        case .black: .black
        case .heavy: .heavy
        case .bold: .bold
        case .semibold: .semibold
        }
        return .systemFont(ofSize: size, weight: w)
    }

    private func text(_ s: String, _ font: UIFont, _ color: UIColor, kern: CGFloat = 0) -> NSAttributedString {
        // Arabic letters join, so they keep normal spacing.
        NSAttributedString(string: s, attributes: [.font: font, .foregroundColor: color, .kern: arabic ? 0 : kern])
    }

    private func firstFont(_ t: NSAttributedString) -> UIFont {
        (t.length > 0 ? t.attribute(.font, at: 0, effectiveRange: nil) as? UIFont : nil) ?? .systemFont(ofSize: 17)
    }

    /// Draws from the leading edge: the left one, or the right one in a right to left language.
    private func draw(_ t: NSAttributedString, edge: CGFloat, baseline: CGFloat) {
        let w = t.size().width
        t.draw(at: CGPoint(x: rtl ? edge - w : edge, y: baseline - firstFont(t).ascender))
    }

    private func drawCentered(_ t: NSAttributedString, _ cx: CGFloat, baseline: CGFloat) {
        t.draw(at: CGPoint(x: cx - t.size().width / 2, y: baseline - firstFont(t).ascender))
    }
}
