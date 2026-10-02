package com.njarrar.backthen.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test fun tokenOnlyGoesToGoogle() {
        assertTrue(isGoogleHttps("https://photospicker.googleapis.com/v1/sessions"))
        assertTrue(isGoogleHttps("https://lh3.googleusercontent.com/abc=w2048-h2048"))
        assertFalse(isGoogleHttps("http://lh3.googleusercontent.com/abc"))
        assertFalse(isGoogleHttps("https://evil.com/googleusercontent.com"))
        assertFalse(isGoogleHttps("https://googleusercontent.com.evil.com/x"))
        assertFalse(isGoogleHttps("not a url"))
    }
}
