package com.njarrar.backthen

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.data.EXTRA_DAY
import com.njarrar.backthen.data.EXTRA_MONTH
import com.njarrar.backthen.data.reminderTapIntent
import com.njarrar.backthen.widget.TodayWidget
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the app the ways a person does: from the reminder, from the widget, and from intents
 * another app could send. Versions 1.24 to 1.28 crashed on launch from the reminder, since
 * its "day" number landed where the app kept the shown day as text.
 */
@RunWith(AndroidJUnit4::class)
class LaunchTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val other = if (MonthDay.today() == MonthDay(2, 14)) MonthDay(6, 4) else MonthDay(2, 14)

    private fun shownDay(intent: Intent): MonthDay {
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var day: MonthDay? = null
            scenario.onActivity { day = ViewModelProvider(it)[AppViewModel::class.java].day.value }
            // A restart with saved state, as after the phone ends the app in the background.
            scenario.recreate()
            scenario.onActivity { ViewModelProvider(it)[AppViewModel::class.java].day.value }
            return day!!
        }
    }

    private fun plain() = Intent(ctx, MainActivity::class.java)

    @Test fun reminderTapOpensItsDay() {
        assertEquals(other, shownDay(reminderTapIntent(ctx, other)))
    }

    @Test fun widgetTapOpensToday() {
        assertEquals(MonthDay.today(), shownDay(TodayWidget.tapIntent(ctx)))
    }

    @Test fun plainLaunchOpensToday() {
        assertEquals(MonthDay.today(), shownDay(plain()))
    }

    @Test fun daySentAsTextStillOpens() {
        val intent = plain().putExtra(EXTRA_MONTH, other.month.toString()).putExtra(EXTRA_DAY, other.day.toString())
        assertEquals(other, shownDay(intent))
    }

    @Test fun oddExtrasNeverCrash() {
        val odd = listOf(
            plain().putExtra(EXTRA_MONTH, 99).putExtra(EXTRA_DAY, 99),
            plain().putExtra(EXTRA_MONTH, "x").putExtra(EXTRA_DAY, 3.5),
            plain().putExtra(EXTRA_DAY, 5),
            plain().putExtra("shownDay", 7).putExtra("shownDaySavedOn", 8L),
            plain().putExtra(MainActivity.EXTRA_OPEN_TODAY, "yes"),
        )
        for (intent in odd) assertEquals(MonthDay.today(), shownDay(intent))
    }
}
