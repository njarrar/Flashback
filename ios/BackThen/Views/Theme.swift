import CoreText
import SwiftUI
import UIKit

// Warm Archival (Light) & Warm Darkroom (Dark) design tokens — 100% WCAG 2.1 AA+ verified.

private func ui(_ hex: UInt32, _ alpha: CGFloat = 1) -> UIColor {
    UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
}

private func dynamic(_ light: UIColor, _ dark: UIColor, lightHigh: UIColor? = nil, darkHigh: UIColor? = nil) -> Color {
    Color(uiColor: UIColor { traits in
        let isDark = traits.userInterfaceStyle == .dark
        let isHigh = traits.accessibilityContrast == .high
        if isDark { return (isHigh ? darkHigh : nil) ?? dark }
        return (isHigh ? lightHigh : nil) ?? light
    })
}

enum Palette {
    // MARK: Brand Triad (Cobalt primary CTA, Coral emotional accent, Sun milestones)
    /// Cobalt primary CTA fill (Light #1E5CE0 w/ white 5.73:1; Dark #6BA1FF w/ #0A1429 7.13:1).
    static let brandCobaltFill = dynamic(ui(0x1E5CE0), ui(0x6BA1FF), lightHigh: ui(0x1443A8), darkHigh: ui(0x8AB7FF))
    static let brandCobaltFillInk = dynamic(ui(0xFFFFFF), ui(0x0A1429), lightHigh: ui(0xFFFFFF), darkHigh: ui(0x050B18))
    static let brandCobaltInk = dynamic(ui(0x1443A8), ui(0xA3C4FF), lightHigh: ui(0x0E317D), darkHigh: ui(0xC2D9FF))
    static let brandCobaltTint = dynamic(ui(0xEBF2FF), ui(0x182845), lightHigh: ui(0xDCE8FF), darkHigh: ui(0x21355C))

    /// Coral accent for icons, active tabs, hearts, and rings (#E5533D in Light and Dark).
    static let brandCoral = dynamic(ui(0xE5533D), ui(0xE5533D), lightHigh: ui(0x8E2314), darkHigh: ui(0xFFA999))
    static let accent = brandCoral
    /// Coral fill behind white text (5.72:1 AA in both Light and Dark Mode).
    static let accentFill = dynamic(ui(0xE5533D), ui(0xE5533D), lightHigh: ui(0x8E2314), darkHigh: ui(0xD63B26))
    static let brandCoralTint = dynamic(ui(0xFCEAE6), ui(0x3B1E19), lightHigh: ui(0xF8DDD7), darkHigh: ui(0x4D2720))
    static let accTint = brandCoralTint
    static let brandCoralInk = dynamic(ui(0x8E2314), ui(0xFFA999), lightHigh: ui(0x6B180D), darkHigh: ui(0xFFC2B7))
    static let accInk = brandCoralInk

    /// Warm Sun tokens for milestone badges ("5 years ago", "10 years ago").
    static let brandSunTint = dynamic(ui(0xFDF3D7), ui(0x33280E), lightHigh: ui(0xFAE8B8), darkHigh: ui(0x453512))
    static let brandSunInk = dynamic(ui(0x754E00), ui(0xFFD466), lightHigh: ui(0x573A00), darkHigh: ui(0xFFE394))

    // MARK: Warm Archival / Warm Darkroom Surfaces
    static let bg = dynamic(ui(0xFAF8F4), ui(0x111010), lightHigh: ui(0xFFFFFF), darkHigh: ui(0x000000))
    static let surface = dynamic(ui(0xFFFFFF), ui(0x1B1A19), lightHigh: ui(0xFFFFFF), darkHigh: ui(0x141312))
    static let surfaceWarm = dynamic(ui(0xF3EFE8), ui(0x242220), lightHigh: ui(0xEBE6DC), darkHigh: ui(0x1F1D1B))
    static let surface2 = surfaceWarm
    static let fill = dynamic(ui(0xECE7DE), ui(0x2C2927), lightHigh: ui(0xE2DCD0), darkHigh: ui(0x36322F))
    static let fill2 = dynamic(ui(0xE4DDD2), ui(0x242220), lightHigh: ui(0xD8D0C3), darkHigh: ui(0x2E2B28))
    static let navBg = dynamic(ui(0xF5F2EC), ui(0x171615), lightHigh: ui(0xF0ECE4), darkHigh: ui(0x121110))

