package com.njarrar.backthen.core

import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The lang/ folder at the top of the repo, as the app packs it. Tests run from android/app. */
object LangFiles {
    val dir = File("../../lang")
    val codes: List<String> get() = dir.listFiles().orEmpty().filter { it.extension == "xml" }.map { it.nameWithoutExtension }.sorted()
    fun load(code: String) = File(dir, "$code.xml").inputStream().use { Strings.parse(code, it) }
}

/** Checks the files in lang/ and how the app reads them. */
class LangTest {
    private val slot = Regex("\\{(\\w+)\\}")
    private fun slots(text: String) = slot.findAll(text).map { it.groupValues[1] }.toSet()
    private val en = LangFiles.load("en")

    @AfterTest fun reset() = L.use(en, en, Locale.ENGLISH)

    @Test fun shipsEveryLanguage() {
        assertEquals(listOf("ar", "en", "es", "fr", "pt", "zh"), LangFiles.codes)
    }

    @Test fun everyLanguageHasEveryWordWithTheSameSlots() {
        assertTrue(en.strings.size > 100)
        for (code in LangFiles.codes - "en") {
            val lang = LangFiles.load(code)
            assertTrue(lang.name.isNotBlank(), "$code has no name")
            for ((key, text) in en.strings) {
                val t = assertNotNull(lang.strings[key], "$code is missing $key")
                assertTrue(t.isNotBlank(), "$code $key is empty")
                assertEquals(slots(text), slots(t), "$code $key has different {slots}")
            }
            assertEquals(emptySet(), lang.strings.keys - en.strings.keys, "$code has words English does not")
            for ((key, forms) in en.plurals) {
                val mine = assertNotNull(lang.plurals[key], "$code is missing plurals $key")
                assertNotNull(mine["other"], "$code $key has no 'other' form")
                val allowed = slots(forms.getValue("other"))
                for ((form, t) in mine) assertTrue(allowed.containsAll(slots(t)), "$code $key $form has unknown {slots}")
            }
        }
    }

    @Test fun arabicIsRightToLeftInArabicScript() {
        val ar = LangFiles.load("ar")
        assertTrue(ar.rtl)
        assertTrue(ar.arabicScript)
        assertEquals("العربية", ar.name)
        for (code in listOf("en", "fr", "es", "pt", "zh")) {
            val lang = LangFiles.load(code)
            assertTrue(!lang.rtl && !lang.arabicScript, "$code should be left to right")
        }
        // Every plural form Arabic uses is there.
        for (key in en.plurals.keys - "ago_years") {
            assertEquals(setOf("zero", "one", "two", "few", "many", "other"), ar.plurals.getValue(key).keys, key)
        }
    }

    @Test fun arabicAndEdgePluralForms() {
        val cases = listOf(0 to "zero", 1 to "one", 2 to "two", 3 to "few", 10 to "few", 11 to "many",
            99 to "many", 100 to "other", 102 to "other", 103 to "few", 111 to "many")
        for ((n, form) in cases) assertEquals(form, pluralForm(n, "ar"), "ar $n")
        assertEquals("one", pluralForm(0, "fr"))
        assertEquals("other", pluralForm(2, "fr"))
        assertEquals("other", pluralForm(0, "pt"))
        assertEquals("one", pluralForm(1, "pt"))
        assertEquals("other", pluralForm(2, "pt"))
        assertEquals("one", pluralForm(1, "es"))
        assertEquals("other", pluralForm(0, "en"))
        // Int.MIN_VALUE must not overflow abs(Int) into a negative modulo.
        assertEquals("other", pluralForm(Int.MIN_VALUE, "en"))
        assertEquals("many", pluralForm(Int.MIN_VALUE, "ar"))
    }

