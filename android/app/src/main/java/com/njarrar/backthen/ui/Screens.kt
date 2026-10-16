package com.njarrar.backthen.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.njarrar.backthen.AppViewModel
import com.njarrar.backthen.R
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.YearGroup
import com.njarrar.backthen.core.formatDate
import com.njarrar.backthen.core.formatTime
import com.njarrar.backthen.core.fullDate
import com.njarrar.backthen.core.label
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.nearbyDays
import com.njarrar.backthen.core.shortLabel
import com.njarrar.backthen.core.yearsForDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Asks for photo access, then notification access, as each step needs it. */
@Composable
fun rememberPhotoPermission(vm: AppViewModel, onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val ok = vm.library.hasAccess()
        if (ok) vm.connectDevice()
        onResult(ok)
    }
    return { launcher.launch(vm.library.permissions) }
}

@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onResult(true) }
}

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val state by vm.saved.collectAsStateWithLifecycle()
    val busy by vm.googleImport.collectAsStateWithLifecycle()
    var denied by remember { mutableStateOf(false) }
    var google by remember { mutableStateOf(false) }
    var chosePhone by remember { mutableStateOf(false) }
    val today = MonthDay.today()
    val askPhotos = rememberPhotoPermission(vm) { ok -> denied = !ok; chosePhone = ok }
    val askNotify = rememberNotificationPermission { ok ->
        vm.finishOnboarding(ok)
        if (ok) vm.say(L.s("toast_reminder_set", "time" to formatTime(state.reminder.hour, state.reminder.minute)))
    }
    val startGoogle = rememberGoogleImport(vm)

    if (chosePhone || state.sources.google) {
        // Step two: the reminder.
        val years = yearsForDay(vm.index(state), today)
        Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().padding(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Logo(40.dp)
                    Column(Modifier.weight(1f)) {
                        Row {
                            T(L.s("app_name"), size = 14, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            T(formatTime(state.reminder.hour, state.reminder.minute), size = 14, muted = true)
                        }
                        T(
                            if (years.isEmpty()) L.s("reminder_title_any", "day" to today.shortLabel())
                            else L.s("reminder_title_years", "day" to today.shortLabel(), "years" to L.p("years", years.size)),
                            size = 14, weight = FontWeight.SemiBold,
                        )
                        T(if (years.isEmpty()) L.s("reminder_body") else L.s("reminder_body_oldest", "year" to years.last()), size = 14, muted = true)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(L.s("reminder_onboarding_title"), size = 32, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                    T(L.s("reminder_onboarding_body"), muted = true)
                }
            }
            Pill(L.s("turn_on_reminder"), askNotify, modifier = Modifier.fillMaxWidth())
            Pill(L.s("not_now"), { vm.finishOnboarding(false) }, kind = Kind.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth())
        }
        return
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Logo(36.dp)
            T(L.s("app_name"), size = 22, weight = FontWeight.Black, tight = true)
        }
        Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = 260.dp)) {
            val label = today.shortLabel()
            Print(2016, label, R.drawable.welcome_2016_hills, 150.dp, -7f, Modifier.offset(8.dp, 36.dp))
            Print(2021, label, R.drawable.welcome_2021_sail, 150.dp, 6f, Modifier.align(Alignment.TopEnd).offset((-4).dp, 8.dp))
            Print(2025, label, R.drawable.welcome_2025_cake, 176.dp, -1.5f, Modifier.align(Alignment.TopCenter).offset(y = 118.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T(L.s("welcome_title"), size = 32, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
            T(L.s("welcome_body"), muted = true)
        }
        if (denied) {
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(p.accTint).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.WarningAmber, null, tint = p.accInk, modifier = Modifier.size(18.dp))
                T(L.s("welcome_denied"), size = 14, weight = FontWeight.Medium, color = p.accInk)
            }
        }
        // Google Photos leads on Android, as in the design.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(L.s("use_google_photos"), { google = true }, modifier = Modifier.fillMaxWidth())
            Pill(L.s("use_phone_photos"), askPhotos, kind = Kind.Secondary, modifier = Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, null, tint = p.muted, modifier = Modifier.size(13.dp))
                Text(
                    L.s("privacy_line"), color = p.muted, textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
                )
            }
        }
    }
    if (google) {
        GoogleSheet(
            connected = state.sources.google, picked = state.googlePhotos.size, busy = busy,
            onPick = startGoogle, onCancel = vm::cancelGoogleImport, onDisconnect = vm::disconnectGoogle,
            onDismiss = { google = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(vm: AppViewModel, bottomPadding: androidx.compose.ui.unit.Dp, scrollTop: Flow<Tab>, onSelectingChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val state by vm.saved.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val indexing by vm.indexing.collectAsStateWithLifecycle()
    val busy by vm.googleImport.collectAsStateWithLifecycle()
    // Start from the last result so the list keeps its place when this tab comes back.
    var result by remember { mutableStateOf(vm.lastDay) }
    var calendar by remember { mutableStateOf(false) }
    val demo by vm.demoScene.collectAsStateWithLifecycle()
    LaunchedEffect(demo) { if (demo == "calendar") calendar = true }
    var settings by remember { mutableStateOf(false) }
    var google by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(listOf<Photo>()) }
    var albumFor by remember { mutableStateOf<List<Photo>?>(null) }
    val list = rememberLazyListState()
    val topBar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    val index = vm.index(state)
    val startGoogle = rememberGoogleImport(vm)
    val askPhotos = rememberPhotoPermission(vm) { ok -> if (!ok) vm.say(L.s("toast_allow_access")) }
    val askNotify = rememberNotificationPermission { ok ->
        if (ok) vm.setReminder { it.copy(on = true) } else vm.say(L.s("toast_notifications_off"))
    }

    LaunchedEffect(day, index) { result = (day to vm.photosFor(day)).also { vm.lastDay = it } }
    LaunchedEffect(Unit) {
        scrollTop.collect {
            list.animateScrollToItem(0)
            topBar.state.heightOffset = 0f
        }
    }
    val groups = result?.takeIf { it.first == day }?.second

    fun stopSelecting() { selecting = false; picked = emptyList(); onSelectingChange(false) }
    fun go(md: MonthDay) {
        calendar = false
        stopSelecting()
        vm.day.value = md
        scope.launch { list.scrollToItem(0); topBar.state.heightOffset = 0f }
    }
    BackHandler(enabled = selecting) { stopSelecting() }

    val today = MonthDay.today()
    val total = groups?.sumOf { it.photos.size } ?: 0
    val summary = when {
        groups == null || indexing && groups.isEmpty() -> L.s("today_loading")
        groups.isNotEmpty() -> L.s("photos_from_years", "photos" to L.p("photos", total), "years" to L.p("years", groups.size))
        else -> L.s("today_none")
    }
    val view = LocalView.current
    val selection = Selection(selecting, picked.map { it.key }.toSet()) { ph ->
        Haptics.tick(view)
        picked = if (picked.any { it.key == ph.key }) picked.filterNot { it.key == ph.key } else picked + ph
    }
    val favorites = state.favorites.map { it.key }.toSet()

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize().nestedScroll(topBar.nestedScrollConnection)) {
            // The date is the title: large at rest, title size once the list scrolls.
            MaterialTheme(
                typography = MaterialTheme.typography.copy(
                    headlineMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = tightSpacing(32).sp),
                    titleLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
                ),
            ) {
                LargeTopAppBar(
                    title = {
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { calendar = true }.semantics { heading() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(day.label(), color = p.ink)
                            Icon(Icons.Filled.ArrowDropDown, L.s("pick_another_day"), tint = Accent)
                        }
                    },
                    actions = {
                        TextButton(onClick = { if (selecting) stopSelecting() else { selecting = true; onSelectingChange(true) } }) {
                            T(L.s(if (selecting) "cancel" else "select"), size = 15, weight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { calendar = true }) { Icon(Icons.Outlined.CalendarMonth, L.s("pick_a_day"), tint = p.ink) }
                        IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.MoreVert, L.s("options"), tint = p.ink) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg, scrolledContainerColor = p.bg.copy(alpha = 0.92f)),
                    scrollBehavior = topBar,
                )
            }
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = bottomPadding + 24.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "summary") {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        T(summary, size = 15, muted = true)
                        if (day != today) {
                            Row(Modifier.padding(top = 8.dp)) {
                                Pill(L.s("back_to_today"), { go(today) }, kind = Kind.Tint, height = 34.dp, icon = { Icon(Icons.AutoMirrored.Filled.Undo, null, tint = it, modifier = Modifier.size(14.dp)) })
                            }
                        }
                    }
                }
                when {
                    groups == null -> item { Skeleton() }
                    groups.isEmpty() -> item {
                        val noSources = !state.sources.device && !state.sources.google
                        EmptyDay(day, noSources, nearbyDays(index, day), onGo = ::go, onConnect = { settings = true })
                    }
                    else -> groups.forEach { g ->
                        val share = { sharePhotos(context, g.photos, fullDate(g.year, day)) }
                        val pinKey = "pin-${g.year}"
                        stickyHeader(key = pinKey) {
                            // Shown only while pinned to the top of the list; the big header covers this space otherwise.
                            val pinned by remember {
                                derivedStateOf {
                                    list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == pinKey }
                                        ?.let { it.offset <= list.layoutInfo.viewportStartOffset } == true
                                }
                            }
                            YearBar(g.year, g.photos.size, day, pinned, onShare = share)
                        }
                        item(key = "head-${g.year}") { YearHeader(g.year, g.photos.size, day, onShare = share) }
                        item(key = "photos-${g.year}") {
                            YearMosaic(
                                g.photos, favorites, selection,
                                onOpen = { i -> vm.viewer.value = com.njarrar.backthen.Viewer(g.photos, i) },
                            )
                        }
                    }
                }
            }
        }
        if (selecting) {
            SelectBar(
                picked.size,
                onShare = { sharePhotos(context, picked, L.p("photos", picked.size)) },
                onAlbum = { albumFor = picked },
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).padding(bottom = bottomPadding),
            )
        }
    }

    if (calendar) CalendarDialog(day, index, onPick = ::go, onDismiss = { calendar = false })
    if (settings) {
        SettingsSheet(
            state,
            onConnectDevice = { if (!state.sources.device) askPhotos() },
            onGoogle = { settings = false; google = true },
            onReminder = { on -> if (on) askNotify() else vm.setReminder { it.copy(on = false) } },
            onReminderTime = { h, m -> vm.setReminder { it.copy(hour = h, minute = m) } },
            onDismiss = { settings = false },
        )
    }
    if (google) {
        GoogleSheet(
            state.sources.google, state.googlePhotos.size, busy,
            onPick = startGoogle, onCancel = vm::cancelGoogleImport,
            onDisconnect = { vm.disconnectGoogle(); google = false },
            onDismiss = { google = false },
        )
    }
    albumFor?.let { photos ->
        AlbumSheet(
            photos, state.albums,
            onCreate = { name -> vm.createAlbum(name, photos); albumFor = null; stopSelecting() },
            onAdd = { a -> vm.addToAlbum(a.id, photos); albumFor = null; stopSelecting() },
            onDismiss = { albumFor = null },
        )
    }
}