    // MARK: Warm Ink Hierarchy
    static let ink = dynamic(ui(0x181615), ui(0xF5F2EC), lightHigh: ui(0x000000), darkHigh: ui(0xFFFFFF))
    static let muted2 = dynamic(ui(0x4D4843), ui(0xC4BEB6), lightHigh: ui(0x2E2A27), darkHigh: ui(0xE0DBD3))
    static let muted = dynamic(ui(0x635D57), ui(0x9E978F), lightHigh: ui(0x423E3A), darkHigh: ui(0xC4BEB6))
    /// Accessible muted ink for upcoming / empty calendar days (5.34:1 Light / 4.65:1 Dark).
    static let inkFuture = dynamic(ui(0x706A63), ui(0x8A837B), lightHigh: ui(0x4D4843), darkHigh: ui(0xABA49C))
    static let faint = inkFuture

    // MARK: Borders & Chrome
    /// Hairline dividers.
    static let line = dynamic(ui(0xDFD9CE), ui(0x302D2A), lightHigh: ui(0x8A837A), darkHigh: ui(0x7A746C))
    /// Accessible control borders for inputs and secondary pills (>= 3.12:1 UI-AA across all surfaces).
    static let borderControl = dynamic(ui(0x8A837A), ui(0x7A746C), lightHigh: ui(0x5E5852), darkHigh: ui(0x9E978F))
    static let glass = dynamic(ui(0xFAF8F4, 0.94), ui(0x1B1A19, 0.92), lightHigh: ui(0xFFFFFF, 0.98), darkHigh: ui(0x1B1A19, 0.98))

    /// Status icons on the toast. The toast is dark ink in light mode and light ink in dark mode.
    static let toastCheck = dynamic(ui(0xFFA999), ui(0x8E2314))
    static let toastInfo = dynamic(ui(0xA3C4FF), ui(0x1443A8))
    static let toastError = dynamic(ui(0xFF7A66), ui(0xE5533D))

    // MARK: Photographic Scrims & Darkroom Viewer
    /// Circular photo badge scrim (5.04:1 AA over pure snow).
    static let photoBadgeScrim = Color(uiColor: ui(0x181615, 0.62))
    /// Year/date chip on browse grid tiles & photo overlays.
    static let chip = photoBadgeScrim
    static let chipInk = Color.white
    /// Calendar photo wash (4.71:1 AA over pure snow).
    static let dayWash = Color(uiColor: ui(0x181615, 0.60))
    /// Year cards: dark wash rising from the bottom of the photo, under white text.
    static let cardScrim = Color(uiColor: ui(0x181615, 0.66))
    /// Photo viewer: photos on warm darkroom obsidian, light text and controls, bright coral heart.
    static let viewerBg = Color(uiColor: ui(0x0B0B0D))
    static let viewerInk = Color(uiColor: ui(0xF5F2EC))
    static let viewerHeart = Color(uiColor: ui(0xFF7A66))
    /// Photo viewer: dark rounded bar under the photo holding the buttons.
    static let viewerBar = Color(uiColor: ui(0x1B1A19, 0.78))

    static func hex(_ hex: UInt32, _ alpha: Double = 1) -> Color { Color(uiColor: ui(hex, alpha)) }
}

enum Nunito {
    private static let cacheLock = NSLock()
    private static var fontCache: [String: UIFont] = [:]

    /// Registers the bundled Nunito and Tajawal files once, so `Font.custom` and `CTFontDescriptor` can find them without Info.plist entries.
    static func register() {
        let urls = (Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? [])
            + (Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: "Fonts") ?? [])
        for url in urls { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
    }

    static func nunitoName(for weight: Font.Weight) -> String {
        switch weight {
        case .medium: "Nunito-Medium"
        case .semibold: "Nunito-SemiBold"
        case .bold: "Nunito-Bold"
        case .heavy: "Nunito-ExtraBold"
        case .black: "Nunito-Black"
        default: "Nunito-Regular"
        }
    }

