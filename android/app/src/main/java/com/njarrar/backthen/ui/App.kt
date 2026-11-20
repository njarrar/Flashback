package com.njarrar.backthen.ui

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.njarrar.backthen.AppViewModel
import com.njarrar.backthen.ToastKind
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

enum class Tab(private val key: String) {
    Today("tab_today"), Favorites("tab_favorites"), Albums("tab_albums");

    val label: String get() = L.s(key)
}

@Composable
fun BackThenApp(vm: AppViewModel) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val view = LocalView.current
    val reduce = LocalReduceMotion.current
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val state by vm.saved.collectAsStateWithLifecycle()
    val viewer by vm.viewer.collectAsStateWithLifecycle()
    val reel by vm.reel.collectAsStateWithLifecycle()
    val toastState by vm.toastState.collectAsStateWithLifecycle()
    val demo by vm.demoScene.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    // Single source of truth for selection state across rotations, theme changes, and tab switches.
    var selecting by rememberSaveable { mutableStateOf(false) }
    var pickedKeys by rememberSaveable { mutableStateOf(listOf<String>()) }
    var albumFor by rememberSaveable { mutableStateOf<List<String>?>(null) }

    val tabs = rememberSaveableStateHolder()
    val reselect = remember { MutableSharedFlow<Tab>(extraBufferCapacity = 1) }

    // Clear selection whenever switching tabs or opening a day via reminder.
    LaunchedEffect(tab) {
        selecting = false
        pickedKeys = emptyList()
    }
    LaunchedEffect(Unit) {
        vm.openToday.collect {
            selecting = false
            pickedKeys = emptyList()
            tab = Tab.Today
        }
    }
    // Never leak stale albumFor across ViewerScreen dismissals.
    LaunchedEffect(viewer) { albumFor = null }

    LaunchedEffect(demo) {
        when (demo) {
            "favorites" -> tab = Tab.Favorites
            "album" -> { tab = Tab.Albums; openAlbum = "demo" }
        }
    }

    // Standard Android M3 back behavior: return to Today tab before exiting app.
    BackHandler(enabled = state.onboarded && tab != Tab.Today && openAlbum == null && !selecting && viewer == null && reel == null) {
        tab = Tab.Today
    }

    fun selectTab(t: Tab) {
        Haptics.tick(view)
        if (tab == t) {
            if (t == Tab.Albums && openAlbum != null) {
                openAlbum = null
            } else {
                reselect.tryEmit(t)
            }
        } else {
            tab = t
        }
    }

    val overlayActive = viewer != null || reel != null

    Box(Modifier.fillMaxSize()) {
        if (!state.onboarded) {
            OnboardingScreen(vm)
        } else {
            val mainContent: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { bottom ->
                tabs.SaveableStateProvider(tab.name) {
                    when (tab) {
                        Tab.Today -> TodayScreen(
                            vm = vm,
                            bottomPadding = bottom,
                            scrollTop = reselect.on(Tab.Today),
                            selecting = selecting,
                            pickedKeys = pickedKeys,
                            onSelectingChange = { selecting = it },
                            onPickedKeysChange = { pickedKeys = it },
                        )
                        Tab.Favorites -> FavoritesScreen(
                            vm = vm,
                            bottomPadding = bottom,
                            scrollTop = reselect.on(Tab.Favorites),
                            selecting = selecting,
                            pickedKeys = pickedKeys,
                            onSelectingChange = { selecting = it },
                            onPickedKeysChange = { pickedKeys = it },
                        )
                        Tab.Albums -> {
                            val album = state.albums.firstOrNull { it.id == openAlbum }
                            tabs.SaveableStateProvider(album?.id ?: "albums") {
                                if (album != null) {
                                    AlbumScreen(vm, album, bottom, reselect.on(Tab.Albums)) { openAlbum = null }
                                } else {
                                    AlbumsScreen(vm, bottom, reselect.on(Tab.Albums), onOpen = { openAlbum = it.id })
                                }
                            }
                        }
                    }
                }
            }

            if (wide) {
                // Adaptive NavigationRail for tablets, foldables, and landscape widths >= 600dp.
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(p.bg)
                        .semantics { if (overlayActive) hideFromAccessibility() },
                ) {
                    if (!selecting) {
                        NavigationRail(containerColor = p.navBg) {
                            Tab.entries.forEach { t ->
                                val on = tab == t
                                NavigationRailItem(
                                    selected = on,
                                    onClick = { selectTab(t) },
                                    icon = { TabIcon(t, on) },
                                    label = { TabLabel(t.label, on) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = Accent,
                                        unselectedIconColor = p.muted2,
                                        indicatorColor = p.accTint,
                                    ),
                                )
                            }
                        }
                        Box(Modifier.fillMaxHeight().width(1.dp).background(p.line))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        mainContent(0.dp)
                    }
                }
            } else {
                Scaffold(
                    containerColor = p.bg,
                    modifier = Modifier.semantics { if (overlayActive) hideFromAccessibility() },
                    bottomBar = {
                        if (!selecting) {
                            Column {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                                NavigationBar(containerColor = p.navBg) {
                                    Tab.entries.forEach { t ->
                                        val on = tab == t
                                        NavigationBarItem(
                                            selected = on,
                                            onClick = { selectTab(t) },
                                            icon = { TabIcon(t, on) },
                                            label = { TabLabel(t.label, on) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Accent,
                                                unselectedIconColor = p.muted2,
                                                indicatorColor = p.accTint,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) { inner ->
                    mainContent(inner.calculateBottomPadding())
                }
            }
        }

        // Full-screen ViewerScreen overlay with reduced-motion-aware AnimatedVisibility.
        var lastViewer by remember { mutableStateOf(viewer) }
        if (viewer != null) lastViewer = viewer
        AnimatedVisibility(
            visible = viewer != null,
            enter = if (reduce) EnterTransition.None else fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.96f),
            exit = if (reduce) ExitTransition.None else fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.96f),
        ) {
            val v = viewer ?: lastViewer
            if (v != null) {
                val favorites = remember(state.favorites) { state.favorites.map { it.key }.toSet() }
                ViewerScreen(
                    photos = v.photos,
                    start = v.start,
                    favorites = favorites,
                    onFavorite = vm::toggleFavorite,
                    onShare = { ph ->
                        if (!sharePhotos(context, listOf(ph), L.s("photo"))) {
                            vm.showToast(L.s("share_failed"), ToastKind.Error)
                        }
                    },
                    onAlbum = { albumFor = listOf(it.key) },
                    onClose = { vm.viewer.value = null },
                    onShowDay = { md -> vm.openDay(md) },
                    cityFor = { ph ->
                        withContext(Dispatchers.IO) {
                            if (!vm.places.hasCities) return@withContext null
                            val cities = vm.places.cities() ?: return@withContext null
                            var uri = Uri.parse(ph.uri)
                            if (Build.VERSION.SDK_INT >= 29) {
                                runCatching { uri = MediaStore.setRequireOriginal(uri) }
                            }
                            val latLon = runCatching {
                                context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).latLong }
                            }.getOrNull() ?: return@withContext null
                            val id = cities.city(latLon[0], latLon[1]) ?: return@withContext null
                            val city = cities.cities.getOrNull(id) ?: return@withContext null
                            val country = countryName(city.country)
                            if (country.isNotBlank()) "${city.name}, $country" else city.name
                        }
                    },
                )
                albumFor?.let { keys ->
                    val photos: List<Photo> = v.photos.filter { it.key in keys }
                    if (photos.isNotEmpty()) {
                        AlbumSheet(
                            photos,
                            state.albums,
                            onCreate = { name ->
                                val a = vm.createAlbum(name, photos)
                                albumFor = null
                                vm.viewer.value = null
                                tab = Tab.Albums
                                openAlbum = a.id
                            },
                            onAdd = { a ->
                                vm.addToAlbum(a.id, photos)
                                albumFor = null
                            },
                            onDismiss = { albumFor = null },
                        )
                    }
                }
            }
        }

        // Full-screen ReelScreen overlay with reduced-motion-aware AnimatedVisibility.
        var lastReel by remember { mutableStateOf(reel) }
        if (reel != null) lastReel = reel
        AnimatedVisibility(
            visible = reel != null,
            enter = if (reduce) EnterTransition.None else fadeIn(tween(200)),
            exit = if (reduce) ExitTransition.None else fadeOut(tween(150)),
        ) {
            val r = reel ?: lastReel
            if (r != null) {
                ReelScreen(
                    photos = r.photos,
                    startTab = r.tab,
                    onToast = { text, kind -> vm.showToast(text, kind) },
                    onClose = { vm.reel.value = null },
                )
            }
        }

        // Top-anchored non-occluding Toast driven by vm.toastState (never collides with SelectBar, Filmstrip, or Reel panel).
        Toast(
            state = toastState,
            onDismiss = vm::dismissToast,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp),
        )
    }
}

@Composable
private fun TabIcon(t: Tab, on: Boolean) {
    Icon(
        when (t) {
            Tab.Today -> if (on) Icons.Filled.History else Icons.Outlined.History
            Tab.Favorites -> if (on) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
            Tab.Albums -> if (on) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary
        },
        contentDescription = null,
    )
}

@Composable
private fun TabLabel(label: String, on: Boolean) {
    val p = LocalPalette.current
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, 1.3f))) {
        BasicText(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = if (on) p.ink else p.muted2,
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
            ),
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
        )
    }
}

/** Taps on the tab that is already showing, for that tab to scroll back to the top. */
private fun SharedFlow<Tab>.on(tab: Tab) = filter { it == tab }
