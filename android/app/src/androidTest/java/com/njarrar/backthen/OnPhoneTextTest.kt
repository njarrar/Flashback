package com.njarrar.backthen

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Strings
import com.njarrar.backthen.core.startLanguage
import com.njarrar.backthen.data.ReminderWorker
import com.njarrar.backthen.data.Reminders
import com.njarrar.backthen.data.SavedState
import com.njarrar.backthen.data.Sources
import com.njarrar.backthen.data.StateFile
import com.njarrar.backthen.data.reminderText
import com.njarrar.backthen.data.Reminder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Runs on a phone or emulator, with Android's own XML reader and the app's packed files.
 * The desktop tests in src/test use a different reader, which is how 1.24 shipped with
 * every word showing as its key. These tests catch that before a release.
 */
@RunWith(AndroidJUnit4::class)
class OnPhoneTextTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val permissions: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private val codes: List<String> =
        ctx.assets.list("").orEmpty().filter { it.endsWith(".xml") }.map { it.removeSuffix(".xml") }.sorted()

    private val english: Strings = ctx.assets.open("en.xml").use { Strings.parse("en", it) }

    @After fun reset() {
        startLanguage(ctx)
    }

    /** A word that came out as its own key, such as "reminder_title_years". */
    private fun looksLikeKey(text: String) =
        (english.strings.keys + english.plurals.keys).any { it.contains('_') && text.contains(it) }

    @Test fun everyLanguageFileLoadsOnThePhone() {
        assertEquals(listOf("ar", "en", "es", "fr", "pt", "zh"), codes)
        assertTrue("en.xml read as empty", english.strings.size > 100)
        for (code in codes) {
            startLanguage(ctx, only = code)
            assertTrue("$code: no words loaded", L.ready)
            assertEquals("$code: wrong file loaded", code, L.lang.code)
            assertEquals("$code: words missing on the phone", english.strings.keys, L.lang.strings.keys)
            for (key in english.strings.keys) {
                assertFalse("$code: $key shows as its key", L.s(key) == key)
            }
            for (key in english.plurals.keys) {
                for (n in listOf(0, 1, 2, 3, 11, 100)) {
                    assertFalse("$code: plural $key for $n shows as its key", L.p(key, n) == key)
                }
            }
        }
    }

    @Test fun reminderTextInEveryLanguage() {
        val day = MonthDay.of(LocalDate.of(2026, 11, 22))
        for (code in codes) {
            startLanguage(ctx, only = code)
            for (years in listOf(listOf(2024), listOf(2025, 2019), listOf(2025, 2024, 2023, 2022, 2021, 2020, 2019, 2018, 2017, 2016, 2015))) {
                val text = reminderText(day, years)
                assertNotNull("$code: no reminder text", text)
                val (title, body) = text!!
                assertFalse("$code: title shows a key: $title", looksLikeKey(title))
                assertFalse("$code: body shows a key: $body", looksLikeKey(body))
                assertFalse("$code: unfilled slot in $title", title.contains('{'))
                assertFalse("$code: unfilled slot in $body", body.contains('{'))
            }
        }
    }

    /** Runs the real reminder job, as the phone does at the chosen time, and reads what it posted. */
    @Test fun reminderJobPostsRealText() {
        val today = LocalDate.now()
        val key = MonthDay.of(today).key
        val stateFile = StateFile(ctx)
        val saved = stateFile.read()
        val manager = ctx.getSystemService(NotificationManager::class.java)
        try {
            stateFile.write(
                SavedState(
                    onboarded = true,
                    sources = Sources(device = true),
                    reminder = Reminder(on = true, hour = 9, minute = 0),
                    deviceIndex = mapOf(key to mapOf(today.year - 3 to 4, today.year - 7 to 2)),
                    // Same zone as the phone, so the job keeps this index instead of rebuilding it.
                    indexZone = ZoneId.systemDefault().id,
                ),
            )
            val worker = TestListenableWorkerBuilder<ReminderWorker>(ctx).build()
            val result = runBlocking { worker.doWork() }
            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals("the reminder job did not mark today as sent", today.toEpochDay(), stateFile.read().lastNotifiedEpochDay)
            // notify() hands the post to the system on another thread, so wait for it to show.
            var posted = manager.activeNotifications.firstOrNull { it.id == 1 }
            val until = System.currentTimeMillis() + 10_000
            while (posted == null && System.currentTimeMillis() < until) {
                Thread.sleep(200)
                posted = manager.activeNotifications.firstOrNull { it.id == 1 }
            }
            assertNotNull("the reminder job posted nothing", posted)
            val extras = posted!!.notification.extras
            val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString()
            val body = extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString()
            assertFalse("title shows a key: $title", looksLikeKey(title))
            assertFalse("body shows a key: $body", looksLikeKey(body))
            assertTrue("body has no year: $body", body.contains(L.num(today.year - 7)))
        } finally {
            manager.cancel(1)
            Reminders.cancel(ctx)
            stateFile.write(saved)
        }
    }
}