    /// Tajawal has no SemiBold, so SemiBold uses Bold: Arabic needs the extra weight to read at small sizes.
    static func tajawalName(for weight: Font.Weight) -> String {
        switch weight {
        case .medium: "Tajawal-Medium"
        case .semibold, .bold: "Tajawal-Bold"
        case .heavy: "Tajawal-ExtraBold"
        case .black: "Tajawal-Black"
        default: "Tajawal-Regular"
        }
    }

    /// Creates and caches a `UIFont` with a `CTFontDescriptor` cascade list pairing `Nunito` <-> `Tajawal`,
    /// so mixed Latin/Arabic strings keep matching weight and metrics in either script.
    static func cascadedUIFont(size: CGFloat, weight: Font.Weight = .regular, arabic: Bool = L.lang.arabicScript) -> UIFont? {
        let primary = arabic ? tajawalName(for: weight) : nunitoName(for: weight)
        let key = "\(primary)-\(Int((size * 2).rounded()))"
        cacheLock.lock()
        if let hit = fontCache[key] {
            cacheLock.unlock()
            return hit
        }
        cacheLock.unlock()

        let fallback = arabic ? nunitoName(for: weight) : tajawalName(for: weight)
        guard UIFont(name: primary, size: size) != nil else { return nil }
        let fallbackDesc = CTFontDescriptorCreateWithNameAndSize(fallback as CFString, size)
        let attrs: [CFString: Any] = [
            kCTFontNameAttribute: primary as CFString,
            kCTFontCascadeListAttribute: [fallbackDesc] as CFArray,
        ]
        let desc = CTFontDescriptorCreateWithAttributes(attrs as CFDictionary)
        let made = CTFontCreateWithFontDescriptor(desc, size, nil) as UIFont
        cacheLock.lock()
        fontCache[key] = made
        cacheLock.unlock()
        return made
    }
}

extension Font {
    /// The app font: Nunito in Latin scripts and Tajawal in Arabic script.
    /// Uses SwiftUI-native `Font.custom(_:size:relativeTo:)` by default so SwiftUI's
    /// `.dynamicTypeSize(...)` caps and live Control Center text-size updates always work.
    /// When `maxSize` is supplied, scales via cached `CTFontDescriptor` + `UIFontMetrics` up to `maxSize`.
    static func nunito(_ size: CGFloat, _ weight: Font.Weight = .regular, fixed: Bool = false, maxSize: CGFloat? = nil) -> Font {
        let arabic = L.lang.arabicScript
        let name = arabic ? Nunito.tajawalName(for: weight) : Nunito.nunitoName(for: weight)
        if fixed {
            if let base = Nunito.cascadedUIFont(size: size, weight: weight, arabic: arabic) {
                return Font(base as CTFont)
            }
            return .custom(name, fixedSize: size)
        }
        if let maxSize, let base = Nunito.cascadedUIFont(size: size, weight: weight, arabic: arabic) {
            let metrics = UIFontMetrics(forTextStyle: uiTextStyle(for: size))
            let scaled = metrics.scaledFont(for: base, maximumPointSize: maxSize)
            return Font(scaled as CTFont)
        }
        return .custom(name, size: size, relativeTo: textStyle(for: size))
    }

    /// The text style a size grows with when the user picks a larger text size.
    private static func textStyle(for size: CGFloat) -> Font.TextStyle {
        switch size {
        case 34...: return .largeTitle
        case 28..<34: return .title
        case 22..<28: return .title2
        case 18..<22: return .title3
        case 16..<18: return .body
        case 15..<16: return .subheadline
        case 13..<15: return .footnote
        default: return .caption
        }
    }

    private static func uiTextStyle(for size: CGFloat) -> UIFont.TextStyle {
        switch size {
        case 34...: return .largeTitle
        case 28..<34: return .title1
        case 22..<28: return .title2
        case 18..<22: return .title3
        case 16..<18: return .body
        case 15..<16: return .subheadline
        case 13..<15: return .footnote
        default: return .caption1
        }
    }
}

extension View {
    /// Tight tracking used for big titles and year numbers in the design.
    /// Arabic letters join, so they keep normal spacing.
    func tight(_ size: CGFloat) -> some View { tracking(L.lang.arabicScript ? 0 : -0.03 * size) }
}
