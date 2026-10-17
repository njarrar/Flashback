package com.njarrar.backthen

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.njarrar.backthen.core.startLanguage
import java.io.File

class BackThenApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startLanguage(this)
        // Version 1.8 and earlier could import from Google Photos into this folder.
        Thread { File(filesDir, "google-photos").deleteRecursively() }.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .crossfade(120)
            .build()
}
