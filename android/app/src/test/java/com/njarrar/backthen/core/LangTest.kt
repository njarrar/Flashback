package com.njarrar.backthen.core

import java.io.File
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(listOf("ar", "en", "es", "fr"), LangFiles.codes)
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
        for (code in listOf("en", "fr", "es")) {
            val lang = LangFiles.load(code)
            assertTrue(!lang.rtl && !lang.arabicScript, "$code should be left to right")
        }
        // Every plural form Arabic uses is there.
        for (key in en.plurals.keys - "ago_years") {
            assertEquals(setOf("zero", "one", "two", "few", "many", "other"), ar.plurals.getValue(key).keys, key)
        }
    }

    @Test fun arabicPluralForms() {
        val cases = listOf(0 to "zero", 1 to "one", 2 to "two", 3 to "few", 10 to "few", 11 to "many",
            99 to "many", 100 to "other", 102 to "other", 103 to "few", 111 to "many")
        for ((n, form) in cases) assertEquals(form, pluralForm(n, "ar"), "ar $n")
        assertEquals("one", pluralForm(0, "fr"))
        assertEquals("other", pluralForm(2, "fr"))
        assertEquals("one", pluralForm(1, "es"))
        assertEquals("other", pluralForm(0, "en"))
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

    @Test fun missingWordsFallBackToEnglish() {
        L.use(en, Strings("ar", strings = mapOf("tab_today" to "اليوم")), Locale.ENGLISH)
        assertEquals("اليوم", L.s("tab_today"))
        assertEquals("Favorites", L.s("tab_favorites"))
        assertEquals("3 photos", L.p("photos", 3))
    }

    @Test fun picksThePhoneLanguage() {
        val have = listOf("ar", "en", "es", "fr")
        assertEquals("ar" to "ar-SA", L.pick(listOf("ar-SA"), have))
        assertEquals("fr", L.pick(listOf("de-DE", "fr-CA"), have).first)
        assertEquals("en", L.pick(listOf("de"), have).first)
        assertEquals("es", L.pick(listOf("es_MX"), have).first)
        assertEquals("pt-BR", L.pick(listOf("pt-BR"), have + "pt-BR").first)
    }

    @Test fun yearsAreNotGrouped() {
        L.use(en, en, Locale.US)
        assertEquals("2021", L.num(2021))
    }

    /** Every word the Kotlin and Swift code asks for is in en.xml. */
    @Test fun everyWordTheCodeUsesExists() {
        val sources = listOf(File("src/main/java"), File("../../ios/BackThen"))
            .flatMap { it.walkTopDown().filter { f -> f.extension in setOf("kt", "swift") && !f.name.startsWith("Lang") }.toList() }
        assertTrue(sources.size > 20)
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
