package com.njarrar.backthen.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.njarrar.backthen.AppViewModel
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.MONTHS_SHORT
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.YearGroup
import com.njarrar.backthen.core.label
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.nearbyDays
import com.njarrar.backthen.core.plural
import com.njarrar.backthen.core.shortLabel
import com.njarrar.backthen.core.yearsForDay
import com.njarrar.backthen.data.YearLayout
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
    val time = timeLabel(state.reminder.hour, state.reminder.minute)
    val askNotify = rememberNotificationPermission { ok ->
        vm.finishOnboarding(ok)
        if (ok) vm.say("Daily reminder set for $time")
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
                            T("Back Then", size = 14, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            T(time, size = 14, muted = true)
                        }
                        T("${today.shortLabel()} has photos from ${if (years.isEmpty()) "past years" else plural(years.size, "year")}", size = 14, weight = FontWeight.SemiBold)
                        T((if (years.isEmpty()) "" else "The oldest is from ${years.last()}. ") + "Tap to look back.", size = 14, muted = true)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("One reminder a day", size = 32, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                    T("Only on days that have photos from past years. You can change the time later.", muted = true)
                }
            }
            Pill("Turn on reminder", askNotify, modifier = Modifier.fillMaxWidth())
            Pill("Not now", { vm.finishOnboarding(false) }, kind = Kind.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth())
        }
        return
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Logo(36.dp)
            T("Back Then", size = 22, weight = FontWeight.Black, tight = true)
        }
        Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = 260.dp)) {
            val label = today.shortLabel()
            Print(2016, label, Color(0xFFFFC93C), 150.dp, -7f, Modifier.offset(8.dp, 36.dp))
            Print(2021, label, Color(0xFF2E7BFF), 150.dp, 6f, Modifier.align(Alignment.TopEnd).offset((-4).dp, 8.dp))
            Print(2025, label, Color(0xFFFF4B3E), 176.dp, -1.5f, Modifier.align(Alignment.TopCenter).offset(y = 118.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("See what this day looked like, every year.", size = 32, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
            T("Each morning, Back Then finds the photos you took on today's date in past years and sorts them by year.", muted = true)
        }
        if (denied) {
            Row(Modifier.clip(RoundedCornerShape(14.dp)).background(p.accTint).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.WarningAmber, null, tint = p.accInk, modifier = Modifier.size(18.dp))
                T("Without photo access there is nothing to show. You can allow it later in Settings.", size = 14, weight = FontWeight.Medium, color = p.accInk)
            }
        }
        // Google Photos leads on Android, as in the design.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Use Google Photos", { google = true }, modifier = Modifier.fillMaxWidth())
            Pill("Use photos on this phone", askPhotos, kind = Kind.Secondary, modifier = Modifier.fillMaxWidth())
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(vm: AppViewModel, bottomPadding: androidx.compose.ui.unit.Dp, onSelectingChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val state by vm.saved.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val indexing by vm.indexing.collectAsStateWithLifecycle()
    val busy by vm.googleImport.collectAsStateWithLifecycle()
    var result by remember { mutableStateOf<Pair<MonthDay, List<YearGroup>>?>(null) }
    var calendar by remember { mutableStateOf(false) }
    val demo by vm.demoScene.collectAsStateWithLifecycle()
    LaunchedEffect(demo) { if (demo == "calendar") calendar = true }
    var settings by remember { mutableStateOf(false) }
    var google by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(listOf<Photo>()) }
    var albumFor by remember { mutableStateOf<List<Photo>?>(null) }
    var shareFor by remember { mutableStateOf<ShareRequest?>(null) }
    var stripPick by remember(day) { mutableStateOf<Int?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val index = vm.index(state)
    val startGoogle = rememberGoogleImport(vm)
    val askPhotos = rememberPhotoPermission(vm) { ok -> if (!ok) vm.say("Allow photo access in Settings") }
    val askNotify = rememberNotificationPermission { ok ->
        if (ok) vm.setReminder { it.copy(on = true) } else vm.say("Notifications are off in Settings")
    }

    LaunchedEffect(day, index) { result = day to vm.photosFor(day) }
    val groups = result?.takeIf { it.first == day }?.second

    fun stopSelecting() { selecting = false; picked = emptyList(); onSelectingChange(false) }
    fun go(md: MonthDay) {
        calendar = false
        stopSelecting()
        vm.day.value = md
        scope.launch { list.scrollToItem(0) }
    }
    BackHandler(enabled = selecting) { stopSelecting() }

    val today = MonthDay.today()
    val hidden = day.key in state.hiddenDates
    val total = groups?.sumOf { it.photos.size } ?: 0
    val summary = when {
        hidden -> "You hid this date"
        groups == null || indexing && groups.isEmpty() -> "Looking through your library…"
        groups.isNotEmpty() -> "${plural(total, "photo")} from ${plural(groups.size, "year")}"
        else -> "No photos on this date"
    }
    val selection = Selection(selecting, picked.map { it.key }.toSet()) { ph ->
        picked = if (picked.any { it.key == ph.key }) picked.filterNot { it.key == ph.key } else picked + ph
    }
    val favorites = state.favorites.map { it.key }.toSet()

    Box(Modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = bottomPadding + 24.dp), modifier = Modifier.statusBarsPadding()) {
            item {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("Back Then", size = 20, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (selecting) stopSelecting() else { selecting = true; onSelectingChange(true) } }) {
                        T(if (selecting) "Cancel" else "Select", size = 15, weight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { calendar = true }) { Icon(Icons.Outlined.CalendarMonth, "Pick a day", tint = p.ink) }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.MoreVert, "Options", tint = p.ink) }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { calendar = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(day.label(), size = 32, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                        Icon(Icons.Filled.ArrowDropDown, "Pick another day", tint = Accent)
                    }
                    T(summary, size = 15, muted = true)
                }
                if (day != today) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp)) {
                        Pill("Back to today", { go(today) }, kind = Kind.Tint, height = 34.dp, icon = { Icon(Icons.AutoMirrored.Filled.Undo, null, tint = it, modifier = Modifier.size(14.dp)) })
                    }
                }
            }
            when {
                hidden -> item { HiddenDay(day) { vm.setHidden(day, false) } }
                groups == null -> item { Skeleton() }
                groups.isEmpty() -> item {
                    val noSources = !state.sources.device && !state.sources.google
                    EmptyDay(day, noSources, nearbyDays(index, day), onGo = ::go, onConnect = { settings = true })
                }
                state.layout == YearLayout.STRIP -> {
                    val years = groups.map { it.year }
                    val at = stripPick?.let { years.indexOf(it) }?.takeIf { it >= 0 } ?: 0
                    val g = groups[at]
                    stickyHeader(key = "years") { YearPills(years, g.year) { stripPick = it } }
                    item(key = g.year) {
                        StripYear(
                            g.year, g.photos, day, favorites, selection,
                            newer = years.getOrNull(at - 1), older = years.getOrNull(at + 1),
                            onOpen = { i -> vm.viewer.value = com.njarrar.backthen.Viewer(g.photos, i) },
                            onShare = { shareFor = ShareRequest("${day.label()}, ${g.year}", g.photos) },
                            onPick = { stripPick = it },
                        )
                    }
                }
                else -> items(groups, key = { it.year }) { g ->
                    YearSection(
                        g.year, g.photos, day, favorites, selection,
                        onOpen = { i -> vm.viewer.value = com.njarrar.backthen.Viewer(g.photos, i) },
                        onShare = { shareFor = ShareRequest("${day.label()}, ${g.year}", g.photos) },
                    )
                }
            }
        }
        if (selecting) {
            SelectBar(
                picked.size,
                onShare = { shareFor = ShareRequest(plural(picked.size, "photo"), picked) },
                onAlbum = { albumFor = picked },
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).padding(bottom = bottomPadding),
            )
        }
    }

    if (calendar) CalendarDialog(day, index, onPick = ::go, onDismiss = { calendar = false })
    if (settings) {
        SettingsSheet(
            state, day,
            onConnectDevice = { if (!state.sources.device) askPhotos() },
            onGoogle = { settings = false; google = true },
            onReminder = { on -> if (on) askNotify() else vm.setReminder { it.copy(on = false) } },
            onReminderTime = { h, m -> vm.setReminder { it.copy(hour = h, minute = m) } },
            onLayout = vm::setLayout,
            onHidden = { md, on -> vm.setHidden(md, on); if (md == day) settings = false },
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
    shareFor?.let { req ->
        ShareSheet(
            req,
            onSend = { sharePhotos(context, req.photos, req.title); shareFor = null },
            onSave = { vm.saveToGallery(req.title, req.photos); shareFor = null },
            onDismiss = { shareFor = null },
        )
    }
}

/** Shown instead of photos on a day the user hid. */
@Composable
private fun HiddenDay(day: MonthDay, onShow: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 64.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(p.fill), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.VisibilityOff, null, tint = p.muted, modifier = Modifier.size(28.dp))
        }
        T("${day.label()} is hidden", size = 24, weight = FontWeight.Bold, tight = true)
        T("It won't come up on its own or in reminders.", size = 15, muted = true, modifier = Modifier.widthIn(max = 300.dp))
        Row { Pill("Show it again", onShow, height = 40.dp) }
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
        T(if (noSources) "No photos connected" else "Nothing from ${day.label()} yet", size = 24, weight = FontWeight.Bold, tight = true)
        T(
            when {
                noSources -> "Connect your phone's photos or Google Photos to see this day in past years."
                before != null || after != null -> "You didn't take photos on this date in past years. These nearby days have some."
                else -> "You didn't take photos on this date in past years."
            },
            size = 15, muted = true, modifier = Modifier.widthIn(max = 300.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (noSources) Pill("Connect photos", onConnect, height = 40.dp)
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
fun FavoritesScreen(vm: AppViewModel, bottomPadding: androidx.compose.ui.unit.Dp) {
    val state by vm.saved.collectAsStateWithLifecycle()
    val favs = state.favorites
    LazyColumn(Modifier.fillMaxSize().background(LocalPalette.current.bg).statusBarsPadding(), contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
        item { Header("Favorites", if (favs.isEmpty()) "Nothing saved yet" else plural(favs.size, "photo")) }
        item {
            if (favs.isEmpty()) EmptyNote(Icons.Outlined.FavoriteBorder, "No favorites yet", "Open any photo and tap the heart. It will be kept here, whatever day it's from.")
            else SquareGrid(favs, emptySet(), showYear = true) { i -> vm.viewer.value = com.njarrar.backthen.Viewer(favs, i) }
        }
    }
}

@Composable
fun AlbumsScreen(vm: AppViewModel, bottomPadding: androidx.compose.ui.unit.Dp, onOpen: (Album) -> Unit, onNew: () -> Unit) {
    val p = LocalPalette.current
    val state by vm.saved.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().background(p.bg).statusBarsPadding(), contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
        item {
            Header("Albums", plural(state.albums.size, "album")) { Pill("+ New", onNew, height = 40.dp) }
            if (state.albums.isEmpty()) EmptyNote(Icons.Outlined.PhotoLibrary, "No albums yet", "On Today, tap Select, pick a few photos, then tap Album.")
        }
        items(state.albums, key = { it.id }) { a ->
            val made = localDate(a.createdAt)
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
                    T("${plural(a.photos.size, "photo")}, made ${MONTHS_SHORT[made.monthValue - 1]} ${made.dayOfMonth}", size = 14, muted = true)
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(vm: AppViewModel, album: Album, bottomPadding: androidx.compose.ui.unit.Dp, onBack: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val made = localDate(album.createdAt)
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().background(p.bg).statusBarsPadding(), contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Albums", tint = p.ink) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.saveToGallery(album.name, album.photos) }) { Icon(Icons.Outlined.Download, "Save to gallery", tint = p.ink) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete album", tint = p.ink) }
                Pill("Share album", { sharing = true }, kind = Kind.Accent, height = 40.dp, modifier = Modifier.padding(end = 12.dp))
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
                T(album.name, size = 30, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                T("${plural(album.photos.size, "photo")}, made ${MONTHS_SHORT[made.monthValue - 1]} ${made.dayOfMonth}", size = 15, muted = true)
            }
            SquareGrid(album.photos, emptySet(), showYear = false) { i -> vm.viewer.value = com.njarrar.backthen.Viewer(album.photos, i) }
        }
    }
    if (sharing) {
        ShareSheet(
            ShareRequest(album.name, album.photos),
            onSend = { sharePhotos(context, album.photos, album.name); sharing = false },
            onSave = { vm.saveToGallery(album.name, album.photos); sharing = false },
            onDismiss = { sharing = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { T("Delete \"${album.name}\"?", size = 20, weight = FontWeight.Bold) },
            text = { T("The photos stay in your library.", muted = true) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onBack(); vm.deleteAlbum(album.id) }) { T("Delete", weight = FontWeight.SemiBold, color = Accent) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { T("Cancel", weight = FontWeight.SemiBold) } },
        )
    }
}
