import CoreText
import UIKit
import XCTest
@testable import Back_Then

/// Checks the files in lang/ and how the app reads them.
final class LangTests: XCTestCase {
    private let saved = (L.lang, L.locale)

    override func tearDown() {
        L.use(saved.0, locale: saved.1)
    }

    private func load(_ code: String) throws -> Strings {
        try XCTUnwrap(L.load(code), "lang/\(code).xml is missing from the app or does not parse")
    }

    private func slots(_ text: String) -> Set<String> {
        let re = try! NSRegularExpression(pattern: "\\{(\\w+)\\}")
        let ns = text as NSString
        return Set(re.matches(in: text, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range(at: 1)) })
    }

    func testAppShipsEveryLanguage() {
        XCTAssertEqual(Set(L.available), ["ar", "en", "es", "fr"])
        // The build step makes an .lproj folder per language, which is what tells iOS the app speaks it.
        for code in ["ar", "en", "es", "fr"] {
            XCTAssertTrue(Bundle.main.localizations.contains(code), "no \(code).lproj in the app")
        }
    }

    func testEveryLanguageHasEveryWordWithTheSameSlots() throws {
        let en = try load("en")
        XCTAssertGreaterThan(en.strings.count, 100)
        for code in L.available where code != "en" {
            let lang = try load(code)
            XCTAssertFalse(lang.name.isEmpty, "\(code) has no name")
            for (key, text) in en.strings {
                let t = try XCTUnwrap(lang.strings[key], "\(code) is missing \(key)")
                XCTAssertFalse(t.trimmingCharacters(in: .whitespaces).isEmpty, "\(code) \(key) is empty")
                XCTAssertEqual(slots(t), slots(text), "\(code) \(key) has different {slots}")
            }
            XCTAssertEqual(Set(lang.strings.keys).subtracting(en.strings.keys), [], "\(code) has words English does not")
            for (key, forms) in en.plurals {
                let mine = try XCTUnwrap(lang.plurals[key], "\(code) is missing plurals \(key)")
                XCTAssertNotNil(mine["other"], "\(code) \(key) has no 'other' form")
                let allowed = slots(forms["other"] ?? "")
                for (form, t) in mine {
                    XCTAssertTrue(slots(t).isSubset(of: allowed), "\(code) \(key) \(form) has unknown {slots}")
                }
            }
        }
    }

    func testDirectionMatchesTheLanguage() throws {
        for code in L.available {
            let lang = try load(code)
            let rtl = Locale.Language(identifier: code).characterDirection == .rightToLeft
            XCTAssertEqual(lang.rtl, rtl, "\(code) dir is wrong")
        }
        let ar = try load("ar")
        XCTAssertTrue(ar.rtl)
        XCTAssertTrue(ar.arabicScript)
    }

    func testArabicPluralForms() {
        let cases: [(Int, String)] = [(0, "zero"), (1, "one"), (2, "two"), (3, "few"), (10, "few"), (11, "many"),
                                      (99, "many"), (100, "other"), (102, "other"), (103, "few"), (111, "many")]
        for (n, form) in cases { XCTAssertEqual(pluralForm(n, language: "ar"), form, "ar \(n)") }
        XCTAssertEqual(pluralForm(0, language: "fr"), "one")
        XCTAssertEqual(pluralForm(2, language: "fr"), "other")
        XCTAssertEqual(pluralForm(1, language: "es"), "one")
        XCTAssertEqual(pluralForm(0, language: "en"), "other")
    }

    func testArabicWords() throws {
        // Plain "ar" uses Western digits on iOS; the phone's digit choice comes in the locale.
        L.use(try load("ar"), locale: Locale(identifier: "ar"))
        XCTAssertEqual(L.p("photos", 5), "5 صور")
        L.use(try load("ar"), locale: Locale(identifier: "ar_SA@numbers=arab"))
        XCTAssertEqual(L.p("photos", 1), "صورة واحدة")
        XCTAssertEqual(L.p("photos", 2), "صورتان")
        XCTAssertEqual(L.p("photos", 5), "٥ صور")
        XCTAssertEqual(L.p("photos", 25), "٢٥ صورة")
        XCTAssertEqual(L.num(2021), "٢٠٢١")
        XCTAssertEqual(L.s("tab_today"), "اليوم")
        XCTAssertTrue(MonthDay(month: 9, day: 16).label.contains("أكتوبر"))
    }

    func testEnglishWordsAndSlots() throws {
        L.use(try load("en"), locale: Locale(identifier: "en_US"))
        XCTAssertEqual(L.s("photos_from_years", ["photos": L.p("photos", 1), "years": L.p("years", 3)]), "1 photo from 3 years")
        XCTAssertEqual(L.s("photos_from_years", ["photos": "{years}", "years": "3 years"]), "{years} from 3 years")
        XCTAssertEqual(L.num(2021), "2021")
        XCTAssertEqual(MonthDay(month: 1, day: 29).label, "February 29")
        XCTAssertEqual(agoLabel(2020, thisYear: 2026), "6 years ago")
        XCTAssertEqual(agoLabel(2025, thisYear: 2026), "1 year ago")
    }

    func testFrenchAndSpanish() throws {
        L.use(try load("fr"), locale: Locale(identifier: "fr_FR"))
        XCTAssertEqual(L.p("photos", 0), "0 photo")
        XCTAssertEqual(L.p("photos", 2), "2 photos")
        XCTAssertEqual(MonthDay(month: 9, day: 16).label, "16 octobre")
        L.use(try load("es"), locale: Locale(identifier: "es_ES"))
        XCTAssertEqual(L.p("years", 1), "1 año")
        XCTAssertEqual(L.p("years", 4), "4 años")
    }

    func testMissingWordsFallBackToEnglish() {
        var tiny = Strings(code: "ar")
        tiny.strings["tab_today"] = "اليوم"
        L.use(tiny, locale: Locale(identifier: "ar"))
        XCTAssertEqual(L.s("tab_today"), "اليوم")
        XCTAssertEqual(L.s("tab_favorites"), "Favorites")
    }

    func testPicksThePhoneLanguage() {
        let have = ["ar", "en", "es", "fr"]
        XCTAssertEqual(L.pick(preferred: ["ar-SA"], available: have).code, "ar")
        XCTAssertEqual(L.pick(preferred: ["ar-SA"], available: have).tag, "ar-SA")
        XCTAssertEqual(L.pick(preferred: ["de-DE", "fr-CA"], available: have).code, "fr")
        XCTAssertEqual(L.pick(preferred: ["de"], available: have).code, "en")
        XCTAssertEqual(L.pick(preferred: ["es_MX"], available: have).code, "es")
    }

    func testArabicFontHasArabicLetters() {
        Nunito.register()
        for name in ["Tajawal-Regular", "Tajawal-Medium", "Tajawal-Bold", "Tajawal-ExtraBold", "Tajawal-Black"] {
            let font = UIFont(name: name, size: 16)
            XCTAssertNotNil(font, "\(name) is not in the app")
            guard let font else { continue }
            let chars = Array("صورة ٢٠٢١".utf16)
            var glyphs = [CGGlyph](repeating: 0, count: chars.count)
            XCTAssertTrue(CTFontGetGlyphsForCharacters(font as CTFont, chars, &glyphs, chars.count), "\(name) lacks Arabic letters")
        }
    }

    /// Every word the Swift code asks for is in en.xml.
    func testEveryWordTheCodeUsesExists() throws {
        let en = try load("en")
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().appending(path: "BackThen")
        let files = FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil)?
            .compactMap { $0 as? URL }.filter { $0.pathExtension == "swift" } ?? []
        XCTAssertFalse(files.isEmpty)
        let call = try NSRegularExpression(pattern: "L\\.(s|p)\\(([^,)]*)")
        let word = try NSRegularExpression(pattern: "\"([a-z0-9_]+)\"")
        var seen = 0
        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            let ns = text as NSString
            for m in call.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
                let kind = ns.substring(with: m.range(at: 1))
                let args = ns.substring(with: m.range(at: 2))
                let ans = args as NSString
                for w in word.matches(in: args, range: NSRange(location: 0, length: ans.length)) {
                    let key = ans.substring(with: w.range(at: 1))
                    seen += 1
                    if kind == "s" {
                        XCTAssertNotNil(en.strings[key], "\(file.lastPathComponent) uses \(key), not in en.xml")
                    } else {
                        XCTAssertNotNil(en.plurals[key], "\(file.lastPathComponent) uses plurals \(key), not in en.xml")
                    }
                }
            }
        }
        XCTAssertGreaterThan(seen, 80)
    }
}
