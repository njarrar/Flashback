package com.njarrar.backthen

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.njarrar.backthen.core.startLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class BackThenApplication : Application(), SingletonImageLoader.Factory {
    /** Application-scoped single-threaded IO scope for serialized state persistence that survives Activity destruction. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    override fun onCreate() {
        super.onCreate()
        startLanguage(this)
        // Version 1.8 and earlier could import from Google Photos into this folder.
        val legacy = File(filesDir, "google-photos")
        if (legacy.exists()) {
            appScope.launch { runCatching { legacy.deleteRecursively() } }
        }
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
