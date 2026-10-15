package com.njarrar.backthen

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade

class BackThenApplication : Application(), SingletonImageLoader.Factory {
    // Keeps decoded thumbnails in up to a quarter of the app's memory, and fades photos in.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .crossfade(150)
            .build()
}