@Composable
private fun Skeleton() {
    val p = LocalPalette.current
    Column(Modifier.padding(16.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(128.dp, 48.dp).clip(RoundedCornerShape(10.dp)).background(p.fill2))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(76.dp, 28.dp).clip(RoundedCornerShape(8.dp)).background(p.fill))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(2f).height(228.dp).clip(RoundedCornerShape(10.dp)).background(p.fill2))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(10.dp)).background(p.fill))
                Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(10.dp)).background(p.fill2))
            }
        }
    }
}

@Composable
private fun EmptyDay(day: MonthDay, noSources: Boolean, near: Pair<MonthDay?, MonthDay?>, onGo: (MonthDay) -> Unit, onConnect: () -> Unit) {
    val p = LocalPalette.current
    val (before, after) = near
    Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 64.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(p.fill), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.HideImage, null, tint = p.muted, modifier = Modifier.size(28.dp))
        }
        T(if (noSources) L.s("empty_no_sources_title") else L.s("empty_title", "day" to day.label()), size = 24, weight = FontWeight.Bold, tight = true)
        T(
            when {
                noSources -> L.s("empty_no_sources_android")
                before != null || after != null -> L.s("empty_nearby")
                else -> L.s("empty_none")
            },
            size = 15, muted = true, modifier = Modifier.widthIn(max = 300.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (noSources) Pill(L.s("connect_photos"), onConnect, height = 40.dp)
            before?.let { md -> Pill(md.shortLabel(), { onGo(md) }, kind = Kind.Secondary, height = 40.dp, icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = it, modifier = Modifier.size(15.dp)) }) }
            after?.let { md -> Pill(md.shortLabel(), { onGo(md) }, kind = Kind.Secondary, height = 40.dp, icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = it, modifier = Modifier.size(15.dp)) }) }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            T(title, size = 34, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
            T(subtitle, size = 15, muted = true)
        }
        trailing?.invoke()
    }
}

