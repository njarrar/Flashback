package com.njarrar.backthen

import androidx.lifecycle.viewModelScope
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.buildDayIndex
import com.njarrar.backthen.data.YearLayout
import kotlinx.coroutines.launch

/**
 * Debug-only screen setups for the README screenshots.
 * Launch with `am start -n com.njarrar.backthen/.MainActivity --es scene <scene>`, where scene is
 * today, calendar, viewer, favorites or album, or for the text check also reminder, settings,
 * select, share, addalbum, cards, strip or empty.
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
    // The sample photos skip tomorrow, so it shows the empty day.
    if (scene == "empty") day.value = MonthDay.of(java.time.LocalDate.now().plusDays(1))
    demoScene.value = scene
}
