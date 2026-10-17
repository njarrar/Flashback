package com.njarrar.backthen.data

import kotlinx.serialization.json.Json
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
        val s = json.decodeFromJsonElement(SavedState.serializer(), dropGooglePhotos(json.parseToJsonElement(old)))
        assertTrue(s.onboarded)
        assertEquals(listOf("d:1"), s.favorites.map { it.key })
        assertEquals(listOf("d:2"), s.albums.single().photos.map { it.key })
    }
}