@Composable
private fun EmptyNote(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    val p = LocalPalette.current
    Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(p.accTint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(28.dp))
        }
        T(title, size = 22, weight = FontWeight.Bold, tight = true)
        T(body, size = 15, muted = true, modifier = Modifier.widthIn(max = 300.dp))
    }
}

@Composable
fun FavoritesScreen(vm: AppViewModel, bottomPadding: androidx.compose.ui.unit.Dp, scrollTop: Flow<Tab>) {
    val state by vm.saved.collectAsStateWithLifecycle()
    val favs = state.favorites
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { scrollTop.collect { list.animateScrollToItem(0) } }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().background(LocalPalette.current.bg).statusBarsPadding(), contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
        item {
            val years = favs.map { localDate(it.time).year }.toSet().size
            Header(L.s("tab_favorites"), if (favs.isEmpty()) L.s("favorites_none") else L.s("photos_from_years", "photos" to L.p("photos", favs.size), "years" to L.p("years", years)))
        }
        item {
            if (favs.isEmpty()) EmptyNote(Icons.Outlined.FavoriteBorder, L.s("favorites_empty_title"), L.s("favorites_empty_body"))
            else SquareGrid(favs, emptySet(), showYear = true) { i -> vm.viewer.value = com.njarrar.backthen.Viewer(favs, i) }
        }
    }
}

