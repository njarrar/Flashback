import CoreText
import SwiftUI

// Tokens from the design. One coral accent, kept for hearts, selection and the current day.

private func ui(_ hex: UInt32, _ alpha: CGFloat = 1) -> UIColor {
    UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
}

private func dynamic(_ light: UIColor, _ dark: UIColor) -> Color {
    Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? dark : light })
}

enum Palette {
    static let accent = Color(uiColor: ui(0xE5533D))
    static let bg = dynamic(ui(0xF6F6F3), ui(0x121213))
    static let surface = dynamic(ui(0xFFFFFF), ui(0x1C1C1E))
    static let surface2 = dynamic(ui(0xF3F2EE), ui(0x242426))
    static let ink = dynamic(ui(0x141416), ui(0xF2F2EF))
    static let muted = dynamic(ui(0x6E6E73), ui(0x9A9A9F))
    static let muted2 = dynamic(ui(0x45454A), ui(0xB4B4B8))
    static let faint = dynamic(ui(0xA3A3A8), ui(0x5E5E63))
    static let fill = dynamic(ui(0xECECE8), ui(0x2A2A2C))
    static let fill2 = dynamic(ui(0xE6E6E2), ui(0x232325))
    static let line = dynamic(ui(0x141416, 0.1), ui(0xFFFFFF, 0.12))
    static let glass = dynamic(ui(0xFFFFFF, 0.9), ui(0x2C2C2E, 0.88))
    static let accTint = dynamic(ui(0xFBE4DF), ui(0x3A211C))
    static let accInk = dynamic(ui(0x9A2E1E), ui(0xFF9C8A))
    /// Year chip on browse grid tiles: white text on a dark capsule, the same in both themes.
    static let chip = Color(uiColor: ui(0x000000, 0.36))
    static let chipInk = Color.white
    /// Photo viewer: photos on near-black, light text and controls, a brighter heart.
    static let viewerBg = Color(uiColor: ui(0x0D0D0E))
    static let viewerInk = Color(uiColor: ui(0xF4F4F2))
    static let viewerHeart = Color(uiColor: ui(0xFF6A52))

    static func hex(_ hex: UInt32, _ alpha: Double = 1) -> Color { Color(uiColor: ui(hex, alpha)) }
}

enum Nunito {
    /// Registers the bundled Nunito and Tajawal files once, so `Font.custom` can find them without Info.plist entries.
    static func register() {
        let urls = (Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? [])
            + (Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: "Fonts") ?? [])
        for url in urls { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
    }
}

extension Font {
    /// The app font: Nunito, or Tajawal for languages in Arabic script, since Nunito has no Arabic letters.
    static func nunito(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        if L.lang.arabicScript { return tajawal(size, weight) }
        let name: String
        switch weight {
        case .medium: name = "Nunito-Medium"
        case .semibold: name = "Nunito-SemiBold"
        case .bold: name = "Nunito-Bold"
        case .heavy: name = "Nunito-ExtraBold"
        case .black: name = "Nunito-Black"
        default: name = "Nunito-Regular"
        }
        return .custom(name, size: size, relativeTo: textStyle(for: size))
    }

    /// Tajawal has no SemiBold, so SemiBold uses Bold: Arabic needs the extra weight to read at small sizes.
    private static func tajawal(_ size: CGFloat, _ weight: Font.Weight) -> Font {
        let name: String
        switch weight {
        case .medium: name = "Tajawal-Medium"
        case .semibold, .bold: name = "Tajawal-Bold"
        case .heavy: name = "Tajawal-ExtraBold"
        case .black: name = "Tajawal-Black"
        default: name = "Tajawal-Regular"
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
}

extension View {
    /// Tight tracking used for big titles and year numbers in the design.
    /// Arabic letters join, so they keep normal spacing.
    func tight(_ size: CGFloat) -> some View { tracking(L.lang.arabicScript ? 0 : -0.03 * size) }
}
