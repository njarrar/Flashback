package com.njarrar.backthen

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.data.EXTRA_DAY
import com.njarrar.backthen.data.EXTRA_MONTH
import com.njarrar.backthen.ui.BackThenApp
import com.njarrar.backthen.ui.BackThenTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openDayFrom(intent)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        intent?.getStringExtra("scene")?.takeIf { debuggable && savedInstanceState == null }?.let { vm.runDemo(it) }
        setContent {
            BackThenTheme { BackThenApp(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        // New photos may have been taken since the app was last open.
        if (vm.saved.value.onboarded && vm.saved.value.sources.device) vm.refreshIndex()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openDayFrom(intent)
    }

    /** A tap on the daily reminder opens that day. */
    private fun openDayFrom(intent: Intent?) {
        val m = intent?.getIntExtra(EXTRA_MONTH, -1) ?: -1
        val d = intent?.getIntExtra(EXTRA_DAY, -1) ?: -1
        if (m >= 0 && d > 0) vm.day.value = MonthDay(m, d)
    }
}
