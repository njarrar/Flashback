package com.njarrar.backthen.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.njarrar.backthen.AppViewModel
import com.njarrar.backthen.R
import com.njarrar.backthen.ToastKind
import com.njarrar.backthen.Viewer
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.City
import com.njarrar.backthen.core.CityList
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.YearGroup
import com.njarrar.backthen.core.formatDate
import com.njarrar.backthen.core.fullDate
import com.njarrar.backthen.core.homeCity
import com.njarrar.backthen.core.label
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.masonryColumns
import com.njarrar.backthen.core.nearbyDays
import com.njarrar.backthen.core.shortLabel
import com.njarrar.backthen.core.swipeTarget
import com.njarrar.backthen.core.tileAspect
import com.njarrar.backthen.core.yearsForDay
import com.njarrar.backthen.data.CAN_SAVE_TO_GALLERY
import com.njarrar.backthen.data.YearLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ONBOARDING_TIMES = listOf(8 to 0, 9 to 0, 12 to 0, 18 to 0, 20 to 0)

internal fun openAppSettings(context: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

internal fun openNotificationSettings(context: Context) {
    val opened = runCatching {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
    if (!opened) openAppSettings(context)
}

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val state by vm.saved.collectAsStateWithLifecycle()
    val demo by vm.demoScene.collectAsStateWithLifecycle()
    var denied by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var chosePhone by rememberSaveable { mutableStateOf(demo == "reminder") }
    LaunchedEffect(demo) { if (demo == "reminder") chosePhone = true }

    // If the user enabled photo access in system Settings and came back, advance automatically.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, chosePhone) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !chosePhone && vm.library.hasAccess()) {
                vm.connectDevice()
                denied = false
                permanentlyDenied = false
                chosePhone = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val today = MonthDay.today()
    val askPhotos = rememberPhotoPermission(vm) { ok ->
        denied = !ok
        if (!ok) {
            val canAskAgain = activity != null && vm.library.permissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
            permanentlyDenied = !canAskAgain
        } else {
            permanentlyDenied = false
        }
        chosePhone = ok
    }
    val time = timeLabel(state.reminder.hour, state.reminder.minute)
    val askNotify = rememberNotificationPermission { ok ->
        vm.finishOnboarding(ok)
        if (ok) vm.showToast(L.s("toast_reminder_set", "time" to time), ToastKind.Success)
    }

    if (chosePhone) {
        // Step two: the reminder with step progress & interactive reminder time chips.
        val years = yearsForDay(vm.index(state), today)
        val previewTitle = if (years.isEmpty()) L.s("reminder_title_any", "day" to today.shortLabel())
        else L.s("reminder_title_years", "day" to today.shortLabel(), "years" to L.p("years", years.size))
        val previewBody = if (years.isEmpty()) L.s("reminder_body") else L.s("reminder_body_oldest", "year" to years.last())
        Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding().padding(24.dp)) {
            StepIndicator(step = 2, total = 2)
            BoxWithConstraints(Modifier.weight(1f)) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(p.surface)
                            .border(1.dp, p.line, RoundedCornerShape(22.dp))
                            .padding(14.dp)
                            .clearAndSetSemantics {
                                contentDescription = "${L.s("daily_reminder")}, $time, $previewTitle. $previewBody"
                            },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Logo(40.dp)
                        Column(Modifier.weight(1f)) {
                            Row {
                                T(L.s("app_name"), size = 14, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                T(time, size = 14, muted = true)
                            }
                            T(previewTitle, size = 14, weight = FontWeight.SemiBold)
                            T(previewBody, size = 14, muted = true)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        T(L.s("reminder_onboarding_title"), size = 32, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                        T(L.s("reminder_onboarding_body"), muted = true)
                    }
                    // Interactive reminder time chips so users can choose their preferred hour upfront.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ONBOARDING_TIMES.forEach { (h, m) ->
                            val selectedTime = state.reminder.hour == h && state.reminder.minute == m
                            Box(
                                Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(50))
                                    .clickable(role = Role.RadioButton) { vm.setReminder { it.copy(hour = h, minute = m) } }
                                    .semantics { selected = selectedTime },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (selectedTime) p.ink else p.surface)
                                        .border(1.dp, if (selectedTime) p.ink else p.line, RoundedCornerShape(50))
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    T(timeLabel(h, m), size = 14, weight = FontWeight.SemiBold, color = if (selectedTime) p.bg else p.ink)
                                }
                            }
                        }
                    }
                }
            }
            Pill(L.s("turn_on_reminder"), askNotify, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            Pill(L.s("not_now"), { vm.finishOnboarding(false) }, kind = Kind.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth())
        }
        return
    }

    // Step one: welcome prints + permission ask with Open Settings recovery on permanent denial.
    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Logo(36.dp)
            T(L.s("app_name"), size = 22, weight = FontWeight.Black, tight = true, modifier = Modifier.weight(1f))
            StepIndicator(step = 1, total = 2)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Layout(
                content = {
                    WelcomePrints(today.shortLabel())
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        T(L.s("welcome_title"), size = 32, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                        T(L.s("welcome_body"), muted = true)
                        if (denied) {
                            Row(
                                Modifier
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(p.accTint)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Outlined.WarningAmber, null, tint = p.accInk, modifier = Modifier.size(18.dp))
                                T(L.s("welcome_denied"), size = 14, weight = FontWeight.Medium, color = p.accInk)
                            }
                        }
                    }
                },
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) { (art, words), c ->
                val gap = 24.dp.roundToPx()
                val text = words.measure(Constraints(maxWidth = c.maxWidth))
                val room = viewport.roundToPx() - text.height - gap * 2
                val artHeight = if (room < 150.dp.roundToPx()) 0 else minOf(room, (c.maxWidth * 1.3f).toInt())
                val prints = if (artHeight > 0) art.measure(Constraints.fixed(c.maxWidth, artHeight)) else null
                val height = maxOf(viewport.roundToPx(), artHeight + text.height + gap * 2)
                layout(c.maxWidth, height) {
                    if (prints != null) prints.place(0, gap)
                    text.place(0, height - text.height - gap)
                }
            }
        }
        if (permanentlyDenied) {
            Pill(L.s("open_settings"), { openAppSettings(context) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Pill(L.s("show_my_photos"), askPhotos, kind = Kind.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth())
        } else {
            Pill(L.s("show_my_photos"), askPhotos, modifier = Modifier.fillMaxWidth())
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, null, tint = p.muted2, modifier = Modifier.size(13.dp))
            Text(
                L.s("privacy_line"),
                color = p.muted2,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
            )
        }
    }
}

