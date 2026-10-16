package com.njarrar.backthen.core

import java.io.InputStream
import java.text.NumberFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
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
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input).documentElement
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
    val a = kotlin.math.abs(n)
    val n10 = a % 10
    val n100 = a % 100
    return when (language.take(2)) {
        "ar" -> when {
            a == 0 -> "zero"
            a == 1 -> "one"
            a == 2 -> "two"
            n100 in 3..10 -> "few"
            n100 in 11..99 -> "many"
            else -> "other"
        }
        "fr", "pt", "hi", "fa", "bn" -> if (a <= 1) "one" else "other"
        "ru", "uk", "be" -> when {
            n10 == 1 && n100 != 11 -> "one"
            n10 in 2..4 && n100 !in 12..14 -> "few"
            else -> "many"
        }
        "pl" -> when {
            a == 1 -> "one"
            n10 in 2..4 && n100 !in 12..14 -> "few"
            else -> "many"
        }
        "cs", "sk" -> when (a) { 1 -> "one"; in 2..4 -> "few"; else -> "other" }
        "he" -> when (a) { 1 -> "one"; 2 -> "two"; else -> "other" }
        "zh", "ja", "ko", "th", "vi", "id", "ms" -> "other"
        else -> if (a == 1) "one" else "other"
    }
}

/**
 * The app's current language. The phone's language setting picks the file; any word
 * missing from it comes from English, so a new file can start small.
 */
object L {
    var english = Strings("en"); private set
    var lang = english; private set
    var locale: Locale = Locale.ENGLISH; private set

    fun use(english: Strings, lang: Strings, locale: Locale) {
        this.english = english
        this.lang = lang
        this.locale = locale
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
    fun s(key: String, vararg args: Pair<String, Any>): String =
        fill(lang.strings[key] ?: english.strings[key] ?: key, args.toMap())

    /** The plural word for [n], such as "3 photos". `{n}` is the number. */
    fun p(key: String, n: Int, vararg args: Pair<String, Any>): String {
        val forms = lang.plurals[key] ?: english.plurals[key] ?: emptyMap()
        val text = forms[pluralForm(n, lang.code)] ?: forms["other"]
            ?: english.plurals[key]?.get(pluralForm(n, "en")) ?: key
        return fill(text, args.toMap() + ("n" to n))
    }

    /** A whole number in the language's digits, without grouping (so years read 2021). */
    fun num(n: Int): String = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }.format(n)

    fun fill(text: String, args: Map<String, Any>): String {
        if ('{' !in text) return text
        var out = text
        for ((name, value) in args) out = out.replace("{$name}", if (value is Int) num(value) else value.toString())
        return out
    }
}
