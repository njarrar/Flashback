package com.njarrar.backthen.core

import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReelTest {
    private val zone = ZoneId.of("America/New_York")
    private fun at(y: Int, h: Int = 12) = LocalDateTime.of(y, 11, 3, h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun photo(y: Int, h: Int = 12) = Photo("d:$y-$h", PhotoSource.DEVICE, "content://media/$y-$h", 1200, 900, at(y, h))
    private fun near(a: Double, b: Double) = assertTrue(abs(a - b) < 1e-9, "$a is not $b")

    @Test fun planAddsIntroPhotosAndEndCard() {
        val plan = ReelPlan(3, ReelOptions())
        assertEquals(
            listOf(ReelPlan.Kind.Intro, ReelPlan.Kind.Photo(0), ReelPlan.Kind.Photo(1), ReelPlan.Kind.Photo(2), ReelPlan.Kind.Outro),
            plan.items.map { it.kind },
        )
        // 2 + 3 × 2.8 + 2, less four moves of 0.65 each.
        near(9.8, plan.total)
        assertEquals(294, plan.frameCount)
    }

    @Test fun planWithoutEndCardEndsOnLastPhoto() {
        val plan = ReelPlan(2, ReelOptions(endCard = false))
        assertEquals(ReelPlan.Kind.Photo(1), plan.items.last().kind)
        assertEquals(ReelPlan.Kind.Photo(1), plan.moment(plan.total).a.kind)
        assertNull(plan.moment(plan.total).b)
    }

    @Test fun momentsCoverTheWholeReel() {
        val plan = ReelPlan(4, ReelOptions())
        assertEquals(ReelPlan.Kind.Intro, plan.moment(0.0).a.kind)
        val m = plan.moment(plan.items[1].start + plan.overlap / 2)
        assertEquals(ReelPlan.Kind.Intro, m.a.kind)
        assertEquals(ReelPlan.Kind.Photo(0), m.b?.kind)
        near(0.5, m.progress)
        assertEquals(ReelPlan.Kind.Outro, plan.moment(plan.total).a.kind)
        for (f in 0..plan.frameCount) {
            val x = plan.moment(f.toDouble() / ReelPlan.FPS)
            assertTrue(x.progress in 0.0..1.0)
            x.b?.let { assertTrue(x.a.start < it.start) }
        }
    }

    @Test fun speedsAndMoveLengths() {
        val slow = ReelPlan(1, ReelOptions(speed = ReelSpeed.SLOW, move = ReelMove.FLASH))
        near(0.45, slow.overlap)
        near(3.8, slow.items[1].length)
        near(0.5, ReelPlan(1, ReelOptions(move = ReelMove.SLIDE)).overlap)
    }

    @Test fun reduceMotionTurnsFlashIntoFade() {
        assertEquals(ReelMove.FADE, ReelPlan(2, ReelOptions(move = ReelMove.FLASH), reduceMotion = true).move)
        assertEquals(ReelMove.FLASH, ReelPlan(2, ReelOptions(move = ReelMove.FLASH), reduceMotion = false).move)
    }

    @Test fun planCapsPhotos() {
        assertEquals(ReelPlan.MAX_PHOTOS + 2, ReelPlan(50, ReelOptions()).items.size)
    }

    @Test fun orderNewestOrOldestFirstAndCapped() {
        val photos = listOf(photo(2019), photo(2025), photo(2015), photo(2025, 9))
        assertEquals(listOf("d:2025-12", "d:2025-9", "d:2019-12", "d:2015-12"), reelOrder(photos, true).map { it.key })
        assertEquals(listOf("d:2015-12", "d:2019-12", "d:2025-9", "d:2025-12"), reelOrder(photos, false).map { it.key })
        val many = (1980 until 2025).map { photo(it) }
        assertEquals(ReelPlan.MAX_PHOTOS, reelOrder(many, true).size)
        assertEquals("d:2024-12", reelOrder(many, true).first().key)
    }

    @Test fun filmStampLooksLikeACamera() {
        assertEquals("'15 11 3", filmStamp(at(2015), zone))
        assertEquals("'05 11 3", filmStamp(at(2005), zone))
    }

    @Test fun easeRunsZeroToOne() {
        near(0.0, reelEase(0.0))
        near(0.5, reelEase(0.5))
        near(1.0, reelEase(1.0))
        assertTrue(reelEase(0.25) < 0.25 && reelEase(0.75) > 0.75)
    }

    @Test fun sizes() {
        assertEquals(1080 to 1920, ReelSize.STORY.width to ReelSize.STORY.height)
        assertEquals(1080 to 1350, ReelSize.PORTRAIT.width to ReelSize.PORTRAIT.height)
        assertEquals(1080 to 1080, ReelSize.SQUARE.width to ReelSize.SQUARE.height)
    }

    @Test fun optionsSaveAndLoad() {
        val o = ReelOptions(look = ReelLook.FILM, size = ReelSize.SQUARE, endCard = false)
        val text = Json.encodeToString(ReelOptions.serializer(), o)
        assertEquals(o, Json.decodeFromString(ReelOptions.serializer(), text))
        // Options saved by an older version, with fewer fields, still load.
        assertEquals(ReelOptions(look = ReelLook.CLEAN), Json { ignoreUnknownKeys = true }.decodeFromString(ReelOptions.serializer(), "{\"look\":\"CLEAN\"}"))
    }

    @Test fun everyLanguageHasTheReelWords() {
        val en = LangFiles.load("en")
        val keys = en.strings.keys.filter { it.startsWith("reel") || it == "make_reel" || it == "toast_reel_saved" }
        assertTrue(keys.size > 30)
        for (code in listOf("fr", "es", "ar")) {
            val lang = LangFiles.load(code)
            for (k in keys) assertTrue(k in lang.strings, "$code is missing $k")
            assertTrue("reel_too_many" in lang.plurals, "$code is missing reel_too_many")
        }
        L.use(en, en, java.util.Locale.ENGLISH)
        assertEquals("A reel holds up to 30 photos, so it uses the first 30", L.p("reel_too_many", 30))
        assertEquals("3 years · 2015 to 2025", L.s("reel_years_range", "years" to L.p("years", 3), "from" to L.num(2015), "to" to L.num(2025)))
    }
}