@Composable
private fun StepIndicator(step: Int, total: Int) {
    val p = LocalPalette.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = L.s("n_of_total", "n" to step, "total" to total) },
    ) {
        for (i in 1..total) {
            val active = i == step
            Box(
                Modifier
                    .width(if (active) 22.dp else 8.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (active) p.ink else p.line),
            )
        }
    }
}

/**
 * The three tilted prints, drawn on a 328 x 400 board and scaled to fit the room they get.
 * Angles flip cleanly in RTL so the fan stays balanced across scripts.
 */
@Composable
private fun WelcomePrints(label: String) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sign = if (rtl) -1f else 1f
    BoxWithConstraints(Modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (maxHeight <= 1.dp || maxWidth <= 1.dp) return@BoxWithConstraints
        val scale = minOf(maxWidth / 328.dp, maxHeight / 400.dp)
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, 1f),
            LocalLayoutDirection provides LayoutDirection.Ltr,
        ) {
            Box(Modifier.requiredSize(328.dp, 400.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
                Print(2016, label, R.drawable.welcome_2016_hills, 150.dp, -9f * sign, Modifier.offset(4.dp, 38.dp))
                Print(2021, label, R.drawable.welcome_2021_sail, 150.dp, 8f * sign, Modifier.align(Alignment.TopEnd).offset((-4).dp, 8.dp))
                Print(2025, label, R.drawable.welcome_2025_cake, 176.dp, -2f * sign, Modifier.align(Alignment.TopCenter).offset(y = 122.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    vm: AppViewModel,
    bottomPadding: Dp,
    scrollTop: Flow<Tab>,
    selecting: Boolean,
    pickedKeys: List<String>,
    onSelectingChange: (Boolean) -> Unit,
    onPickedKeysChange: (List<String>) -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val reduce = LocalReduceMotion.current
    val view = LocalView.current
    val state by vm.saved.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val indexing by vm.indexing.collectAsStateWithLifecycle()
    val placesScanning by vm.placesScanning.collectAsStateWithLifecycle()
    val demo by vm.demoScene.collectAsStateWithLifecycle()
    val placeCard by vm.placeCard.collectAsStateWithLifecycle()

    // Start from the last result so the list keeps its place when this tab comes back.
    var result by remember { mutableStateOf(vm.lastDay) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var albumFor by remember { mutableStateOf<List<Photo>?>(null) }
    var shareFor by remember { mutableStateOf<ShareRequest?>(null) }
    var stripPick by rememberSaveable(day.key) { mutableStateOf<Int?>(null) }
    var placeOpenId by rememberSaveable { mutableStateOf<Int?>(null) }
    var placeAsk by rememberSaveable { mutableStateOf(false) }
    var cityList by remember { mutableStateOf<CityList?>(null) }
    LaunchedEffect(vm.places.hasCities) {
        if (vm.places.hasCities) cityList = vm.places.cities()
    }
    val placeOpenCity: City? = remember(placeOpenId, cityList, placeCard) {
        val id = placeOpenId ?: return@remember null
        if (placeCard?.city?.id == id) placeCard?.city else cityList?.cities?.getOrNull(id)
    }

    val list = rememberLazyListState()
    val topBar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scope = rememberCoroutineScope()
    val index = vm.index(state)

    val askPhotos = rememberPhotoPermission(vm) { ok ->
        if (!ok) vm.showToast(L.s("toast_allow_access"), ToastKind.Error)
    }
    val askNotify = rememberNotificationPermission { ok ->
        if (ok) vm.setReminder { it.copy(on = true) }
        else vm.showToast(L.s("toast_notifications_off"), ToastKind.Error)
    }
    val askPlaces = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.setPlaces(true) }

    // Guard PlaceAskSheet against active sheets, viewer, reel, selecting, or place screen.
    LaunchedEffect(index.isEmpty(), state.places.asked, demo) {
        if (index.isEmpty() || state.places.asked || demo != null || !vm.places.hasCities) return@LaunchedEffect
        delay(1500)
        if (!calendar && !settings && !selecting && placeOpenId == null && vm.viewer.value == null && vm.reel.value == null) {
            placeAsk = true
        }
    }

    LaunchedEffect(day, index) { result = (day to vm.photosFor(day)).also { vm.lastDay = it } }
    LaunchedEffect(Unit) {
        scrollTop.collect {
            if (reduce) list.scrollToItem(0) else list.animateScrollToItem(0)
            topBar.state.heightOffset = 0f
        }
    }
    val groups = result?.takeIf { it.first == day }?.second
    val allPhotos = remember(groups) { groups?.flatMap { it.photos }.orEmpty() }
    val photoByKey = remember(allPhotos) { allPhotos.associateBy { it.key } }
    val picked = remember(pickedKeys, photoByKey) { pickedKeys.mapNotNull { photoByKey[it] } }
    val pickedSet = remember(pickedKeys) { pickedKeys.toSet() }

    LaunchedEffect(demo, groups) {
        val first = groups?.firstOrNull() ?: return@LaunchedEffect
        when (demo) {
            "calendar" -> calendar = true
            "settings" -> settings = true
            "select" -> {
                onSelectingChange(true)
                onPickedKeysChange(first.photos.take(3).map { it.key })
            }
            "share" -> shareFor = ShareRequest(fullDate(first.year, day), first.photos)
            "addalbum" -> albumFor = first.photos.take(3)
        }
    }

    fun stopSelecting() {
        onSelectingChange(false)
        onPickedKeysChange(emptyList())
    }
    fun go(md: MonthDay) {
        calendar = false
        stopSelecting()
        vm.day.value = md
        scope.launch {
            list.scrollToItem(0)
            topBar.state.heightOffset = 0f
        }
    }
    BackHandler(enabled = selecting) { stopSelecting() }

    val today = MonthDay.today()
    val hidden = day.key in state.hiddenDates
    val total = allPhotos.size
    val canSelect = !hidden && total > 0
    val allPicked = total > 0 && pickedSet.size >= total
    val summary = when {
        hidden -> L.s("you_hid_date")
        groups == null || indexing && groups.isEmpty() -> L.s("today_loading")
        groups.isNotEmpty() -> L.s("photos_from_years", "photos" to L.p("photos", total), "years" to L.p("years", groups.size))
        else -> L.s("today_none")
    }

    val pickedKeysNow by rememberUpdatedState(pickedKeys)
    val selection = remember(selecting, pickedSet, view) {
        Selection(
            on = selecting,
            keys = pickedSet,
            start = { ph ->
                Haptics.longPress(view)
                onSelectingChange(true)
                onPickedKeysChange(listOf(ph.key))
            },
            toggle = { ph ->
                Haptics.tick(view)
                val cur = pickedKeysNow
                onPickedKeysChange(if (ph.key in cur) cur.filterNot { it == ph.key } else cur + ph.key)
            },
            toggleAll = { batch, select ->
                Haptics.tick(view)
                val batchKeys = batch.map { it.key }.toSet()
                val cur = pickedKeysNow
                val next = if (select) {
                    cur + batch.map { it.key }.filterNot { it in cur }
                } else {
                    cur.filterNot { it in batchKeys }
                }
                onPickedKeysChange(next)
            },
        )
    }

    val favorites = remember(state.favorites) { state.favorites.map { it.key }.toSet() }
    val partialAccess = vm.library.hasPartialAccess()

    // Delta-time normalized auto-scroll (consistent across 60Hz/90Hz/120Hz) + post-layout recheck.
    val drag = remember { DragSelect() }
    val dragOrder = remember(groups, state.layout, stripPick) {
        if (state.layout == YearLayout.STRIP && !groups.isNullOrEmpty()) {
            val years = groups.map { it.year }
            val at = stripPick?.let { years.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            val g = groups[at]
            val cols = masonryColumns(g.photos.map { tileAspect(it.width, it.height) })
            cols.flatten().map { g.photos[it] }
        } else {
            allPhotos
        }
    }
    val orderNow by rememberUpdatedState(dragOrder)
    val pickedNow by rememberUpdatedState(picked)
    val selectingNow by rememberUpdatedState(selecting)
    val maxAutoScrollPxPerSec = with(density) { 480.dp.toPx() }
    LaunchedEffect(drag, maxAutoScrollPxPerSec) {
        snapshotFlow { drag.edge.floatValue }.collectLatest { factor ->
            if (factor == 0f) return@collectLatest
            var prev = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - prev) / 1e9f).coerceIn(0f, 0.05f)
                prev = now
                list.scrollBy(factor * maxAutoScrollPxPerSec * dt)
                // Wait one frame for layout to commit updated tile rects before rechecking hit-test.
                withFrameNanos {}
                drag.recheck?.invoke()
            }
        }
    }

    // Sideways day-swipe: no per-delta coroutine spawns, dp-based flick velocity, YearPills exclusion, Reduce Motion aware.
    var swipeDragPx by remember { mutableFloatStateOf(0f) }
    var yearPillsBounds by remember { mutableStateOf<Rect?>(null) }
    var listOriginY by remember { mutableFloatStateOf(0f) }
    var listOriginX by remember { mutableFloatStateOf(0f) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val neighbors = remember(day, index) { swipeTarget(index, day, -1) to swipeTarget(index, day, 1) }
    fun target(dx: Float) = if ((dx < 0) != rtl) neighbors.second else neighbors.first
    fun land(t: Pair<MonthDay, Int>) {
        Haptics.tick(view)
        go(t.first)
        if (t.second > 0) vm.showToast(L.p("skipped_days", t.second), ToastKind.Info)
    }
    val flickThresholdPx = with(density) { 400.dp.toPx() }
    val dragThresholdPx = with(density) { 80.dp.toPx() }

    val onShareYear = remember(day) {
        { g: YearGroup -> shareFor = ShareRequest(fullDate(g.year, day), g.photos) }
    }

    // Single derivedStateOf computing which year is currently scrolled past for the floating YearBar.
    val pinnedYear by remember(groups, state.layout, hidden) {
        derivedStateOf {
            if (hidden || state.layout != YearLayout.MOSAIC || groups.isNullOrEmpty()) return@derivedStateOf null
            val info = list.layoutInfo
            val visible = info.visibleItemsInfo
            val firstPhoto = visible.firstOrNull { (it.key as? String)?.startsWith("photos-") == true } ?: return@derivedStateOf null
            val year = (firstPhoto.key as String).removePrefix("photos-").toIntOrNull() ?: return@derivedStateOf null
            val headVisible = visible.any { it.key == "head-$year" && it.offset >= info.viewportStartOffset - 8 }
            if (headVisible) null else groups.firstOrNull { it.year == year }
        }
    }

    // Non-nested CARDS pager state.
    val cardsActive = state.layout == YearLayout.CARDS && !hidden && !groups.isNullOrEmpty()
    val cardPager = rememberPagerState(initialPage = 0) { if (cardsActive) groups!!.size else 1 }
    LaunchedEffect(day.key) { if (cardPager.currentPage != 0) cardPager.scrollToPage(0) }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(topBar.nestedScrollConnection)
                .semantics { if (placeOpenCity != null) hideFromAccessibility() },
        ) {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .weight(1f, fill = false)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button) { calendar = true }
                                .semantics {
                                    heading()
                                    customActions = listOfNotNull(
                                        neighbors.first?.let { t -> CustomAccessibilityAction(L.s("previous_day_photos")) { land(t); true } },
                                        neighbors.second?.let { t -> CustomAccessibilityAction(L.s("next_day_photos")) { land(t); true } },
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                day.label(),
                                color = p.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                                style = TextStyle(
                                    fontFamily = Nunito,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    lineHeight = (if (L.lang.arabicScript) 40 else 34).sp,
                                    letterSpacing = tightSpacing(28).sp,
                                ),
                            )
                            Icon(Icons.Filled.ArrowDropDown, L.s("pick_another_day"), tint = Accent)
                        }
                        if (!selecting && (neighbors.first != null || neighbors.second != null)) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { neighbors.first?.let(::land) },
                                enabled = neighbors.first != null,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    L.s("previous_day_photos"),
                                    tint = p.ink.copy(alpha = if (neighbors.first != null) 0.72f else 0.25f),
                                )
                            }
                            IconButton(
                                onClick = { neighbors.second?.let(::land) },
                                enabled = neighbors.second != null,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    L.s("next_day_photos"),
                                    tint = p.ink.copy(alpha = if (neighbors.second != null) 0.72f else 0.25f),
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        TextButton(onClick = {
                            Haptics.tick(view)
                            onPickedKeysChange(if (allPicked) emptyList() else allPhotos.map { it.key })
                        }) {
                            T(L.s(if (allPicked) "clear_selection" else "select_all"), size = 14, weight = FontWeight.SemiBold, color = Accent)
                        }
                        TextButton(onClick = ::stopSelecting) {
                            T(L.s("cancel"), size = 15, weight = FontWeight.SemiBold)
                        }
                    } else {
                        TextButton(
                            onClick = { onSelectingChange(true) },
                            enabled = canSelect,
                        ) {
                            T(
                                L.s("select"),
                                size = 15,
                                weight = FontWeight.SemiBold,
                                color = p.ink.copy(alpha = if (canSelect) 1f else 0.35f),
                            )
                        }
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Outlined.MoreVert, L.s("options"), tint = p.ink)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = p.surface) {
                                if (allPhotos.size >= 2 && !hidden) {
                                    DropdownMenuItem(
                                        text = { T(L.s("make_reel")) },
                                        leadingIcon = { Icon(Icons.Outlined.Movie, null, tint = p.ink) },
                                        onClick = { menu = false; vm.startReel(allPhotos) },
                                    )
                                    HorizontalDivider(color = p.line)
                                }
                                listOf(
                                    YearLayout.MOSAIC to "layout_all_years",
                                    YearLayout.STRIP to "layout_one_year",
                                    YearLayout.CARDS to "layout_cards",
                                ).forEach { (layout, key) ->
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
                                HorizontalDivider(color = p.line)
                                val destructive = !hidden
                                DropdownMenuItem(
                                    text = {
                                        T(
                                            if (hidden) L.s("show_day_again", "day" to day.label()) else L.s("hide_date"),
                                            color = if (destructive) MaterialTheme.colorScheme.error else p.ink,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                            null,
                                            tint = if (destructive) MaterialTheme.colorScheme.error else p.ink,
                                        )
                                    },
                                    onClick = { menu = false; vm.setHidden(day, !hidden) },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = p.bg,
                    scrolledContainerColor = p.bg.copy(alpha = 0.94f),
                ),
                scrollBehavior = topBar,
            )

            val swipeModifier = Modifier
                .onGloballyPositioned {
                    val pos = it.positionInRoot()
                    listOriginX = pos.x
                    listOriginY = pos.y
                }
                .pointerInput(selecting, neighbors, rtl, reduce, flickThresholdPx, dragThresholdPx) {
                    if (selecting) return@pointerInput
                    var dx = 0f
                    var ignored = false
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            dx = 0f
                            tracker.resetTracking()
                            val rootX = startOffset.x + listOriginX
                            val rootY = startOffset.y + listOriginY
                            ignored = yearPillsBounds?.contains(androidx.compose.ui.geometry.Offset(rootX, rootY)) == true
                        },
                        onDragCancel = {
                            if (ignored) return@detectHorizontalDragGestures
                            scope.launch {
                                if (reduce) swipeDragPx = 0f
                                else animate(swipeDragPx, 0f, animationSpec = spring()) { v, _ -> swipeDragPx = v }
                            }
                        },
                        onDragEnd = {
                            if (ignored) return@detectHorizontalDragGestures
                            val v = tracker.calculateVelocity().x
                            val t = target(dx)
                            val far = abs(dx) > dragThresholdPx || (abs(v) > flickThresholdPx && (v < 0) == (dx < 0))
                            scope.launch {
                                if (t != null && far) {
                                    if (reduce) {
                                        swipeDragPx = 0f
                                        land(t)
                                    } else {
                                        val out = if (dx < 0) -size.width.toFloat() else size.width.toFloat()
                                        animate(swipeDragPx, out, animationSpec = tween(150)) { cur, _ -> swipeDragPx = cur }
                                        land(t)
                                        swipeDragPx = -out
                                        animate(-out, 0f, animationSpec = tween(220)) { cur, _ -> swipeDragPx = cur }
                                    }
                                } else {
                                    if (reduce) swipeDragPx = 0f
                                    else animate(swipeDragPx, 0f, animationSpec = spring()) { cur, _ -> swipeDragPx = cur }
                                }
                            }
                        },
                    ) { change, amount ->
                        if (ignored) return@detectHorizontalDragGestures
                        change.consume()
                        dx += amount
                        tracker.addPosition(change.uptimeMillis, change.position)
                        swipeDragPx = if (target(dx) == null) dx / 4f else dx
                    }
                }
                .graphicsLayer {
                    translationX = swipeDragPx
                    alpha = if (size.width > 0f) 1f - (abs(swipeDragPx) / size.width).coerceIn(0f, 1f) * 0.5f else 1f
                }

            PullToRefreshBox(
                isRefreshing = indexing,
                onRefresh = {
                    if (!cardsActive || (cardPager.currentPage == 0 && !cardPager.isScrollInProgress)) {
                        vm.refreshIndex(force = true)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (cardsActive) {
                    // Non-nested layout for YearCards so VerticalPager never conflicts with LazyColumn.
                    Column(
                        Modifier
                            .fillMaxSize()
                            .then(swipeModifier)
                            .padding(bottom = bottomPadding + 12.dp),
                    ) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                            T(summary, size = 15, muted = true)
                            if (day != today) {
                                Row(Modifier.padding(top = 8.dp)) {
                                    Pill(L.s("back_to_today"), { go(today) }, kind = Kind.Tint, height = 44.dp, icon = { Icon(Icons.AutoMirrored.Filled.Undo, null, tint = it, modifier = Modifier.size(14.dp)) })
                                }
                            }
                        }
                        if (partialAccess && !selecting) {
                            LimitedAccessBanner(
                                onManage = askPhotos,
                                onSettings = { openAppSettings(context) },
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                            )
                        }
                        YearCards(
                            groups = groups!!,
                            day = day,
                            favorites = favorites,
                            selection = selection,
                            onOpen = { photos, i -> vm.viewer.value = Viewer(photos, i) },
                            onShare = { year, photos -> shareFor = ShareRequest(fullDate(year, day), photos) },
                            pager = cardPager,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    CompositionLocalProvider(LocalDragSelect provides drag) {
                        Box(Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = list,
                                contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        drag.modifier(
                                            order = { orderNow },
                                            selecting = { selectingNow },
                                            picked = { pickedNow },
                                        ) { nextPicked ->
                                            if (nextPicked.size != pickedNow.size) Haptics.tick(view)
                                            onPickedKeysChange(nextPicked.map { it.key })
                                        },
                                    )
                                    .then(swipeModifier),
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
                                if (partialAccess && !selecting) {
                                    item(key = "limited-access") {
                                        LimitedAccessBanner(
                                            onManage = askPhotos,
                                            onSettings = { openAppSettings(context) },
                                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                        )
                                    }
                                }
                                placeCard?.takeIf { !selecting }?.let { card ->
                                    item(key = "place") {
                                        PlaceCardView(
                                            vm,
                                            card,
                                            onOpen = { placeOpenId = card.city.id },
                                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                                        )
                                    }
                                }
                                when {
                                    hidden -> item(key = "hidden") { HiddenDay(day) { vm.setHidden(day, false) } }
                                    groups == null -> item(key = "skeleton") { Skeleton() }
                                    groups.isEmpty() -> item(key = "empty") {
                                        val noSources = !state.sources.device
                                        EmptyDay(day, noSources, nearbyDays(index, day), onGo = ::go, onConnect = { settings = true })
                                    }
                                    state.layout == YearLayout.STRIP -> {
                                        val years = groups.map { it.year }
                                        val at = stripPick?.let { years.indexOf(it) }?.takeIf { it >= 0 } ?: 0
                                        val g = groups[at]
                                        stickyHeader(key = "years") {
                                            YearPills(years, g.year, onBounds = { yearPillsBounds = it }) { pickedYear ->
                                                stripPick = pickedYear
                                                scope.launch {
                                                    if (reduce) list.scrollToItem(0) else list.animateScrollToItem(0)
                                                }
                                            }
                                        }
                                        item(key = "strip-${g.year}") {
                                            StripYear(
                                                g.year,
                                                g.photos,
                                                day,
                                                favorites,
                                                selection,
                                                newer = years.getOrNull(at - 1),
                                                older = years.getOrNull(at + 1),
                                                onOpen = { i -> vm.viewer.value = Viewer(g.photos, i) },
                                                onShare = { onShareYear(g) },
                                                onPick = { nextYear ->
                                                    stripPick = nextYear
                                                    scope.launch {
                                                        if (reduce) list.scrollToItem(0) else list.animateScrollToItem(0)
                                                    }
                                                },
                                            )
                                        }
                                    }
                                    else -> groups.forEach { g ->
                                        val yearAllPicked = g.photos.isNotEmpty() && g.photos.all { it.key in pickedSet }
                                        item(key = "head-${g.year}") {
                                            YearHeader(
                                                year = g.year,
                                                count = g.photos.size,
                                                day = day,
                                                onShare = { onShareYear(g) },
                                                selecting = selecting,
                                                allPicked = yearAllPicked,
                                                onToggleYear = { selection.toggleAll?.invoke(g.photos, !yearAllPicked) },
                                            )
                                        }
                                        item(key = "photos-${g.year}") {
                                            YearMosaic(
                                                g.photos,
                                                favorites,
                                                selection,
                                                onOpen = { i -> vm.viewer.value = Viewer(g.photos, i) },
                                            )
                                        }
                                    }
                                }
                            }

                            // Single floating YearBar overlay for the currently scrolled-past year.
                            pinnedYear?.let { g ->
                                val yearAllPicked = g.photos.isNotEmpty() && g.photos.all { it.key in pickedSet }
                                YearBar(
                                    year = g.year,
                                    count = g.photos.size,
                                    day = day,
                                    pinned = true,
                                    onShare = { onShareYear(g) },
                                    selecting = selecting,
                                    allPicked = yearAllPicked,
                                    onToggleYear = { selection.toggleAll?.invoke(g.photos, !yearAllPicked) },
                                    modifier = Modifier.align(Alignment.TopCenter),
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selecting,
            enter = if (reduce) EnterTransition.None else slideInVertically(tween(200)) { it } + fadeIn(tween(180)),
            exit = if (reduce) ExitTransition.None else slideOutVertically(tween(180)) { it } + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
        ) {
            SelectBar(
                picked.size,
                onShare = { shareFor = ShareRequest(L.p("photos", picked.size), picked) },
                onAlbum = { albumFor = picked },
                onReel = { if (vm.startReel(picked)) stopSelecting() },
            )
        }

        placeOpenCity?.let { city ->
            PlaceScreen(vm, city, state.placeIndex, bottomPadding, onBack = { placeOpenId = null })
        }
    }

    if (placeAsk) {
        PlaceAskSheet(
            onTurnOn = { placeAsk = false; askPlaces.launch(vm.places.permissions) },
            onNotNow = { placeAsk = false; vm.placesAsked() },
        )
    }

    if (calendar) {
        CalendarSheet(
            start = day,
            index = index,
            coversForMonth = vm::coversForMonth,
            hiddenDates = state.hiddenDates,
            onPick = ::go,
            onDismiss = { calendar = false },
        )
    }

    if (settings) {
        val visitedCities = remember(state.placeIndex, cityList) {
            val listCities = cityList?.cities ?: return@remember emptyList()
            state.placeIndex.entries
                .sortedByDescending { it.value.size }
                .mapNotNull { listCities.getOrNull(it.key) }
        }
        val activeHomeId = state.customHomeCity ?: remember(state.placeIndex) { homeCity(state.placeIndex) }
        val homeName = remember(activeHomeId, cityList) { activeHomeId?.let { cityList?.cities?.getOrNull(it)?.name } }
        SettingsSheet(
            state = state,
            day = day,
            partialAccess = partialAccess,
            placesScanning = placesScanning,
            homeCityName = homeName,
            visitedCities = visitedCities,
            onConnectDevice = { if (!state.sources.device) askPhotos() },
            onManagePhotos = askPhotos,
            onOpenSettings = { openAppSettings(context) },
            onReminder = { on -> if (on) askNotify() else vm.setReminder { it.copy(on = false) } },
            onReminderTime = { h, m -> vm.setReminder { it.copy(hour = h, minute = m) } },
            onLayout = vm::setLayout,
            onHidden = { md, on -> vm.setHidden(md, on); if (md == day) settings = false },
            onHomeCity = { id -> vm.setHomeCity(id) },
            onDismiss = { settings = false },
            onPlaces = if (!vm.places.hasCities) null else { on -> if (on) askPlaces.launch(vm.places.permissions) else vm.setPlaces(false) },
        )
    }

    albumFor?.let { photos ->
        AlbumSheet(
            photos,
            state.albums,
            onCreate = { name -> vm.createAlbum(name, photos); albumFor = null; stopSelecting() },
            onAdd = { a ->
                vm.addToAlbum(a.id, photos)
                albumFor = null
                stopSelecting()
            },
            onDismiss = { albumFor = null },
        )
    }

    shareFor?.let { req ->
        ShareSheet(
            req,
            onSend = {
                if (!sharePhotos(context, req.photos, req.title)) {
                    vm.showToast(L.s("share_failed"), ToastKind.Error)
                }
                shareFor = null
            },
            onSave = { vm.saveToGallery(req.title, req.photos); shareFor = null },
            onDismiss = { shareFor = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimitedAccessBanner(onManage: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(p.accTint)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.WarningAmber, null, tint = p.accInk, modifier = Modifier.size(18.dp))
            T(L.s("limited_access"), size = 15, weight = FontWeight.Bold, color = p.accInk)
        }
        T(L.s("limited_access_note"), size = 13, color = p.accInk)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(L.s("manage_photos"), onManage, kind = Kind.Secondary, height = 40.dp)
            Pill(L.s("open_settings"), onSettings, kind = Kind.Ghost, height = 40.dp)
        }
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
        Row { Pill(L.s("show_it_again"), onShow, height = 44.dp) }
    }
}

@Composable
private fun Skeleton() {
    val p = LocalPalette.current
    Column(
        Modifier
            .padding(16.dp)
            .padding(top = 8.dp)
            .semantics { contentDescription = L.s("loading") },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(128.dp, 48.dp).clip(RoundedCornerShape(10.dp)).shimmer(p.fill2, p.fill))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(76.dp, 28.dp).clip(RoundedCornerShape(8.dp)).shimmer(p.fill, p.fill2))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(2f).height(228.dp).clip(RoundedCornerShape(10.dp)).shimmer(p.fill2, p.fill))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(10.dp)).shimmer(p.fill, p.fill2))
                Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(10.dp)).shimmer(p.fill2, p.fill))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
            size = 15,
            muted = true,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (noSources) Pill(L.s("connect_photos"), onConnect, height = 44.dp)
            before?.let { md ->
                Pill(
                    md.shortLabel(),
                    { onGo(md) },
                    kind = Kind.Secondary,
                    height = 44.dp,
                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = it, modifier = Modifier.size(15.dp)) },
                )
            }
            after?.let { md ->
                Pill(
                    md.shortLabel(),
                    { onGo(md) },
                    kind = Kind.Secondary,
                    height = 44.dp,
                    iconAfter = true,
                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = it, modifier = Modifier.size(15.dp)) },
                )
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, horizontalPadding: Dp = 20.dp, trailing: (@Composable () -> Unit)? = null) {
    val largeFont = LocalDensity.current.fontScale >= 1.35f
    if (largeFont && trailing != null) {
        Column(
            Modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T(title, size = 34, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
            T(subtitle, size = 15, muted = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { trailing() }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = horizontalPadding, end = horizontalPadding, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                T(title, size = 34, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                T(subtitle, size = 15, muted = true)
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun EmptyNote(icon: ImageVector, title: String, body: String) {
    val p = LocalPalette.current
    Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(p.accTint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(28.dp))
        }
        T(title, size = 22, weight = FontWeight.Bold, tight = true)
        T(body, size = 15, muted = true, modifier = Modifier.widthIn(max = 300.dp))
    }
}

/** Subtle status-bar scrim so scrolling grids never clash under transparent system status icons. */
@Composable
private fun StatusBarScrim(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(p.bg.copy(alpha = 0.92f), p.bg.copy(alpha = 0f))))
            .statusBarsPadding()
            .height(10.dp),
    )
}

@Composable
fun FavoritesScreen(
    vm: AppViewModel,
    bottomPadding: Dp,
    scrollTop: Flow<Tab>,
    selecting: Boolean,
    pickedKeys: List<String>,
    onSelectingChange: (Boolean) -> Unit,
    onPickedKeysChange: (List<String>) -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val view = LocalView.current
    val reduce = LocalReduceMotion.current
    val state by vm.saved.collectAsStateWithLifecycle()
    val favs = state.favorites
    val grid = rememberLazyGridState()
    var shareFor by remember { mutableStateOf<ShareRequest?>(null) }
    var albumFor by remember { mutableStateOf<List<Photo>?>(null) }

    LaunchedEffect(Unit) {
        scrollTop.collect {
            if (reduce) grid.scrollToItem(0) else grid.animateScrollToItem(0)
        }
    }

    fun stopSelecting() {
        onSelectingChange(false)
        onPickedKeysChange(emptyList())
    }
    BackHandler(enabled = selecting) { stopSelecting() }

    val favByKey = remember(favs) { favs.associateBy { it.key } }
    val picked = remember(pickedKeys, favByKey) { pickedKeys.mapNotNull { favByKey[it] } }
    val pickedSet = remember(pickedKeys) { pickedKeys.toSet() }
    val pickedKeysNow by rememberUpdatedState(pickedKeys)
    val selection = remember(selecting, pickedSet, view) {
        Selection(
            on = selecting,
            keys = pickedSet,
            start = { ph ->
                Haptics.longPress(view)
                onSelectingChange(true)
                onPickedKeysChange(listOf(ph.key))
            },
            toggle = { ph ->
                Haptics.tick(view)
                val cur = pickedKeysNow
                onPickedKeysChange(if (ph.key in cur) cur.filterNot { it == ph.key } else cur + ph.key)
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg)) {
        val cols = maxOf(3, (maxWidth / 120.dp).toInt())
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            state = grid,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = bottomPadding + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            item(key = "favs-header", span = { GridItemSpan(maxLineSpan) }) {
                val years = remember(favs) { favs.map { localDate(it.time).year }.toSet().size }
                val allPicked = favs.isNotEmpty() && pickedSet.size >= favs.size
                Header(
                    L.s("tab_favorites"),
                    if (favs.isEmpty()) L.s("favorites_none")
                    else L.s("photos_from_years", "photos" to L.p("photos", favs.size), "years" to L.p("years", years)),
                    horizontalPadding = 4.dp,
                    trailing = if (favs.isEmpty()) null else {
                        {
                            if (selecting) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        Haptics.tick(view)
                                        onPickedKeysChange(if (allPicked) emptyList() else favs.map { it.key })
                                    }) {
                                        T(L.s(if (allPicked) "clear_selection" else "select_all"), size = 14, weight = FontWeight.SemiBold, color = Accent)
                                    }
                                    TextButton(onClick = ::stopSelecting) {
                                        T(L.s("cancel"), size = 15, weight = FontWeight.SemiBold)
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (favs.size >= 2) {
                                        IconButton(onClick = { vm.startReel(favs) }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Outlined.Movie, L.s("make_reel"), tint = p.ink, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    TextButton(onClick = { onSelectingChange(true) }) {
                                        T(L.s("select"), size = 15, weight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    },
                )
            }
            if (favs.isEmpty()) {
                item(key = "favs-empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyNote(Icons.Outlined.FavoriteBorder, L.s("favorites_empty_title"), L.s("favorites_empty_body"))
                }
            } else {
                itemsIndexed(favs, key = { _, ph -> ph.key }) { i, photo ->
                    Box(Modifier.aspectRatio(1f)) {
                        PhotoTile(
                            photo = photo,
                            radius = 12.dp,
                            favorite = false,
                            selection = selection,
                            onOpen = { vm.viewer.value = Viewer(favs, i) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        DateChip(formatDate(localDate(photo.time), "yMMMd"), Modifier.align(Alignment.BottomStart).padding(6.dp))
                    }
                }
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))

        AnimatedVisibility(
            visible = selecting,
            enter = if (reduce) EnterTransition.None else slideInVertically(tween(200)) { it } + fadeIn(tween(180)),
            exit = if (reduce) ExitTransition.None else slideOutVertically(tween(180)) { it } + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
        ) {
            SelectBar(
                picked.size,
                onShare = { shareFor = ShareRequest(L.p("photos", picked.size), picked) },
                onAlbum = { albumFor = picked },
                onReel = { if (vm.startReel(picked)) stopSelecting() },
            )
        }
    }

    albumFor?.let { photos ->
        AlbumSheet(
            photos,
            state.albums,
            onCreate = { name -> vm.createAlbum(name, photos); albumFor = null; stopSelecting() },
            onAdd = { a ->
                vm.addToAlbum(a.id, photos)
                albumFor = null
                stopSelecting()
            },
            onDismiss = { albumFor = null },
        )
    }

    shareFor?.let { req ->
        ShareSheet(
            req,
            onSend = {
                if (!sharePhotos(context, req.photos, req.title)) {
                    vm.showToast(L.s("share_failed"), ToastKind.Error)
                }
                shareFor = null
            },
            onSave = { vm.saveToGallery(req.title, req.photos); shareFor = null },
            onDismiss = { shareFor = null },
        )
    }
}

@Composable
fun AlbumsScreen(
    vm: AppViewModel,
    bottomPadding: Dp,
    scrollTop: Flow<Tab>,
    onOpen: (Album) -> Unit,
) {
    val p = LocalPalette.current
    val view = LocalView.current
    val reduce = LocalReduceMotion.current
    val state by vm.saved.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var creating by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scrollTop.collect {
            if (reduce) list.scrollToItem(0) else list.animateScrollToItem(0)
        }
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        ) {
            item(key = "albums-header") {
                Header(L.s("tab_albums"), L.p("albums", state.albums.size)) {
                    Pill(
                        L.s("new_album"),
                        { creating = true },
                        height = 44.dp,
                        icon = { Icon(Icons.Filled.Add, null, tint = it, modifier = Modifier.size(16.dp)) },
                    )
                }
                if (state.albums.isEmpty()) {
                    EmptyNote(Icons.Outlined.PhotoLibrary, L.s("albums_empty_title"), L.s("albums_empty_body"))
                }
            }
            items(state.albums, key = { it.id }) { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) { onOpen(a) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AlbumCover(a.photos)
                    Column(Modifier.weight(1f)) {
                        T(a.name, size = 17, weight = FontWeight.SemiBold, maxLines = 1)
                        T(L.s("album_made", "photos" to L.p("photos", a.photos.size), "date" to formatDate(localDate(a.createdAt), "MMMd")), size = 14, muted = true)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.faint, modifier = Modifier.size(22.dp))
                }
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
    }

    if (creating) {
        var name by rememberSaveable { mutableStateOf("") }
        val submit = {
            if (name.isNotBlank()) {
                Haptics.confirm(view)
                val album = vm.createAlbum(name.trim(), emptyList())
                creating = false
                onOpen(album)
            }
        }
        AlertDialog(
            onDismissRequest = { creating = false },
            containerColor = p.surface,
            title = { T(L.s("new_album_name"), size = 20, weight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { T(L.s("album_name")) },
                    placeholder = { T(L.s("album_name_example"), color = p.muted) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = p.ink),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = p.line, cursorColor = Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            },
            confirmButton = {
                TextButton(onClick = submit, enabled = name.isNotBlank()) { T(L.s("create"), weight = FontWeight.SemiBold, color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) { T(L.s("cancel"), weight = FontWeight.SemiBold) }
            },
        )
    }
}

/** Adaptive 0 / 1 / 2 / 3 / 4-photo album cover (72dp square). */
@Composable
private fun AlbumCover(photos: List<Photo>) {
    val p = LocalPalette.current
    val cover = photos.take(4)
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(p.fill), contentAlignment = Alignment.Center) {
        when (cover.size) {
            0 -> Icon(Icons.Outlined.PhotoLibrary, null, tint = p.muted, modifier = Modifier.size(26.dp))
            1 -> {
                val req = rememberThumbnailRequest(cover[0].uri, maxPx = 220, crossfade = false)
                AsyncImage(req, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                cover.forEach { ph ->
                    val req = rememberThumbnailRequest(ph.uri, maxPx = 200, crossfade = false)
                    AsyncImage(req, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
            3 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val r0 = rememberThumbnailRequest(cover[0].uri, maxPx = 200, crossfade = false)
                AsyncImage(r0, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    cover.drop(1).forEach { ph ->
                        val req = rememberThumbnailRequest(ph.uri, maxPx = 160, crossfade = false)
                        AsyncImage(req, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                cover.chunked(2).forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { ph ->
                            val req = rememberThumbnailRequest(ph.uri, maxPx = 160, crossfade = false)
                            AsyncImage(req, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(vm: AppViewModel, album: Album, bottomPadding: Dp, scrollTop: Flow<Tab>, onBack: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val view = LocalView.current
    val reduce = LocalReduceMotion.current
    val largeFont = LocalDensity.current.fontScale >= 1.35f
    val state by vm.saved.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val grid = rememberLazyGridState()

    LaunchedEffect(Unit) {
        scrollTop.collect {
            if (reduce) grid.scrollToItem(0) else grid.animateScrollToItem(0)
        }
    }
    BackHandler { if (editing) editing = false else onBack() }

    fun removePhoto(photo: Photo) {
        Haptics.tick(view)
        vm.removeFromAlbum(album.id, photo.key)
        vm.showToast(
            L.s("remove_from_album"),
            ToastKind.Info,
            L.s("undo"),
        ) {
            vm.addToAlbum(album.id, listOf(photo))
        }
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, L.s("close"), tint = p.ink)
                }
            },
            actions = {
                if (editing) {
                    TextButton(onClick = { editing = false }) {
                        T(L.s("save"), size = 15, weight = FontWeight.SemiBold, color = Accent)
                    }
                } else {
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.MoreVert, L.s("more"), tint = p.ink)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = p.surface) {
                            if (album.photos.size >= 2) {
                                DropdownMenuItem(
                                    text = { T(L.s("make_reel")) },
                                    leadingIcon = { Icon(Icons.Outlined.Movie, null, tint = p.ink) },
                                    onClick = { menu = false; vm.startReel(album.photos) },
                                )
                            }
                            if (album.photos.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { T(L.s("remove_from_album")) },
                                    leadingIcon = { Icon(Icons.Filled.Close, null, tint = p.ink) },
                                    onClick = { menu = false; editing = true },
                                )
                            }
                            DropdownMenuItem(text = { T(L.s("rename")) }, onClick = { menu = false; renaming = true })
                            HorizontalDivider(color = p.line)
                            DropdownMenuItem(
                                text = { T(L.s("delete_album"), color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = p.bg, scrolledContainerColor = p.bg),
        )
        val favorites = remember(state.favorites) { state.favorites.map { it.key }.toSet() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cols = maxOf(3, (maxWidth / 120.dp).toInt())
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                state = grid,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "album-header", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 16.dp)) {
                            T(album.name, size = 30, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                            T(L.s("album_made", "photos" to L.p("photos", album.photos.size), "date" to formatDate(localDate(album.createdAt), "MMMd")), size = 15, muted = true)
                        }
                        if (largeFont) {
                            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill(
                                    L.s("share_album"),
                                    { sharing = true },
                                    height = 48.dp,
                                    enabled = album.photos.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = { Icon(Icons.Outlined.Share, null, tint = it, modifier = Modifier.size(18.dp)) },
                                )
                                if (CAN_SAVE_TO_GALLERY) {
                                    Pill(
                                        L.s("save_to_gallery"),
                                        { vm.saveToGallery(album.name, album.photos) },
                                        kind = Kind.Secondary,
                                        height = 48.dp,
                                        enabled = album.photos.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth(),
                                        icon = { Icon(Icons.Outlined.Download, null, tint = it, modifier = Modifier.size(18.dp)) },
                                    )
                                }
                            }
                        } else {
                            Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Pill(
                                    L.s("share_album"),
                                    { sharing = true },
                                    height = 48.dp,
                                    enabled = album.photos.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                    icon = { Icon(Icons.Outlined.Share, null, tint = it, modifier = Modifier.size(18.dp)) },
                                )
                                if (CAN_SAVE_TO_GALLERY) {
                                    Pill(
                                        L.s("save_to_gallery"),
                                        { vm.saveToGallery(album.name, album.photos) },
                                        kind = Kind.Secondary,
                                        height = 48.dp,
                                        enabled = album.photos.isNotEmpty(),
                                        modifier = Modifier.weight(1f),
                                        icon = { Icon(Icons.Outlined.Download, null, tint = it, modifier = Modifier.size(18.dp)) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (album.photos.isEmpty()) {
                    item(key = "album-empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyNote(Icons.Outlined.PhotoLibrary, L.s("album_empty"), L.s("albums_empty_body"))
                    }
                } else {
                    itemsIndexed(album.photos, key = { _, ph -> ph.key }) { i, photo ->
                        Box(Modifier.aspectRatio(1f)) {
                            PhotoTile(
                                photo = photo,
                                radius = 12.dp,
                                favorite = photo.key in favorites,
                                selection = null,
                                onOpen = {
                                    if (editing) removePhoto(photo)
                                    else vm.viewer.value = Viewer(album.photos, i)
                                },
                                customActionsList = listOf(
                                    CustomAccessibilityAction(L.s("remove_from_album")) {
                                        removePhoto(photo)
                                        true
                                    },
                                ),
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (editing) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .size(36.dp)
                                        .clickable(role = Role.Button) { removePhoto(photo) }
                                        .semantics { contentDescription = L.s("remove_from_album") },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.size(24.dp).clip(CircleShape).background(AccentFill),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        var name by rememberSaveable { mutableStateOf(album.name) }
        val commitRename = {
            if (name.isNotBlank()) {
                vm.renameAlbum(album.id, name)
                renaming = false
            }
        }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = p.surface,
            title = { T(L.s("rename_album"), size = 20, weight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { T(L.s("album_name")) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = p.ink),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = p.line, cursorColor = Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitRename() }),
                )
            },
            confirmButton = {
                TextButton(onClick = commitRename, enabled = name.isNotBlank()) { T(L.s("save"), weight = FontWeight.SemiBold, color = Accent) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { T(L.s("cancel"), weight = FontWeight.SemiBold) } },
        )
    }

    if (sharing) {
        ShareSheet(
            ShareRequest(album.name, album.photos),
            onSend = {
                if (!sharePhotos(context, album.photos, album.name)) {
                    vm.showToast(L.s("share_failed"), ToastKind.Error)
                }
                sharing = false
            },
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
                TextButton(onClick = {
                    Haptics.confirm(view)
                    confirmDelete = false
                    onBack()
                    vm.deleteAlbum(album.id)
                }) {
                    T(L.s("delete_album"), weight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { T(L.s("cancel"), weight = FontWeight.SemiBold) } },
        )
    }
}
