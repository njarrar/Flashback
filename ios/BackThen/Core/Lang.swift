import Foundation

/// The words of one language, read from `lang/<code>.xml`.
///
/// A file holds `<string name="key">` entries and `<plurals name="key">` entries with one
/// `<item quantity="...">` per plural form. Words can hold `{name}` slots, filled in by `L.s`.
struct Strings {
    let code: String
    var name = ""
    /// Right to left, such as Arabic or Hebrew.
    var rtl = false
    /// Written in Arabic script: the app switches to an Arabic font and drops tight letter spacing.
    var arabicScript = false
    var strings: [String: String] = [:]
    var plurals: [String: [String: String]] = [:]

    init(code: String) { self.code = code }

    init?(code: String, data: Data) {
        let reader = LangReader(code: code)
        let parser = XMLParser(data: data)
        parser.delegate = reader
        guard parser.parse() else { return nil }
        self = reader.result
    }
}

private final class LangReader: NSObject, XMLParserDelegate {
    var result: Strings
    private var key: String?
    private var plural: String?
    private var quantity: String?
    private var text = ""

    init(code: String) { result = Strings(code: code) }

    func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String] = [:]) {
        switch name {
        case "language":
            result.name = attributes["name"] ?? ""
            result.rtl = attributes["dir"] == "rtl"
            result.arabicScript = attributes["script"] == "arabic"
        case "string": key = attributes["name"]; text = ""
        case "plurals": plural = attributes["name"]; result.plurals[plural ?? ""] = [:]
        case "item": quantity = attributes["quantity"]; text = ""
        default: break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) { text += string }

    func parser(_ parser: XMLParser, didEndElement name: String, namespaceURI: String?, qualifiedName: String?) {
        switch name {
        case "string":
            if let key { result.strings[key] = text }
            key = nil
        case "item":
            if let plural, let quantity { result.plurals[plural]?[quantity] = text }
            quantity = nil
        case "plurals": plural = nil
        default: break
        }
    }
}

/// The plural form a number takes in a language, by the Unicode CLDR rules.
/// Languages not listed use the English rule; a missing form falls back to "other".
func pluralForm(_ n: Int, language: String) -> String {
    let n = abs(n), n10 = n % 10, n100 = n % 100
    switch language.prefix(2) {
    case "ar":
        if n == 0 { return "zero" }
        if n == 1 { return "one" }
        if n == 2 { return "two" }
        if (3...10).contains(n100) { return "few" }
        if (11...99).contains(n100) { return "many" }
        return "other"
    case "fr", "pt", "hi", "fa", "bn":
        return n <= 1 ? "one" : "other"
    case "ru", "uk", "be":
        if n10 == 1 && n100 != 11 { return "one" }
        if (2...4).contains(n10) && !(12...14).contains(n100) { return "few" }
        return "many"
    case "pl":
        if n == 1 { return "one" }
        if (2...4).contains(n10) && !(12...14).contains(n100) { return "few" }
        return "many"
    case "cs", "sk":
        return n == 1 ? "one" : (2...4).contains(n) ? "few" : "other"
    case "he":
        return n == 1 ? "one" : n == 2 ? "two" : "other"
    case "zh", "ja", "ko", "th", "vi", "id", "ms":
        return "other"
    default:
        return n == 1 ? "one" : "other"
    }
}

/// The app's current language. The phone's language setting picks the file; any word
/// missing from it comes from English, so a new file can start small.
enum L {
    static private(set) var english = load("en") ?? Strings(code: "en")
    static private(set) var lang = english
    static private(set) var locale = Locale(identifier: "en")

    /// Codes of every language file in the app, such as `["ar", "en", "es", "fr"]`.
    static var available: [String] {
        (Bundle.main.urls(forResourcesWithExtension: "xml", subdirectory: "lang") ?? [])
            .map { $0.deletingPathExtension().lastPathComponent }.sorted()
    }

    /// Picks the language once, at launch.
    static func start(preferred: [String] = Locale.preferredLanguages) {
        let (code, tag) = pick(preferred: preferred, available: available)
        use(load(code) ?? english, locale: Locale(identifier: tag))
    }

    static func use(_ strings: Strings, locale: Locale) {
        lang = strings
        self.locale = locale
    }

    /// The first preferred language that has a file, and the full tag to format dates and
    /// numbers with (so "ar-MA" keeps its own digits). English when none match.
    static func pick(preferred: [String], available: [String]) -> (code: String, tag: String) {
        let have = Dictionary(available.map { ($0.lowercased(), $0) }, uniquingKeysWith: { a, _ in a })
        for tag in preferred {
            let parts = tag.replacingOccurrences(of: "_", with: "-").split(separator: "-").map(String.init)
            // Try "pt-BR" before "pt".
            for n in stride(from: parts.count, through: 1, by: -1) {
                if let code = have[parts.prefix(n).joined(separator: "-").lowercased()] { return (code, tag) }
            }
        }
        return ("en", "en")
    }

    static func load(_ code: String) -> Strings? {
        guard let url = Bundle.main.url(forResource: code, withExtension: "xml", subdirectory: "lang"),
              let data = try? Data(contentsOf: url) else { return nil }
        return Strings(code: code, data: data)
    }

    /// A word, with its `{name}` slots filled. Numbers are written the language's way.
    static func s(_ key: String, _ args: [String: Any] = [:]) -> String {
        fill(lang.strings[key] ?? english.strings[key] ?? key, args)
    }

    /// The plural word for `n`, such as "3 photos". `{n}` is the number.
    static func p(_ key: String, _ n: Int, _ args: [String: Any] = [:]) -> String {
        let form = pluralForm(n, language: lang.code)
        let words = lang.plurals[key] ?? english.plurals[key] ?? [:]
        let fallback = english.plurals[key] ?? [:]
        let text = words[form] ?? words["other"] ?? fallback[pluralForm(n, language: "en")] ?? key
        var all = args
        all["n"] = n
        return fill(text, all)
    }

    /// A whole number in the language's digits, without grouping (so years read 2021).
    static func num(_ n: Int) -> String {
        n.formatted(.number.grouping(.never).locale(locale))
    }

    static func fill(_ text: String, _ args: [String: Any]) -> String {
        guard text.contains("{") else { return text }
        var out = text
        for (name, value) in args {
            let shown = (value as? Int).map(num) ?? "\(value)"
            out = out.replacingOccurrences(of: "{\(name)}", with: shown)
        }
        return out
    }

    // MARK: Dates, written the way the language writes them.

    /// A date from a CLDR skeleton such as "MMMMd" (October 16) or "EEEE" (Friday).
    static func date(_ date: Date, _ skeleton: String) -> String {
        let f = DateFormatter()
        f.locale = locale
        f.setLocalizedDateFormatFromTemplate(skeleton)
        return f.string(from: date)
    }

    /// A month and day with no year, in a leap year so February 29 works.
    static func monthDay(_ md: MonthDay, _ skeleton: String) -> String {
        var c = DateComponents(year: 2024, month: md.month + 1, day: md.day, hour: 12)
        c.calendar = Calendar(identifier: .gregorian)
        return date(c.date ?? Date(), skeleton)
    }

    static func monthName(_ month: Int) -> String {
        monthDay(MonthDay(month: month, day: 1), "LLLL")
    }

    static func time(hour: Int, minute: Int) -> String {
        var c = DateComponents(year: 2024, month: 1, day: 1, hour: hour, minute: minute)
        c.calendar = Calendar(identifier: .gregorian)
        return date(c.date ?? Date(), "jmm")
    }
}
