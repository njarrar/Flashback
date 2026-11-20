import CoreText
import SwiftUI
import UIKit

// Tokens from the design. One coral accent, kept for hearts, selection and the current day.

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
    /// Adaptive WCAG AA coral accent for icons, text, rings, and active tab labels.
    static let accent = dynamic(ui(0xC93B25), ui(0xFF6B53), lightHigh: ui(0xA62A17), darkHigh: ui(0xFF8874))
    /// Solid WCAG AA coral fill behind white text (e.g., accent pills in both Light and Dark Mode).
    static let accentFill = dynamic(ui(0xC93B25), ui(0xC93B25), lightHigh: ui(0xA62A17), darkHigh: ui(0xD64028))
    static let bg = dynamic(ui(0xF6F6F3), ui(0x121213), lightHigh: ui(0xFFFFFF), darkHigh: ui(0x000000))
    static let surface = dynamic(ui(0xFFFFFF), ui(0x1C1C1E), lightHigh: ui(0xFFFFFF), darkHigh: ui(0x141416))
    static let surface2 = dynamic(ui(0xF3F2EE), ui(0x242426), lightHigh: ui(0xEDEDEA), darkHigh: ui(0x1E1E20))
    static let ink = dynamic(ui(0x141416), ui(0xF2F2EF), lightHigh: ui(0x000000), darkHigh: ui(0xFFFFFF))
    static let muted = dynamic(ui(0x5E5E63), ui(0x9A9A9F), lightHigh: ui(0x3D3D42), darkHigh: ui(0xC2C2C7))
    static let muted2 = dynamic(ui(0x45454A), ui(0xB4B4B8), lightHigh: ui(0x262629), darkHigh: ui(0xDCDCE0))
    static let faint = dynamic(ui(0x8C8C92), ui(0x6E6E73), lightHigh: ui(0x75757A), darkHigh: ui(0x8E8E93))
    static let fill = dynamic(ui(0xECECE8), ui(0x2A2A2C), lightHigh: ui(0xE2E2DC), darkHigh: ui(0x343438))
    static let fill2 = dynamic(ui(0xE6E6E2), ui(0x232325), lightHigh: ui(0xDCDCD6), darkHigh: ui(0x2E2E32))
    static let line = dynamic(ui(0x141416, 0.12), ui(0xFFFFFF, 0.14), lightHigh: ui(0x141416, 0.28), darkHigh: ui(0xFFFFFF, 0.28))
    static let glass = dynamic(ui(0xFFFFFF, 0.92), ui(0x2C2C2E, 0.9), lightHigh: ui(0xFFFFFF, 0.98), darkHigh: ui(0x242426, 0.98))
    static let accTint = dynamic(ui(0xFBE4DF), ui(0x3A211C), lightHigh: ui(0xF8D5CE), darkHigh: ui(0x4A261F))
    static let accInk = dynamic(ui(0x9A2E1E), ui(0xFF9C8A), lightHigh: ui(0x7A1E11), darkHigh: ui(0xFFB5A7))
    /// Status icons on the toast. The toast is dark ink in light mode and light ink in dark mode.
    static let toastCheck = dynamic(ui(0xFF8B76), ui(0x9A2E1E))
    static let toastInfo = dynamic(ui(0x9BC4FF), ui(0x1E56A0))
    static let toastError = dynamic(ui(0xFF8A7A), ui(0xB82514))
    /// Year chip on browse grid tiles & photo overlays: white text on a dark scrim, WCAG AA over bright photos.
    static let chip = Color(uiColor: ui(0x000000, 0.56))
    static let chipInk = Color.white
    /// Calendar: a dark wash over a day's photo, under its white number (WCAG AA over snow/sky).
    static let dayWash = Color(uiColor: ui(0x000000, 0.54))
    /// Year cards: a dark wash rising from the bottom of the photo, under white text.
    static let cardScrim = Color(uiColor: ui(0x000000, 0.62))
    /// Photo viewer: photos on near-black, light text and controls, a brighter heart.
    static let viewerBg = Color(uiColor: ui(0x0D0D0E))
    static let viewerInk = Color(uiColor: ui(0xF4F4F2))
    static let viewerHeart = Color(uiColor: ui(0xFF6A52))

    static func hex(_ hex: UInt32, _ alpha: Double = 1) -> Color { Color(uiColor: ui(hex, alpha)) }
}

enum Nunito {
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

    /// Creates a `UIFont` with a `CTFontDescriptor` cascade list pairing `Nunito` <-> `Tajawal`,
    /// so mixed Latin/Arabic strings keep matching weight and metrics in either script.
    static func cascadedUIFont(size: CGFloat, weight: Font.Weight = .regular, arabic: Bool = L.lang.arabicScript) -> UIFont? {
        let primary = arabic ? tajawalName(for: weight) : nunitoName(for: weight)
        let fallback = arabic ? nunitoName(for: weight) : tajawalName(for: weight)
        guard UIFont(name: primary, size: size) != nil else { return nil }
        let fallbackDesc = CTFontDescriptorCreateWithNameAndSize(fallback as CFString, size)
        let attrs: [CFString: Any] = [
            kCTFontNameAttribute: primary as CFString,
            kCTFontCascadeListAttribute: [fallbackDesc] as CFArray,
        ]
        let desc = CTFontDescriptorCreateWithAttributes(attrs as CFDictionary)
        return CTFontCreateWithFontDescriptor(desc, size, nil) as UIFont
    }
}

extension Font {
    /// The app font: Nunito paired with Tajawal (or Tajawal paired with Nunito in Arabic script)
    /// via a `CTFontDescriptor` cascade list so mixed-script strings keep matching weights.
    static func nunito(_ size: CGFloat, _ weight: Font.Weight = .regular, fixed: Bool = false, maxSize: CGFloat? = nil) -> Font {
        let arabic = L.lang.arabicScript
        if let base = Nunito.cascadedUIFont(size: size, weight: weight, arabic: arabic) {
            if fixed { return Font(base as CTFont) }
            let metrics = UIFontMetrics(forTextStyle: uiTextStyle(for: size))
            let scaled = maxSize.map { metrics.scaledFont(for: base, maximumPointSize: $0) }
                ?? metrics.scaledFont(for: base)
            return Font(scaled as CTFont)
        }
        let name = arabic ? Nunito.tajawalName(for: weight) : Nunito.nunitoName(for: weight)
        if fixed { return .custom(name, fixedSize: size) }
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
