package com.njarrar.backthen

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingRefresh = Runnable {
        if (vm.saved.value.onboarded && vm.saved.value.sources.device) vm.refreshIndex()
    }
    private val mediaObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            mainHandler.removeCallbacks(pendingRefresh)
            mainHandler.postDelayed(pendingRefresh, 750L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug and QA builds take launch options: `--es lang ar` and `--es scene today`, for
        // screenshots and release QA. Store builds ignore them.
        val debuggable = BuildConfig.TEST_HOOKS
        // Again here, since a language change in Settings restarts the screen but not the app.
        startLanguage(this, intent?.getStringExtra("lang")?.takeIf { debuggable })
        enableEdgeToEdge()
        // Only on a fresh start: after a rotation the intent is the same, and the day may have moved on.
        if (savedInstanceState == null) openDayFrom(intent)
        intent?.getStringExtra("scene")?.takeIf { debuggable && savedInstanceState == null }?.let { vm.runDemo(it) }
        setContent {
            BackThenTheme { BackThenApp(vm) }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        }
    }

    override fun onResume() {
        super.onResume()
        vm.catchUpToToday()
        vm.syncPermissions()
    }

    override fun onStop() {
        super.onStop()
        mainHandler.removeCallbacks(pendingRefresh)
        runCatching { contentResolver.unregisterContentObserver(mediaObserver) }
        // Hidden days, sources or new photos may have changed while the app was open.
        TodayWidget.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openDayFrom(intent)
    }

    /** A tap on the daily reminder or home screen widget opens that day. */
    private fun openDayFrom(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TODAY, false) == true) {
            vm.openDay(MonthDay.today())
            return
        }
        val m = intent?.getIntExtra(EXTRA_MONTH, -1) ?: -1
        val d = intent?.getIntExtra(EXTRA_DAY, -1) ?: -1
        // Checked, since any app can start this screen with any numbers.
        MonthDay.fromKey("$m-$d")?.let(vm::openDay)
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "open_today"
    }
}
