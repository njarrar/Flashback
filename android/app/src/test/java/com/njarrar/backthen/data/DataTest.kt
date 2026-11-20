package com.njarrar.backthen.data

import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.PhotoSource
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataTest {
    @Test fun hiddenDaysLeaveTheIndex() {
        val s = SavedState(
            sources = Sources(device = true),
            deviceIndex = mapOf("9-2" to mapOf(2019 to 3), "9-3" to mapOf(2020 to 1)),
            hiddenDates = setOf("9-2"),
        )
        assertEquals(setOf("9-3"), visibleIndex(s).keys)
    }

    @Test fun oldGooglePhotosAreDropped() {
        fun photo(key: String, source: String) = """{"key":"$key","source":"$source","uri":"x","width":4,"height":3,"time":0}"""
        val old = """{"onboarded":true,"sources":{"device":true,"google":true},"googlePhotos":[${photo("g:0", "GOOGLE")}],""" +
            """"favorites":[${photo("d:1", "DEVICE")},${photo("g:1", "GOOGLE")}],""" +
            """"albums":[{"id":"a","name":"Trip","createdAt":0,"photos":[${photo("g:2", "GOOGLE")},${photo("d:2", "DEVICE")}]}]}"""
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val s = decodeLossy(json, json.parseToJsonElement(old))
        assertTrue(s.onboarded)
        assertEquals(listOf("d:1"), s.favorites.map { it.key })
        assertEquals(listOf("d:2"), s.albums.single().photos.map { it.key })
    }

    @Test fun lossyDecodingSurvivesCorruptPhotosAndFields() {
        val dir = Files.createTempDirectory("state-lossy").toFile()
        try {
            val file = File(dir, "state.json")
            file.writeText(
                """
                {
                  "onboarded": true,
                  "sources": {"device": true},
                  "indexZone": "Asia/Dubai",
                  "customHomeCity": 42,
                  "lastNotifiedEpochDay": 20500,
                  "hiddenDates": ["9-2"],
                  "favorites": [
                    {"key":"d:1","uri":"content://1","width":10,"height":10,"time":1000},
                    {"key":"d:bad","source":"UNKNOWN_FUTURE_SOURCE","uri":"content://bad","width":10,"height":10,"time":1000},
                    {"broken":true}
                  ],
                  "albums": [
                    {"id":"a1","name":"Trip","createdAt":123,"photos":[
                      {"key":"d:2","source":"DEVICE","uri":"content://2","width":4,"height":3,"time":2000},
                      {"missing_fields":1}
                    ]},
                    {"not_an_album":true}
                  ]
                }
                """.trimIndent(),
            )
            val loaded = StateFile(file).read()
            assertTrue(loaded.onboarded)
            assertEquals("Asia/Dubai", loaded.indexZone)
            assertEquals(42, loaded.customHomeCity)
            assertEquals(20500L, loaded.lastNotifiedEpochDay)
            assertEquals(setOf("9-2"), loaded.hiddenDates)
            // Photo missing "source" defaults to DEVICE; UNKNOWN_FUTURE_SOURCE coerced to DEVICE; malformed entry dropped.
            assertTrue(loaded.favorites.any { it.key == "d:1" && it.source == PhotoSource.DEVICE })
            assertEquals(1, loaded.albums.size)
            assertEquals("Trip", loaded.albums.single().name)
            assertEquals(listOf("d:2"), loaded.albums.single().photos.map { it.key })
            // Original file was NOT quarantined as bad because valid data was recovered.
            assertTrue(file.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun librarySignatureIsSavedWithTheIndex() {
        val dir = Files.createTempDirectory("state-sig").toFile()
        try {
            val file = File(dir, "state.json")
            val sig = LibrarySignature(count = 42, latestId = 7, latestModified = 1_700_000_000, oldestId = 1, mediaVersion = "v1", mediaGeneration = 99L)
            StateFile(file).write(SavedState(deviceIndex = mapOf("9-2" to mapOf(2019 to 3)), librarySignature = sig))
            assertEquals(sig, StateFile(file).read().librarySignature)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun aFileThatWontLoadIsKeptWithTimestamp() {
        val dir = Files.createTempDirectory("state-bad").toFile()
        try {
            val file = File(dir, "state.json")
            val store = StateFile(file)
            store.write(SavedState(onboarded = true, albums = listOf(Album("a", "Trip", emptyList(), 0))))
            assertEquals("Trip", store.read().albums.single().name)

            file.writeText("""{"albums":[{"id":""")
            assertEquals(SavedState(), store.read())
            assertTrue(!file.exists())
            val firstBad = dir.listFiles().orEmpty().filter { it.name.startsWith("state.bad.") && it.name.endsWith(".json") }
            assertEquals(1, firstBad.size)
            assertEquals("""{"albums":[{"id":""", firstBad.single().readText())

            // A second corruption does not overwrite the earlier backup.
            file.writeText("""not-json-2""")
            assertEquals(SavedState(), store.read())
            val allBad = dir.listFiles().orEmpty().filter { it.name.startsWith("state.bad.") && it.name.endsWith(".json") }
            assertEquals(2, allBad.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun albumFolderNameAndMimeExtensionSanitization() {
        assertEquals("Fall-2025_Trip", sanitizeAlbumFolderName("Fall/2025_Trip"))
        assertEquals("Album", sanitizeAlbumFolderName("... /\\:*?\"<>| ..."))
        assertEquals("heic", extensionForMimeType("image/heic"))
        assertEquals("webp", extensionForMimeType("image/webp"))
        assertEquals("png", extensionForMimeType("IMAGE/PNG"))
    }

    @Test fun reminderDelayRespectsWallClockAcrossDst() {
        val ny = ZoneId.of("America/New_York")
        // Day before spring-forward (2026-03-07 09:00 -> 2026-03-08 09:00 is 23 hours).
        val beforeSpring = ZonedDateTime.of(2026, 3, 7, 9, 0, 0, 0, ny)
        assertEquals(23L * 3_600_000L, nextReminderDelayMillis(9, 0, beforeSpring))
        // Day before fall-back (2026-10-31 09:00 -> 2026-11-01 09:00 is 25 hours).
        val beforeFall = ZonedDateTime.of(2026, 10, 31, 9, 0, 0, 0, ny)
        assertEquals(25L * 3_600_000L, nextReminderDelayMillis(9, 0, beforeFall))
        // skipToday forces tomorrow even when current time is slightly before target.
        val early = ZonedDateTime.of(2026, 5, 10, 8, 59, 55, 0, ny)
        assertEquals(5_000L, nextReminderDelayMillis(9, 0, early, skipToday = false))
        assertEquals(24L * 3_600_000L + 5_000L, nextReminderDelayMillis(9, 0, early, skipToday = true))
    }
}
