package com.njarrar.backthen.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.text.NumberFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import org.w3c.dom.Element

/**
 * The words of one language, read from `lang/<code>.xml`.
 *
 * A file holds `<string name="key">` entries and `<plurals name="key">` entries with one
 * `<item quantity="...">` per plural form. Words can hold `{name}` slots, filled in by [L.s].
 */
class Strings(
    val code: String,
    val name: String = "",
    /** Right to left, such as Arabic or Hebrew. */
    val rtl: Boolean = false,
    /** Written in Arabic script: the app switches to an Arabic font and drops tight letter spacing. */
    val arabicScript: Boolean = false,
    val strings: Map<String, String> = emptyMap(),
    val plurals: Map<String, Map<String, String>> = emptyMap(),
) {
    companion object {
        fun parse(code: String, input: InputStream): Strings {
            val factory = DocumentBuilderFactory.newInstance().apply {
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val root = factory.newDocumentBuilder().parse(input).documentElement
            fun children(parent: Element, tag: String) = parent.getElementsByTagName(tag).let { list ->
                (0 until list.length).map { list.item(it) as Element }
            }
            return Strings(
                code = code,
                name = root.getAttribute("name"),
                rtl = root.getAttribute("dir") == "rtl",
                arabicScript = root.getAttribute("script") == "arabic",
                strings = children(root, "string").associate { it.getAttribute("name") to it.textContent },
                plurals = children(root, "plurals").associate { p ->
                    p.getAttribute("name") to children(p, "item").associate { it.getAttribute("quantity") to it.textContent }
                },
            )
        }
    }
}

/**
 * The plural form a number takes in a language, by the Unicode CLDR rules.
 * Languages not listed use the English rule; a missing form falls back to "other".
 */
fun pluralForm(n: Int, language: String): String {
    val a = abs(n.toLong())
    val n10 = a % 10L
    val n100 = a % 100L
    return when (language.take(2)) {
        "ar" -> when {
            a == 0L -> "zero"
            a == 1L -> "one"
            a == 2L -> "two"
            n100 in 3L..10L -> "few"
            n100 in 11L..99L -> "many"
            else -> "other"
        }
        "fr", "hi", "fa", "bn" -> if (a <= 1L) "one" else "other"
        "ru", "uk", "be" -> when {
            n10 == 1L && n100 != 11L -> "one"
            n10 in 2L..4L && n100 !in 12L..14L -> "few"
            else -> "many"
        }
        "pl" -> when {
            a == 1L -> "one"
            n10 in 2L..4L && n100 !in 12L..14L -> "few"
            else -> "many"
        }
        "cs", "sk" -> when (a) { 1L -> "one"; in 2L..4L -> "few"; else -> "other" }
        "he" -> when (a) { 1L -> "one"; 2L -> "two"; else -> "other" }
        "zh", "ja", "ko", "th", "vi", "id", "ms" -> "other"
        // Includes "en", "es", "pt" (0 uses "other", 1 uses "one").
        else -> if (a == 1L) "one" else "other"
    }
}

/**
 * The app's current language. The phone's language setting picks the file; any word
 * missing from it comes from English, so a new file can start small.
 */
object L {
    private class Snapshot(
        val english: Strings,
        val lang: Strings,
        val locale: Locale,
    )

    @Volatile
    private var state = Snapshot(Strings("en"), Strings("en"), Locale.ENGLISH)

    /** Increments on every [use] call so Compose UI can observe language switches reactively. */
    val version = MutableStateFlow(0)

    val english: Strings get() = state.english
    val lang: Strings get() = state.lang
    val locale: Locale get() = state.locale

    private val numberFormatters = ThreadLocal<Pair<Locale, NumberFormat>>()

    fun use(english: Strings, lang: Strings, locale: Locale) {
        state = Snapshot(english, lang, locale)
        version.update { it + 1 }
    }

    /**
     * The first preferred language that has a file, and the full tag to format dates and
     * numbers with (so "ar-MA" keeps its own digits). English when none match.
     */
    fun pick(preferred: List<String>, available: List<String>): Pair<String, String> {
        val have = available.associateBy { it.lowercase() }
        for (tag in preferred) {
            val parts = tag.replace('_', '-').split('-')
            // Try "pt-BR" before "pt".
            for (n in parts.size downTo 1) {
                have[parts.take(n).joinToString("-").lowercase()]?.let { return it to tag }
            }
        }
        return "en" to "en"
    }

    /** A word, with its `{name}` slots filled. Numbers are written the language's way. */
    fun s(key: String, vararg args: Pair<String, Any>): String {
        val cur = state
        val raw = cur.lang.strings[key] ?: cur.english.strings[key] ?: key
        return if (args.isEmpty()) raw else fill(raw, args.toMap())
    }

    /** The plural word for [n], such as "3 photos". `{n}` is the number. */
    fun p(key: String, n: Int, vararg args: Pair<String, Any>): String {
        val cur = state
        val langForms = cur.lang.plurals[key]
        val text = if (langForms != null) {
            langForms[pluralForm(n, cur.lang.code)] ?: langForms["other"]
        } else {
            null
        } ?: cur.english.plurals[key]?.let { enForms ->
            enForms[pluralForm(n, "en")] ?: enForms["other"]
        } ?: key
        return fill(text, args.toMap() + ("n" to n))
    }

    /** A whole number in the language's digits, without grouping (so years read 2021). */
    fun num(n: Int): String {
        val loc = state.locale
        val cached = numberFormatters.get()
        val fmt = if (cached != null && cached.first == loc) {
            cached.second
        } else {
            NumberFormat.getIntegerInstance(loc).apply { isGroupingUsed = false }.also {
                numberFormatters.set(loc to it)
            }
        }
        return fmt.format(n)
    }

    /** Fills every `{name}` slot in one pass, so a value that holds a slot name (an album called "{n}") stays as typed. */
    fun fill(text: String, args: Map<String, Any>): String {
        if (args.isEmpty() || '{' !in text) return text
        return slot.replace(text) { m ->
            val value = args[m.groupValues[1]] ?: return@replace m.value
            if (value is Int) num(value) else value.toString()
        }
    }

    private val slot = Regex("""\{(\w+)\}""")
}
