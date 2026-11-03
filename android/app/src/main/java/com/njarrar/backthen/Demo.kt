package com.njarrar.backthen

import androidx.lifecycle.viewModelScope
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.buildDayIndex
import com.njarrar.backthen.data.YearLayout
import kotlinx.coroutines.launch
import android.net.Uri
import android.util.Log
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.ReelLook
import com.njarrar.backthen.core.ReelOptions
import com.njarrar.backthen.core.reelOrder
import com.njarrar.backthen.data.ReelExport
import com.njarrar.backthen.data.decodePhoto
import com.njarrar.backthen.ui.ReelFonts
import com.njarrar.backthen.ui.ReelRenderer
import com.njarrar.backthen.ui.ReelTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Debug-only screen setups for the README screenshots.
 * Launch with `am start -n com.njarrar.backthen/.MainActivity --es scene <scene>`, where scene is
 * today, calendar, viewer, favorites or album, or for the text check also reminder, settings,
 * select, share, addalbum, cards, strip, empty, reel or reelmore. reelexport makes a reel without
 * showing it and logs "Reel check" with the result, for the text check workflow.
 */
fun AppViewModel.runDemo(scene: String) = viewModelScope.launch {
    val times = library.readAllTimes()
    val layout = when (scene) { "cards" -> YearLayout.CARDS; "strip" -> YearLayout.STRIP; else -> YearLayout.MOSAIC }
    // The reminder step of the welcome, with the photos found.
    val onboarded = scene != "reminder"
    updateSaved { it.copy(onboarded = onboarded, sources = it.sources.copy(device = true), deviceIndex = buildDayIndex(times), layout = layout) }
    if (!onboarded) { demoScene.value = scene; return@launch }
    val photos = photosFor(MonthDay.today()).flatMap { it.photos }
    if (photos.isEmpty()) return@launch
    val now = System.currentTimeMillis()
    val album = Album("demo", L.s("demo_album_fall"), photos.take(9), now)
    updateSaved {
        it.copy(
            favorites = photos.filterIndexed { i, _ -> i % 3 == 0 },
            albums = listOf(album, Album("demo2", L.s("demo_album_garden"), photos.takeLast(5), now)),
        )
    }
    if (scene == "viewer") viewer.value = Viewer(photos, minOf(2, photos.size - 1))
    if (scene == "reel" || scene == "reelmore") reel.value = ReelOpen(photos, if (scene == "reel") ReelTab.STAMP else ReelTab.MORE)
    if (scene == "reelexport") checkReelExport(photos)
    // The sample photos skip tomorrow, so it shows the empty day.
    if (scene == "empty") day.value = MonthDay.of(java.time.LocalDate.now().plusDays(1))
    demoScene.value = scene
}

/** Makes a film-look reel of the day's photos and logs the result, so CI can check real video comes out. */
private fun AppViewModel.checkReelExport(photos: List<Photo>) = viewModelScope.launch {
    val app = getApplication<android.app.Application>()
    val result = runCatching {
        val ordered = reelOrder(photos.take(4), true)
        val small = withContext(Dispatchers.IO) { ordered.map { decodePhoto(app, Uri.parse(it.uri), 640) } }
        val renderer = ReelRenderer(ordered, ReelOptions(look = ReelLook.FILM), small, ReelFonts(app), reduceMotion = false)
        val file = ReelExport.make(app, renderer, load = { i -> withContext(Dispatchers.IO) { decodePhoto(app, Uri.parse(ordered[i].uri), ReelExport.PHOTO_SIDE) } }, progress = {})
        val meta = android.media.MediaMetadataRetriever().apply { setDataSource(file.path) }
        val w = meta.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val h = meta.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        val ms = meta.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        // Frames from the intro, a move, a photo and the end card, for a person to look at.
        val plan = renderer.plan
        val times = listOf(1.0, plan.items[1].start + plan.overlap / 2, plan.items[1].start + 1.5, plan.total - 0.5)
        val dir = app.getExternalFilesDir(null)
        var frames = 0
        times.forEachIndexed { n, s ->
            meta.getFrameAtTime((s * 1_000_000).toLong(), android.media.MediaMetadataRetriever.OPTION_CLOSEST)?.let { f ->
                java.io.File(dir, "reel-check-$n.png").outputStream().use { f.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                frames++
            }
        }
        meta.release()
        check(frames == times.size) { "only $frames frames decoded" }
        "ok ${w}x$h ${ms}ms ${file.length()} bytes, plan ${plan.total}s"
    }
    Log.i("Flashback", "Reel check: " + result.getOrElse { "failed " + it.stackTraceToString() })
}
