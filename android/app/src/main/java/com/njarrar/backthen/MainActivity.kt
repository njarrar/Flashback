package com.njarrar.backthen

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.startLanguage
import com.njarrar.backthen.data.EXTRA_DAY
import com.njarrar.backthen.data.EXTRA_MONTH
import com.njarrar.backthen.ui.BackThenApp
import com.njarrar.backthen.ui.BackThenTheme
import com.njarrar.backthen.widget.TodayWidget

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Again here, since a language change in Settings restarts the screen but not the app.
        // Debug builds also take `--es lang ar`, for the Arabic screenshots.
        startLanguage(this, intent?.getStringExtra("lang")?.takeIf { debuggable })
        enableEdgeToEdge()
        // Only on a fresh start: after a rotation the intent is the same, and the day may have moved on.
        if (savedInstanceState == null) openDayFrom(intent)
        intent?.getStringExtra("scene")?.takeIf { debuggable && savedInstanceState == null }?.let { vm.runDemo(it) }
        setContent {
            BackThenTheme { BackThenApp(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.catchUpToToday()
        // New photos may have been taken since the app was last open.
        if (vm.saved.value.onboarded && vm.saved.value.sources.device) vm.refreshIndex()
    }

    override fun onStop() {
        super.onStop()
        // Hidden days, sources or new photos may have changed while the app was open.
        TodayWidget.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openDayFrom(intent)
    }

    /** A tap on the daily reminder opens that day. */
    private fun openDayFrom(intent: Intent?) {
        val m = intent?.getIntExtra(EXTRA_MONTH, -1) ?: -1
        val d = intent?.getIntExtra(EXTRA_DAY, -1) ?: -1
        // Checked, since any app can start this screen with any numbers.
        MonthDay.fromKey("$m-$d")?.let(vm::openDay)
    }
}
