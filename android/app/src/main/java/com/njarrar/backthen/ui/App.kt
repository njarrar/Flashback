package com.njarrar.backthen.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.njarrar.backthen.AppViewModel
import com.njarrar.backthen.core.Photo

private enum class Tab(val label: String) { Today("Today"), Favorites("Favorites"), Albums("Albums") }

@Composable
fun BackThenApp(vm: AppViewModel) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val state by vm.saved.collectAsStateWithLifecycle()
    val viewer by vm.viewer.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var albumFor by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val demo by vm.demoScene.collectAsStateWithLifecycle()
    LaunchedEffect(demo) {
        when (demo) {
            "favorites" -> tab = Tab.Favorites
            "album" -> { tab = Tab.Albums; openAlbum = "demo" }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!state.onboarded) {
            OnboardingScreen(vm)
        } else {
            Scaffold(
                containerColor = p.bg,
                bottomBar = {
                    if (!selecting) {
                        NavigationBar(containerColor = p.navBg) {
                            Tab.entries.forEach { t ->
                                val on = tab == t
                                NavigationBarItem(
                                    selected = on,
                                    onClick = { tab = t; if (t != Tab.Albums) openAlbum = null },
                                    icon = {
                                        Icon(
                                            when (t) {
                                                Tab.Today -> if (on) Icons.Filled.History else Icons.Outlined.History
                                                Tab.Favorites -> if (on) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
                                                Tab.Albums -> if (on) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary
                                            },
                                            contentDescription = null,
                                        )
                                    },
                                    label = { T(t.label, size = 12, weight = FontWeight.SemiBold, color = if (on) p.ink else p.muted2) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Accent,
                                        unselectedIconColor = p.muted2,
                                        indicatorColor = p.accTint,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { inner ->
                val bottom = inner.calculateBottomPadding()
                when (tab) {
                    Tab.Today -> TodayScreen(vm, bottom) { selecting = it }
                    Tab.Favorites -> FavoritesScreen(vm, bottom)
                    Tab.Albums -> {
                        val album = state.albums.firstOrNull { it.id == openAlbum }
                        if (album != null) AlbumScreen(vm, album, bottom) { openAlbum = null }
                        else AlbumsScreen(vm, bottom, onOpen = { openAlbum = it.id }, onNew = {
                            tab = Tab.Today
                            vm.say("Tap Select, pick photos, then tap Album")
                        })
                    }
                }
            }
        }

        viewer?.let { v ->
            ViewerScreen(
                photos = v.photos,
                start = v.start,
                favorites = state.favorites.map { it.key }.toSet(),
                onFavorite = vm::toggleFavorite,
                onShare = { sharePhotos(context, listOf(it), "Photo") },
                onAlbum = { albumFor = listOf(it.key) },
                onClose = { vm.viewer.value = null },
            )
            albumFor?.let { keys ->
                val photos: List<Photo> = v.photos.filter { it.key in keys }
                AlbumSheet(
                    photos, state.albums,
                    onCreate = { name ->
                        val a = vm.createAlbum(name, photos)
                        albumFor = null
                        vm.viewer.value = null
                        tab = Tab.Albums
                        openAlbum = a.id
                    },
                    onAdd = { a -> vm.addToAlbum(a.id, photos); albumFor = null },
                    onDismiss = { albumFor = null },
                )
            }
        }

        Toast(toast, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
    }
}
