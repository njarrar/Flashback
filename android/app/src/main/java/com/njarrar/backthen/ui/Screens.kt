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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.LayoutDirection
import com.njarrar.backthen.core.swipeTarget
import kotlin.math.abs
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import com.njarrar.backthen.data.CAN_SAVE_TO_GALLERY
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
    var denied by remember { mutableStateOf(false) }
    val demo by vm.demoScene.collectAsStateWithLifecycle()
    var chosePhone by remember(demo) { mutableStateOf(demo == "reminder") }
    val today = MonthDay.today()
    val askPhotos = rememberPhotoPermission(vm) { ok -> denied = !ok; chosePhone = ok }
    val time = timeLabel(state.reminder.hour, state.reminder.minute)
    val askNotify = rememberNotificationPermission { ok ->
        vm.finishOnboarding(ok)
        if (ok) vm.say(L.s("toast_reminder_set", "time" to time))
    }

    if (chosePhone) {
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
                            T(time, size = 14, muted = true)
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(L.s("show_my_photos"), askPhotos, modifier = Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, null, tint = p.muted2, modifier = Modifier.size(13.dp))
                Text(
                    L.s("privacy_line"), color = p.muted2, textAlign = TextAlign.Center,
                    style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
                )
            }
        }
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
    // Start from the last result so the list keeps its place when this tab comes back.
    var result by remember { mutableStateOf(vm.lastDay) }
    var calendar by remember { mutableStateOf(false) }
    val demo by vm.demoScene.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(listOf<Photo>()) }
    var albumFor by remember { mutableStateOf<List<Photo>?>(null) }
    var shareFor by remember { mutableStateOf<ShareRequest?>(null) }
    var stripPick by remember(day) { mutableStateOf<Int?>(null) }
    val list = rememberLazyListState()
    val topBar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    val index = vm.index(state)
    val askPhotos = rememberPhotoPermission(vm) { ok -> if (!ok) vm.say(L.s("toast_allow_access")) }
    val askNotify = rememberNotificationPermission { ok ->
        if (ok) vm.setReminder { it.copy(on = true) } else vm.say(L.s("toast_notifications_off"))
    }
    val placeCard by vm.placeCard.collectAsStateWithLifecycle()
    var placeOpen by remember { mutableStateOf<com.njarrar.backthen.core.City?>(null) }
    var placeAsk by remember { mutableStateOf(false) }
    val askPlaces = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.setPlaces(true) }
    // Ask about places once, a moment after the first photos show, never during screenshots.
    LaunchedEffect(index.isEmpty(), state.places.asked, demo) {
        if (index.isEmpty() || state.places.asked || demo != null || vm.places.cities == null) return@LaunchedEffect
        delay(1500)
        placeAsk = true
    }

    LaunchedEffect(day, index) { result = (day to vm.photosFor(day)).also { vm.lastDay = it } }
    LaunchedEffect(Unit) {
        scrollTop.collect {
            list.animateScrollToItem(0)
            topBar.state.heightOffset = 0f
        }
    }
    val groups = result?.takeIf { it.first == day }?.second
    LaunchedEffect(demo, groups) {
        val first = groups?.firstOrNull() ?: return@LaunchedEffect
        when (demo) {
            "calendar" -> calendar = true
            "settings" -> settings = true
            "select" -> { selecting = true; picked = first.photos.take(3); onSelectingChange(true) }
            "share" -> shareFor = ShareRequest(fullDate(first.year, day), first.photos)
            "addalbum" -> albumFor = first.photos.take(3)
        }
    }

    fun stopSelecting() { selecting = false; picked = emptyList(); onSelectingChange(false) }
    fun go(md: MonthDay) {
        calendar = false
        stopSelecting()
        vm.day.value = md
        scope.launch { list.scrollToItem(0); topBar.state.heightOffset = 0f }
    }
    BackHandler(enabled = selecting) { stopSelecting() }

    val today = MonthDay.today()
    val hidden = day.key in state.hiddenDates
    val total = groups?.sumOf { it.photos.size } ?: 0
    val summary = when {
        hidden -> L.s("you_hid_date")
        groups == null || indexing && groups.isEmpty() -> L.s("today_loading")
        groups.isNotEmpty() -> L.s("photos_from_years", "photos" to L.p("photos", total), "years" to L.p("years", groups.size))
        else -> L.s("today_none")
    }
    val view = LocalView.current
    val pickedKeys = remember(picked) { picked.map { it.key }.toSet() }
    val selection = remember(selecting, pickedKeys, view) {
        Selection(
            selecting, pickedKeys,
            start = { ph ->
                Haptics.longPress(view)
                selecting = true
                picked = listOf(ph)
                onSelectingChange(true)
            },
        ) { ph ->
            Haptics.tick(view)
            picked = if (picked.any { it.key == ph.key }) picked.filterNot { it.key == ph.key } else picked + ph
        }
    }
    val favorites = remember(state.favorites) { state.favorites.map { it.key }.toSet() }

    // Sideways drags move between days with photos: toward the left for the next day, mirrored
    // right to left. Up and down stays a scroll, and the year pills keep their own sideways scroll.
    val swipeX = remember { Animatable(0f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val neighbors = remember(day, index) { swipeTarget(index, day, -1) to swipeTarget(index, day, 1) }
    fun target(dx: Float) = if ((dx < 0) != rtl) neighbors.second else neighbors.first
    fun land(t: Pair<MonthDay, Int>) {
        Haptics.tick(view)
        go(t.first)
        if (t.second > 0) vm.say(L.p("skipped_days", t.second))
    }

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
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { calendar = true }.semantics {
                                heading()
                                customActions = listOfNotNull(
                                    neighbors.first?.let { t -> CustomAccessibilityAction(L.s("previous_day_photos")) { land(t); true } },
                                    neighbors.second?.let { t -> CustomAccessibilityAction(L.s("next_day_photos")) { land(t); true } },
                                )
                            },
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
                        // The date title opens the calendar, so the bar keeps only Select and this menu.
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, L.s("options"), tint = p.ink) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = p.surface) {
                                DropdownMenuItem(
                                    text = { T(if (hidden) L.s("show_day_again", "day" to day.label()) else L.s("hide_date")) },
                                    leadingIcon = { Icon(if (hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null, tint = p.ink) },
                                    onClick = { menu = false; vm.setHidden(day, !hidden) },
                                )
                                HorizontalDivider(color = p.line)
                                listOf(YearLayout.MOSAIC to "layout_all_years", YearLayout.STRIP to "layout_one_year", YearLayout.CARDS to "layout_cards").forEach { (layout, key) ->
                                    val on = state.layout == layout
                                    DropdownMenuItem(
                                        text = { T(L.s(key)) },
                                        leadingIcon = { Icon(Icons.Filled.Check, null, tint = if (on) p.ink else Color.Transparent) },
                                        onClick = { menu = false; vm.setLayout(layout) },
                                        modifier = Modifier.semantics { selected = on },
                                    )
                                }
                                HorizontalDivider(color = p.line)
                                DropdownMenuItem(
                                    text = { T(L.s("settings")) },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, null, tint = p.ink) },
                                    onClick = { menu = false; settings = true },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg, scrolledContainerColor = p.bg.copy(alpha = 0.92f)),
                    scrollBehavior = topBar,
                )
            }
            PullToRefreshBox(
                isRefreshing = indexing,
                onRefresh = { vm.refreshIndex(force = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = list,
                    contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(selecting, neighbors, rtl) {
                            if (selecting) return@pointerInput
                            var dx = 0f
                            val tracker = VelocityTracker()
                            detectHorizontalDragGestures(
                                onDragStart = { dx = 0f; tracker.resetTracking() },
                                onDragCancel = { scope.launch { swipeX.animateTo(0f) } },
                                onDragEnd = {
                                    val v = tracker.calculateVelocity().x
                                    val t = target(dx)
                                    val far = abs(dx) > 80.dp.toPx() || (abs(v) > 1200f && (v < 0) == (dx < 0))
                                    scope.launch {
                                        if (t != null && far) {
                                            // Slide the day out, swap it, and slide the new one in from the other side.
                                            val out = if (dx < 0) -size.width.toFloat() else size.width.toFloat()
                                            swipeX.animateTo(out, tween(150))
                                            land(t)
                                            swipeX.snapTo(-out)
                                            swipeX.animateTo(0f, tween(220))
                                        } else {
                                            swipeX.animateTo(0f, spring())
                                        }
                                    }
                                },
                            ) { change, amount ->
                                change.consume()
                                dx += amount
                                tracker.addPosition(change.uptimeMillis, change.position)
                                // Gives way slowly when there is no day that way.
                                scope.launch { swipeX.snapTo(if (target(dx) == null) dx / 4 else dx) }
                            }
                        }
                        .graphicsLayer {
                            translationX = swipeX.value
                            alpha = 1f - (abs(swipeX.value) / size.width).coerceIn(0f, 1f) * 0.5f
                        },
                ) {
                    item(key = "summary") {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                            T(summary, size = 15, muted = true)
                            if (day != today) {
                                Row(Modifier.padding(top = 8.dp)) {
                                    Pill(L.s("back_to_today"), { go(today) }, kind = Kind.Tint, height = 44.dp, icon = { Icon(Icons.AutoMirrored.Filled.Undo, null, tint = it, modifier = Modifier.size(14.dp)) })
                                }
                            }
                        }
                    }
                    placeCard?.takeIf { !selecting }?.let { card ->
                        item(key = "place") {
                            PlaceCardView(vm, card, onOpen = { placeOpen = card.city }, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp))
                        }
                    }
                    when {
                        hidden -> item { HiddenDay(day) { vm.setHidden(day, false) } }
                        groups == null -> item { Skeleton() }
                        groups.isEmpty() -> item {
                            val noSources = !state.sources.device
                            EmptyDay(day, noSources, nearbyDays(index, day), onGo = ::go, onConnect = { settings = true })
                        }
                        state.layout == YearLayout.CARDS -> item(key = "cards-${day.key}") {
                            YearCards(
                                groups, day, favorites, selection,
                                onOpen = { photos, i -> vm.viewer.value = com.njarrar.backthen.Viewer(photos, i) },
                                onShare = { year, photos -> shareFor = ShareRequest(fullDate(year, day), photos) },
                            )
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
                                    onShare = { shareFor = ShareRequest(fullDate(g.year, day), g.photos) },
                                    onPick = { stripPick = it },
                                )
                            }
                        }
                        else -> groups.forEach { g ->
                            val share = { shareFor = ShareRequest(fullDate(g.year, day), g.photos) }
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
        }
        if (selecting) {
            SelectBar(
                picked.size,
                onShare = { shareFor = ShareRequest(L.p("photos", picked.size), picked) },
                onAlbum = { albumFor = picked },
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).padding(bottom = bottomPadding),
            )
        }
        placeOpen?.let { city -> PlaceScreen(vm, city, state.placeIndex, bottomPadding, onBack = { placeOpen = null }) }
    }
    if (placeAsk) {
        PlaceAskSheet(
            onTurnOn = { placeAsk = false; askPlaces.launch(vm.places.permissions) },
            onNotNow = { placeAsk = false; vm.placesAsked() },
        )
    }

    if (calendar) CalendarSheet(day, index, cover = vm::coverFor, onPick = ::go, onDismiss = { calendar = false })
    if (settings) {
        SettingsSheet(
            state, day,
            onConnectDevice = { if (!state.sources.device) askPhotos() },
            onReminder = { on -> if (on) askNotify() else vm.setReminder { it.copy(on = false) } },
            onReminderTime = { h, m -> vm.setReminder { it.copy(hour = h, minute = m) } },
            onLayout = vm::setLayout,
            onHidden = { md, on -> vm.setHidden(md, on); if (md == day) settings = false },
            onDismiss = { settings = false },
            onPlaces = if (vm.places.cities == null) null else { on -> if (on) askPlaces.launch(vm.places.permissions) else vm.setPlaces(false) },
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
        T(L.s("day_is_hidden", "day" to day.label()), size = 24, weight = FontWeight.Bold, tight = true)
        T(L.s("hidden_body"), size = 15, muted = true, modifier = Modifier.widthIn(max = 300.dp))
        Row { Pill(L.s("show_it_again"), onShow, height = 40.dp) }
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
            val years = remember(favs) { favs.map { localDate(it.time).year }.toSet().size }
            Header(L.s("tab_favorites"), if (favs.isEmpty()) L.s("favorites_none") else L.s("photos_from_years", "photos" to L.p("photos", favs.size), "years" to L.p("years", years)))
        }
        if (favs.isEmpty()) {
            item { EmptyNote(Icons.Outlined.FavoriteBorder, L.s("favorites_empty_title"), L.s("favorites_empty_body")) }
        } else {
            squareGridItems(favs, emptySet(), showYear = true) { i -> vm.viewer.value = com.njarrar.backthen.Viewer(favs, i) }
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
    var sharing by remember { mutableStateOf(false) }
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
        val favorites = remember(state.favorites) { state.favorites.map { it.key }.toSet() }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding + 24.dp)) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)) {
                    T(album.name, size = 30, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                    T(L.s("album_made", "photos" to L.p("photos", album.photos.size), "date" to formatDate(localDate(album.createdAt), "MMMd")), size = 15, muted = true)
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(
                        L.s("share_album"), { sharing = true }, height = 46.dp, enabled = album.photos.isNotEmpty(), modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Outlined.IosShare, null, tint = it, modifier = Modifier.size(18.dp)) },
                    )
                    if (CAN_SAVE_TO_GALLERY) {
                        Pill(
                            L.s("save_to_gallery"), { vm.saveToGallery(album.name, album.photos) }, kind = Kind.Secondary, height = 46.dp, enabled = album.photos.isNotEmpty(), modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Outlined.Download, null, tint = it, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            squareGridItems(album.photos, favorites, showYear = false) { i -> vm.viewer.value = com.njarrar.backthen.Viewer(album.photos, i) }
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
