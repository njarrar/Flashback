package com.njarrar.backthen

import androidx.lifecycle.viewModelScope
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.buildDayIndex
import kotlinx.coroutines.launch

/**
 * Debug-only screen setups for the README screenshots.
 * Launch with `am start -n com.njarrar.backthen/.MainActivity --es scene today|calendar|viewer|favorites|album`.
 */
fun AppViewModel.runDemo(scene: String) = viewModelScope.launch {
    val times = library.readAllTimes()
    updateSaved { it.copy(onboarded = true, sources = it.sources.copy(device = true), deviceIndex = buildDayIndex(times)) }
    val photos = photosFor(MonthDay.today()).flatMap { it.photos }
    if (photos.isEmpty()) return@launch
    val now = System.currentTimeMillis()
    val album = Album("demo", "Fall weekends", photos.take(9), now)
    updateSaved {
        it.copy(
            favorites = photos.filterIndexed { i, _ -> i % 3 == 0 },
            albums = listOf(album, Album("demo2", "Grandma's garden", photos.takeLast(5), now)),
        )
    }
    if (scene == "viewer") viewer.value = Viewer(photos, minOf(2, photos.size - 1))
    demoScene.value = scene
}