    @Test fun arabicWords() {
        // Western digits here: the JVM has no Arabic digit data. The phone uses ICU and shows ١٢٣.
        L.use(en, LangFiles.load("ar"), Locale.ENGLISH)
        assertEquals("صورة واحدة", L.p("photos", 1))
        assertEquals("صورتان", L.p("photos", 2))
        assertEquals("5 صور", L.p("photos", 5))
        assertEquals("25 صورة", L.p("photos", 25))
        assertEquals("100 صورة", L.p("photos", 100))
        assertEquals("قبل سنتين", L.p("ago_years", 2))
        assertEquals("اليوم", L.s("tab_today"))
        assertEquals("صورتان من 3 سنوات", L.s("photos_from_years", "photos" to L.p("photos", 2), "years" to L.p("years", 3)))
    }

    @Test fun englishFrenchSpanish() {
        L.use(en, en, Locale.ENGLISH)
        assertEquals("1 photo from 3 years", L.s("photos_from_years", "photos" to L.p("photos", 1), "years" to L.p("years", 3)))
        assertEquals("{years} from 3 years", L.s("photos_from_years", "photos" to "{years}", "years" to "3 years"))
        L.use(en, LangFiles.load("fr"), Locale.FRENCH)
        assertEquals("0 photo", L.p("photos", 0))
        assertEquals("2 photos", L.p("photos", 2))
        assertEquals("Il y a 1 an", L.p("ago_years", 1))
        L.use(en, LangFiles.load("es"), Locale.forLanguageTag("es"))
        assertEquals("4 años", L.p("years", 4))
        assertEquals("Hoy", L.s("tab_today"))
    }

    @Test fun portugueseAndChinese() {
        L.use(en, LangFiles.load("pt"), Locale.forLanguageTag("pt-BR"))
        assertEquals("0 fotos", L.p("photos", 0))
        assertEquals("1 foto", L.p("photos", 1))
        assertEquals("2 fotos", L.p("photos", 2))
        assertEquals("Há 3 anos", L.p("ago_years", 3))
        assertEquals("Hoje", L.s("tab_today"))
        L.use(en, LangFiles.load("zh"), Locale.forLanguageTag("zh-CN"))
        assertEquals("1 张照片", L.p("photos", 1))
        assertEquals("5 张照片", L.p("photos", 5))
        assertEquals("3 年前", L.p("ago_years", 3))
        assertEquals("今天", L.s("tab_today"))
        // Chinese has one plural form: every key has "other" and nothing else.
        for ((key, forms) in LangFiles.load("zh").plurals) assertEquals(setOf("other"), forms.keys, key)
    }

    @Test fun missingWordsFallBackToEnglishUsingEnglishPluralRule() {
        L.use(en, Strings("ar", strings = mapOf("tab_today" to "اليوم")), Locale.ENGLISH)
        assertEquals("اليوم", L.s("tab_today"))
        assertEquals("Favorites", L.s("tab_favorites"))
        assertEquals("3 photos", L.p("photos", 3))
        // French treats 0 as "one", but when falling back to English it must use English's rule ("0 photos").
        L.use(en, Strings("fr"), Locale.FRENCH)
        assertEquals("0 photos", L.p("photos", 0))
        assertEquals("1 photo", L.p("photos", 1))
        // Portuguese fallback for 0 also yields "0 photos".
        L.use(en, Strings("pt"), Locale.forLanguageTag("pt-BR"))
        assertEquals("0 photos", L.p("photos", 0))
    }

    @Test fun languageVersionIncrementsOnSwitch() {
        val before = L.version.value
        L.use(en, LangFiles.load("fr"), Locale.FRENCH)
        assertEquals(before + 1, L.version.value)
    }

