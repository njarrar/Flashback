package com.njarrar.backthen

import android.app.Application
import com.njarrar.backthen.core.startLanguage

class BackThenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startLanguage(this)
    }
}
