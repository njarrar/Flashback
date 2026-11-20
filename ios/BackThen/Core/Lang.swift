import Foundation

/// The words of one language, read from `lang/<code>.xml`.
///
/// A file holds `<string name="key">` entries and `<plurals name="key">` entries with one
/// `<item quantity="...">` per plural form. Words can hold `{name}` slots, filled in by `L.s`.
struct Strings: Sendable {
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
        case "plurals":
            if let p = attributes["name"], !p.isEmpty {
                plural = p
                result.plurals[p] = [:]
            } else {
                plural = nil
            }
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
    let m = n.magnitude
    let n10 = m % 10
    let n100 = m % 100
    switch language.lowercased().prefix(2) {
    case "ar":
        if m == 0 { return "zero" }
        if m == 1 { return "one" }
        if m == 2 { return "two" }
        if (3...10).contains(n100) { return "few" }
        if (11...99).contains(n100) { return "many" }
        return "other"
    case "fr", "hi", "fa", "bn":
        return m <= 1 ? "one" : "other"
    case "ru", "uk", "be":
        if n10 == 1 && n100 != 11 { return "one" }
        if (2...4).contains(n10) && !(12...14).contains(n100) { return "few" }
        return "many"
    case "pl":
        if m == 1 { return "one" }
        if (2...4).contains(n10) && !(12...14).contains(n100) { return "few" }
        return "many"
    case "cs", "sk":
        return m == 1 ? "one" : (2...4).contains(m) ? "few" : "other"
    case "he":
        return m == 1 ? "one" : m == 2 ? "two" : "other"
    case "zh", "ja", "ko", "th", "vi", "id", "ms":
        return "other"
    default:
        return m == 1 ? "one" : "other"
    }
}

/// The app's current language. The phone's language setting picks the file; any word
/// missing from it comes from English, so a new file can start small.
enum L {
    private static let lock = NSLock()
    private static var _english = load("en") ?? Strings(code: "en")
    private static var _lang = _english
    private static var _locale = Locale(identifier: "en")
    private static var formatters: [String: DateFormatter] = [:]

    static var english: Strings { lock.withLock { _english } }
    static var lang: Strings { lock.withLock { _lang } }
    static var locale: Locale { lock.withLock { _locale } }

    private static func snapshot() -> (english: Strings, lang: Strings, locale: Locale) {
        lock.withLock { (_english, _lang, _locale) }
    }

    /// Codes of every language file in the app, such as `["ar", "en", "es", "fr"]`.
    static var available: [String] {
        (Bundle.main.urls(forResourcesWithExtension: "xml", subdirectory: "lang") ?? [])
            .map { $0.deletingPathExtension().lastPathComponent }.sorted()
    }

    /// Picks the language once, at launch.
    static func start(preferred: [String] = Locale.preferredLanguages) {
        let (code, tag) = pick(preferred: preferred, available: available)
        let loaded = load(code) ?? english
        use(loaded, locale: Locale(identifier: tag))
    }

    static func use(_ strings: Strings, locale: Locale) {
        lock.withLock {
            _lang = strings
            _locale = locale
            formatters.removeAll()
        }
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
        let snap = snapshot()
        return fill(snap.lang.strings[key] ?? snap.english.strings[key] ?? key, args, locale: snap.locale)
    }

    /// The plural word for `n`, such as "3 photos". `{n}` is the number.
    static func p(_ key: String, _ n: Int, _ args: [String: Any] = [:]) -> String {
        let snap = snapshot()
        let text: String
        if let words = snap.lang.plurals[key] {
            let form = pluralForm(n, language: snap.lang.code)
            if let hit = words[form] ?? words["other"] {
                text = hit
            } else {
                let fallback = snap.english.plurals[key] ?? [:]
                text = fallback[pluralForm(n, language: "en")] ?? fallback["other"] ?? key
            }
        } else {
            let fallback = snap.english.plurals[key] ?? [:]
            text = fallback[pluralForm(n, language: "en")] ?? fallback["other"] ?? key
        }
        var all = args
        all["n"] = n
        return fill(text, all, locale: snap.locale)
    }