@Composable
fun AlbumsScreen(vm: AppViewModel, bottomPadding: androidx.compose.ui.unit.Dp, scrollTop: Flow<Tab>, onOpen: (Album) -> Unit, onNew: () -> Unit) {
    val p = LocalPalette.current
    val state by vm.saved.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { scrollTop.collect { list.animateScrollToItem(0) } }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().background(p.bg).statusBarsPadding(), contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
        item {
            Header(L.s("tab_albums"), L.p("albums", state.albums.size)) {
                Pill(L.s("new_album"), onNew, height = 40.dp, icon = { Icon(Icons.Filled.Add, null, tint = it, modifier = Modifier.size(16.dp)) })
            }
            if (state.albums.isEmpty()) EmptyNote(Icons.Outlined.PhotoLibrary, L.s("albums_empty_title"), L.s("albums_empty_body"))
        }
        items(state.albums, key = { it.id }) { a ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { onOpen(a) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(p.fill), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    a.photos.take(4).chunked(2).forEach { row ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            row.forEach { ph -> AsyncImage(ph.uri, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize()) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    T(a.name, size = 17, weight = FontWeight.SemiBold, maxLines = 1)
                    T(L.s("album_made", "photos" to L.p("photos", a.photos.size), "date" to formatDate(localDate(a.createdAt), "MMMd")), size = 14, muted = true)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(vm: AppViewModel, album: Album, bottomPadding: androidx.compose.ui.unit.Dp, scrollTop: Flow<Tab>, onBack: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val state by vm.saved.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { scrollTop.collect { list.animateScrollToItem(0) } }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(p.bg)) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L.s("tab_albums"), tint = p.ink) }
            },
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, L.s("more"), tint = p.ink) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = p.surface) {
                        DropdownMenuItem(text = { T(L.s("rename")) }, onClick = { menu = false; renaming = true })
                        HorizontalDivider(color = p.line)
                        DropdownMenuItem(
                            text = { T(L.s("delete_album"), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg, scrolledContainerColor = p.bg),
        )
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)) {
                    T(album.name, size = 30, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                    T(L.s("album_made", "photos" to L.p("photos", album.photos.size), "date" to formatDate(localDate(album.createdAt), "MMMd")), size = 15, muted = true)
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(
                        L.s("share_album"), { sharePhotos(context, album.photos, album.name) }, height = 46.dp, modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Outlined.IosShare, null, tint = it, modifier = Modifier.size(18.dp)) },
                    )
                    Pill(
                        L.s("save_to_gallery"), { vm.saveAlbumToGallery(album) }, kind = Kind.Secondary, height = 46.dp, modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Outlined.Download, null, tint = it, modifier = Modifier.size(18.dp)) },
                    )
                }
                SquareGrid(album.photos, state.favorites.map { it.key }.toSet(), showYear = false) { i -> vm.viewer.value = com.njarrar.backthen.Viewer(album.photos, i) }
            }
        }
    }
    if (renaming) {
        var name by remember { mutableStateOf(album.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = p.surface,
            title = { T(L.s("rename_album"), size = 20, weight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = p.ink),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.ink, unfocusedBorderColor = p.line, cursorColor = p.ink),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.renameAlbum(album.id, name); renaming = false }, enabled = name.isNotBlank()) { T(L.s("save"), weight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { T(L.s("cancel"), weight = FontWeight.SemiBold) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = p.surface,
            title = { T(L.s("delete_album_confirm", "name" to album.name), size = 20, weight = FontWeight.Bold) },
            text = { T(L.s("delete_album_note"), muted = true) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onBack(); vm.deleteAlbum(album.id) }) {
                    T(L.s("delete_album"), weight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { T(L.s("cancel"), weight = FontWeight.SemiBold) } },
        )
    }
}