    @Test fun xmlParserRejectsDoctype() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE foo [<!ENTITY x "boom">]><language name="Test"><string name="a">&x;</string></language>"""
        assertFails {
            Strings.parse("xx", ByteArrayInputStream(xxe.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test fun picksThePhoneLanguage() {
        val have = listOf("ar", "en", "es", "fr")
        assertEquals("ar" to "ar-SA", L.pick(listOf("ar-SA"), have))
        assertEquals("fr", L.pick(listOf("de-DE", "fr-CA"), have).first)
        assertEquals("en", L.pick(listOf("de"), have).first)
        assertEquals("es", L.pick(listOf("es_MX"), have).first)
        assertEquals("pt-BR", L.pick(listOf("pt-BR"), have + "pt-BR").first)
        val all = have + "pt" + "zh"
        assertEquals("pt", L.pick(listOf("pt-BR"), all).first)
        assertEquals("pt", L.pick(listOf("pt-PT"), all).first)
        assertEquals("zh", L.pick(listOf("zh-Hans-CN"), all).first)
        assertEquals("zh", L.pick(listOf("zh-CN"), all).first)
    }

    @Test fun yearsAreNotGrouped() {
        L.use(en, en, Locale.US)
        assertEquals("2021", L.num(2021))
    }

    /** The widget picker's text lives in Android resources; it must say the same as lang/. */
    @Test fun widgetDescriptionMatchesLang() {
        for (code in LangFiles.codes) {
            val folder = if (code == "en") "values" else "values-$code"
            val res = File("src/main/res/$folder/strings.xml").readText()
            val want = LangFiles.load(code).strings.getValue("widget_description")
            assertTrue(res.contains(">$want</string>"), "$folder/strings.xml widget_description should be \"$want\"")
        }
    }

    /** Dynamically constructed reel keys exist in every language. */
    @Test fun dynamicKeysExistInEveryLanguage() {
        val dynamicKeys = ReelMove.entries.flatMap { m ->
            val k = m.name.lowercase(Locale.ROOT)
            listOf("reel_move_$k", "reel_move_${k}_note")
        } + ReelSpeed.entries.map { "reel_speed_${it.name.lowercase(Locale.ROOT)}" }
        for (code in LangFiles.codes) {
            val lang = LangFiles.load(code)
            for (key in dynamicKeys) {
                assertNotNull(lang.strings[key], "$code is missing dynamic key $key")
            }
        }
    }

    /** Every word the Kotlin and Swift code asks for is in en.xml. */
    @Test fun everyWordTheCodeUsesExists() {
        val sources = listOf(File("src/main/java"), File("../../ios/BackThen"), File("../../ios/BackThenWidget"))
            .filter { it.exists() }
            .flatMap { it.walkTopDown().filter { f -> f.extension in setOf("kt", "swift") && !f.name.startsWith("Lang") }.toList() }
        assertTrue(sources.size > 15)
        var seen = 0
        for (file in sources) for ((kind, key) in usedKeys(file.readText())) {
            seen++
            if (kind == 's') assertNotNull(en.strings[key], "${file.name} uses $key, not in en.xml")
            else assertNotNull(en.plurals[key], "${file.name} uses plurals $key, not in en.xml")
        }
        assertTrue(seen > 150, "only found $seen words in the code")
    }

    /** The keys in the first argument of each L.s( and L.p( call, such as L.s(if (on) "a" else "b"). */
    private fun usedKeys(text: String): List<Pair<Char, String>> {
        val out = mutableListOf<Pair<Char, String>>()
        val word = Regex("\"([a-z0-9_]+)\"")
        var i = text.indexOf("L.")
        while (i >= 0) {
            val kind = text.getOrNull(i + 2)
            if ((kind == 's' || kind == 'p') && text.getOrNull(i + 3) == '(') {
                // Read up to the first comma or closing paren at this call's own depth.
                var j = i + 4
                var depth = 0
                var quoted = false
                while (j < text.length) {
                    val c = text[j]
                    if (quoted) { if (c == '\\') j++ else if (c == '"') quoted = false }
                    else if (c == '"') quoted = true
                    else if (c == '(' || c == '[') depth++
                    else if ((c == ')' || c == ']') && depth-- == 0) break
                    else if (c == ',' && depth == 0) break
                    j++
                }
                word.findAll(text.substring(i + 4, j)).forEach { out += kind to it.groupValues[1] }
            }
            i = text.indexOf("L.", i + 2)
        }
        return out
    }
}