    /// A whole number in the language's digits, without grouping (so years read 2021).
    static func num(_ n: Int) -> String {
        num(n, locale: locale)
    }

    private static func num(_ n: Int, locale: Locale) -> String {
        n.formatted(.number.grouping(.never).locale(locale))
    }

    /// Fills every `{name}` slot in one pass, so a value that holds a slot name (an album
    /// called "{n}") stays as typed.
    static func fill(_ text: String, _ args: [String: Any]) -> String {
        fill(text, args, locale: locale)
    }

    private static func fill(_ text: String, _ args: [String: Any], locale: Locale) -> String {
        guard !args.isEmpty, text.contains("{") else { return text }
        var out = ""
        var rest = text[...]
        while let open = rest.firstIndex(of: "{") {
            out += rest[..<open]
            let afterOpen = rest.index(after: open)
            guard let close = rest[afterOpen...].firstIndex(of: "}") else {
                out += rest[open...]
                return out
            }
            let name = String(rest[afterOpen..<close])
            if let value = args[name] {
                if let intVal = value as? Int {
                    out += num(intVal, locale: locale)
                } else if let intVal = value as? any BinaryInteger {
                    out += Int64(clamping: intVal).formatted(.number.grouping(.never).locale(locale))
                } else if let dblVal = value as? Double {
                    out += dblVal.formatted(.number.grouping(.never).locale(locale))
                } else {
                    out += "\(value)"
                }
                rest = rest[rest.index(after: close)...]
            } else {
                // Unknown slot or stray '{' (e.g. "{foo {n}"): emit only '{' and keep scanning.
                out.append("{")
                rest = rest[afterOpen...]
            }
        }
        return out + rest
    }

    // MARK: Dates, written the way the language writes them.

    /// A date from a CLDR skeleton such as "MMMMd" (October 16) or "EEEE" (Friday).
    static func date(_ date: Date, _ skeleton: String) -> String {
        lock.withLock {
            let loc = _locale
            let key = "\(loc.identifier)|\(skeleton)"
            if let cached = formatters[key] {
                return cached.string(from: date)
            }
            let f = DateFormatter()
            f.locale = loc
            // The app matches photos by Gregorian date; a phone set to Saudi Arabia would otherwise write the Hijri date.
            var gregorian = Calendar(identifier: .gregorian)
            gregorian.locale = loc
            gregorian.timeZone = .autoupdatingCurrent
            f.calendar = gregorian
            f.timeZone = .autoupdatingCurrent
            f.setLocalizedDateFormatFromTemplate(skeleton)
            formatters[key] = f
            return f.string(from: date)
        }
    }

    /// A month and day with no year, in a leap year so February 29 works.
    static func monthDay(_ md: MonthDay, _ skeleton: String) -> String {
        var c = DateComponents(year: 2024, month: md.month + 1, day: md.day, hour: 12)
        c.calendar = Calendar.app
        return date(c.date ?? Date(), skeleton)
    }

    static func monthName(_ month: Int) -> String {
        monthDay(MonthDay(month: month, day: 1), "LLLL")
    }

    /// The phone's 12 or 24 hour setting, which wins over the language's habit.
    private static var twelveHour: Bool {
        DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: .autoupdatingCurrent)?.contains("a") ?? false
    }

    static func time(hour: Int, minute: Int) -> String {
        var c = DateComponents(year: 2024, month: 1, day: 1, hour: hour, minute: minute)
        c.calendar = Calendar.app
        return date(c.date ?? Date(), twelveHour ? "hmm" : "Hmm")
    }

    /// "Friday 4:12 PM" for a photo's capture time.
    static func weekdayTime(_ time: Date) -> String {
        date(time, twelveHour ? "EEEEhmm" : "EEEEHmm")
    }
}
