package com.njarrar.backthen.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.City
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.label
import com.njarrar.backthen.data.CAN_SAVE_TO_GALLERY
import com.njarrar.backthen.data.SavedState
import com.njarrar.backthen.data.YearLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    // Opens all the way and lifts above the IME keyboard so inputs and lists stay reachable.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

/** Make a new album from the chosen photos, or add them to one that exists. */
@Composable
fun AlbumSheet(
    photos: List<Photo>,
    albums: List<Album>,
    onCreate: (String) -> Unit,
    onAdd: (Album) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    val view = LocalView.current
    val largeFont = LocalDensity.current.fontScale >= 1.35f
    var name by rememberSaveable { mutableStateOf("") }
    val create = { if (name.isNotBlank()) { Haptics.confirm(view); onCreate(name.trim()) } }

    BottomSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(L.s("add_to_album"), size = 22, weight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                T(L.s("n_selected", "photos" to L.p("photos", photos.size)), size = 14, muted = true)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, L.s("close"), tint = p.muted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(L.s("new_album_name"), size = 13, weight = FontWeight.SemiBold, color = p.muted2)
            val inputField: @Composable (Modifier) -> Unit = { mod ->
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { T(L.s("album_name_example"), color = p.muted) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = p.ink),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = p.line, cursorColor = Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    modifier = mod,
                )
            }
            if (largeFont) {
                inputField(Modifier.fillMaxWidth())
                Pill(L.s("create"), create, height = 48.dp, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    inputField(Modifier.weight(1f))
                    Pill(L.s("create"), create, height = 48.dp, enabled = name.isNotBlank())
                }
            }
        }
        if (albums.isNotEmpty()) {
            Column {
                T(L.s("add_to_existing"), size = 13, weight = FontWeight.SemiBold, color = p.muted2, modifier = Modifier.padding(bottom = 4.dp))
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(albums, key = { it.id }) { a ->
                        val req = rememberThumbnailRequest(a.photos.firstOrNull()?.uri, maxPx = 160, crossfade = false)
                        val rowDesc = "${L.s("add_to_named", "name" to a.name)}, ${L.p("photos", a.photos.size)}"
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(role = Role.Button) { Haptics.confirm(view); onAdd(a) }
                                .clearAndSetSemantics { contentDescription = rowDesc }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                req,
                                null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(p.fill),
                            )
                            Column(Modifier.weight(1f)) {
                                T(a.name, weight = FontWeight.SemiBold, maxLines = 1)
                                T(L.p("photos", a.photos.size), size = 13, muted = true)
                            }
                            Icon(Icons.Filled.AddCircle, null, tint = Accent)
                        }
                    }
                }
            }
        }
    }
}

/** Photo sources, daily reminder, places, layout, and hidden dates. Opened from the overflow menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: SavedState,
    day: MonthDay,
    partialAccess: Boolean = false,
    notificationsBlocked: Boolean = false,
    placesScanning: Boolean = false,
    activeHomeId: Int? = null,
    homeCityName: String? = null,
    visitedCities: List<City> = emptyList(),
    onConnectDevice: () -> Unit,
    onManagePhotos: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
    onReminder: (Boolean) -> Unit,
    onReminderTime: (Int, Int) -> Unit,
    onLayout: (YearLayout) -> Unit,
    onHidden: (MonthDay, Boolean) -> Unit,
    onHomeCity: ((Int?) -> Unit)? = null,
    onDismiss: () -> Unit,
    onPlaces: ((Boolean) -> Unit)? = null,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var pickingHome by rememberSaveable { mutableStateOf(false) }

    BottomSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(L.s("settings"), size = 22, weight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, L.s("close"), tint = p.muted)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            T(L.s("photo_sources"), size = 13, weight = FontWeight.Bold, muted = true)
            val statusText = when {
                partialAccess -> L.s("limited_access")
                state.sources.device -> L.s("connected")
                else -> L.s("not_connected")
            }
            SourceRow(
                icon = Icons.Outlined.PhoneAndroid,
                title = L.s("phone_photos"),
                status = statusText,
                connected = state.sources.device && !partialAccess,
                clickable = !state.sources.device || partialAccess,
                onClick = { if (partialAccess && onManagePhotos != null) onManagePhotos() else onConnectDevice() },
            )
            if (partialAccess) {
                T(L.s("limited_access_note"), size = 13, muted = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onManagePhotos != null) {
                        Pill(L.s("manage_photos"), onManagePhotos, kind = Kind.Secondary, height = 40.dp)
                    }
                    if (onOpenSettings != null) {
                        Pill(L.s("open_settings"), onOpenSettings, kind = Kind.Ghost, height = 40.dp)
                    }
                }
            } else if (!state.sources.device && onOpenSettings != null) {
                Row {
                    Pill(L.s("open_settings"), onOpenSettings, kind = Kind.Secondary, height = 40.dp)
                }
            }

            HorizontalDivider(color = p.line)

            // Daily reminder row: merged Switch semantics + separate accessible time picker chip.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .toggleable(value = state.reminder.on, role = Role.Switch, onValueChange = onReminder)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Notifications, null, tint = p.ink)
                    T(L.s("daily_reminder"), weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.reminder.on,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentFill, checkedThumbColor = Color.White),
                    )
                }
            }
            if (state.reminder.on) {
                val formattedTime = timeLabel(state.reminder.hour, state.reminder.minute)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { pickingTime = true }
                        .semantics { contentDescription = "${L.s("change_reminder_time")}, $formattedTime" }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(L.s("change_reminder_time"), size = 14, weight = FontWeight.Medium, muted = true, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(p.fill).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        T(formattedTime, size = 15, weight = FontWeight.SemiBold)
                    }
                }
            }
            if (state.reminder.on && notificationsBlocked) {
                T(L.s("toast_notifications_off"), size = 13, weight = FontWeight.Medium, color = p.accInk)
                if (onOpenNotificationSettings != null) {
                    Row {
                        Pill(L.s("open_settings"), onOpenNotificationSettings, kind = Kind.Secondary, height = 40.dp)
                    }
                }
            }
            T(L.s("reminder_settings_note"), size = 13, muted = true)

            HorizontalDivider(color = p.line)

            if (onPlaces != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .toggleable(value = state.places.on, role = Role.Switch, onValueChange = onPlaces)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Place, null, tint = p.ink)
                    T(L.s("places_title"), weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.places.on,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentFill, checkedThumbColor = Color.White),
                    )
                }
                if (placesScanning) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        T(L.s("places_scanning"), size = 13, muted = true)
                    }
                }
                if (state.places.on && onHomeCity != null && (homeCityName != null || visitedCities.isNotEmpty())) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = visitedCities.isNotEmpty(), role = Role.Button) { pickingHome = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Home, null, tint = p.muted2)
                        Column(Modifier.weight(1f)) {
                            T(L.s("home_city"), size = 14, weight = FontWeight.SemiBold)
                            if (!homeCityName.isNullOrBlank()) {
                                T(homeCityName, size = 13, muted = true)
                            }
                        }
                        if (visitedCities.isNotEmpty()) {
                            T(L.s("change"), size = 14, weight = FontWeight.SemiBold, color = Accent)
                        }
                    }
                }
                T(L.s("places_note"), size = 13, muted = true)
                HorizontalDivider(color = p.line)
            }

            T(L.s("layout"), size = 13, weight = FontWeight.Bold, muted = true)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(p.fill)
                    .padding(4.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    YearLayout.MOSAIC to L.s("layout_all_years"),
                    YearLayout.STRIP to L.s("layout_one_year"),
                    YearLayout.CARDS to L.s("layout_cards"),
                ).forEach { (layout, label) ->
                    val on = state.layout == layout
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (on) p.surface else Color.Transparent)
                            .selectable(selected = on, role = Role.RadioButton) { onLayout(layout) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        T(label, size = 14, weight = FontWeight.SemiBold, color = if (on) p.ink else p.muted2)
                    }
                }
            }

            HorizontalDivider(color = p.line)

            val hidden = day.key in state.hiddenDates
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onHidden(day, !hidden) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(if (hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null, tint = p.ink)
                Column(Modifier.weight(1f)) {
                    T(L.s(if (hidden) "show_day_again" else "hide_day", "day" to day.label()), weight = FontWeight.SemiBold)
                    T(L.s("hidden_note"), size = 13, muted = true)
                }
            }
            val others = state.hiddenDates.mapNotNull { MonthDay.fromKey(it) }.filter { it != day }.sortedWith(compareBy({ it.month }, { it.day }))
            if (others.isNotEmpty()) {
                T(L.s("hidden_dates"), size = 13, weight = FontWeight.Bold, muted = true)
                others.forEach { md ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(md.label(), weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Pill(L.s("show"), { onHidden(md, false) }, kind = Kind.Secondary, height = 40.dp)
                    }
                }
            }
            HorizontalDivider(color = p.line)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(L.s("licenses"), size = 13, weight = FontWeight.Bold, muted = true)
                T("GeoNames (CC BY 4.0) · Nunito & Tajawal (SIL OFL)", size = 12, muted = true)
            }
        }
    }

    if (pickingTime) {
        val is24Hour = DateFormat.is24HourFormat(context)
        val pickerState = rememberTimePickerState(
            initialHour = state.reminder.hour,
            initialMinute = state.reminder.minute,
            is24Hour = is24Hour,
        )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            containerColor = p.surface,
            title = { T(L.s("change_reminder_time"), size = 20, weight = FontWeight.Bold) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onReminderTime(pickerState.hour, pickerState.minute)
                    pickingTime = false
                }) { T(L.s("save"), weight = FontWeight.SemiBold, color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) { T(L.s("cancel"), weight = FontWeight.SemiBold) }
            },
        )
    }

    if (pickingHome && onHomeCity != null) {
        AlertDialog(
            onDismissRequest = { pickingHome = false },
            containerColor = p.surface,
            title = { T(L.s("home_city"), size = 20, weight = FontWeight.Bold) },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(visitedCities, key = { it.id }) { city ->
                        val selectedCity = if (activeHomeId != null) {
                            activeHomeId == city.id
                        } else {
                            state.customHomeCity == city.id || (state.customHomeCity == null && homeCityName == city.name)
                        }
                        val country = countryName(city.country)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .selectable(selected = selectedCity, role = Role.RadioButton) {
                                    onHomeCity(city.id)
                                    pickingHome = false
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                T(city.name, size = 15, weight = if (selectedCity) FontWeight.Bold else FontWeight.Medium)
                                if (country.isNotBlank()) T(country, size = 12, muted = true)
                            }
                            if (selectedCity) Icon(Icons.Filled.Check, null, tint = Accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingHome = false }) { T(L.s("close"), weight = FontWeight.SemiBold) }
            },
        )
    }
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, status: String, connected: Boolean, clickable: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (clickable) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = p.ink)
        Column(Modifier.weight(1f)) {
            T(title, weight = FontWeight.SemiBold)
            T(status, size = 13, weight = FontWeight.Medium, color = if (connected) p.muted else p.accInk)
        }
        Icon(if (connected) Icons.Filled.Check else Icons.Filled.AddCircle, null, tint = if (connected) p.muted else Accent, modifier = Modifier.size(20.dp))
    }
}

/** Floating dark capsule bar shown while picking photos, with Share, Reel, and Album actions. */
@Composable
fun SelectBar(count: Int, onShare: () -> Unit, onAlbum: () -> Unit, modifier: Modifier = Modifier, onReel: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val none = count == 0
    val compactReel = LocalDensity.current.fontScale >= 1.2f || L.lang.arabicScript || LocalConfiguration.current.screenWidthDp < 380
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(p.ink)
            .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        T(
            if (none) L.s("select_hint") else L.s("n_selected", "photos" to L.p("photos", count)),
            size = 15,
            weight = FontWeight.SemiBold,
            color = p.bg,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(enabled = !none, role = Role.Button, onClick = onShare)
                .semantics { contentDescription = L.s("share") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Share, null, tint = p.bg.copy(alpha = if (none) 0.4f else 1f), modifier = Modifier.size(20.dp))
        }
        if (onReel != null) {
            val off = count < 2
            Row(
                Modifier
                    .minimumInteractiveComponentSize()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = !off, role = Role.Button, onClick = onReel)
                    .semantics { contentDescription = L.s("make_reel") }
                    .padding(horizontal = if (compactReel) 12.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.Movie, null, tint = p.bg.copy(alpha = if (off) 0.4f else 1f), modifier = Modifier.size(20.dp))
                if (!compactReel) {
                    T(L.s("reel"), size = 15, weight = FontWeight.SemiBold, color = p.bg.copy(alpha = if (off) 0.4f else 1f), maxLines = 1)
                }
            }
        }
        Pill(
            L.s("album"),
            onAlbum,
            kind = Kind.Accent,
            height = 44.dp,
            enabled = !none,
            icon = { Icon(Icons.Filled.Add, null, tint = it, modifier = Modifier.size(16.dp)) },
        )
    }
}

/** What to share: a year, a selection, or an album. */
data class ShareRequest(val title: String, val photos: List<Photo>)

/** The design's share sheet: a preview, close button, then send through any app or save copies. */
@Composable
fun ShareSheet(request: ShareRequest, onSend: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    BottomSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.size(64.dp).clearAndSetSemantics {}) {
                        request.photos.take(3).withIndex().reversed().forEach { (i, ph) ->
                            val req = rememberThumbnailRequest(ph.uri, maxPx = 160, crossfade = false)
                            val shape = RoundedCornerShape(10.dp)
                            AsyncImage(
                                req,
                                null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .offset((i * 6).dp, (i * 5).dp)
                                    .size(52.dp)
                                    .rotate(listOf(-6f, 4f, -1f)[i])
                                    .shadow(4.dp, shape)
                                    .clip(shape)
                                    .background(p.fill)
                                    .border(2.dp, p.surface, shape),
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    T(request.title, size = 18, weight = FontWeight.Bold, maxLines = 2)
                    T(L.p("photos", request.photos.size), size = 14, muted = true)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, L.s("close"), tint = p.muted)
                }
            }
            Pill(L.s("share"), onSend, modifier = Modifier.fillMaxWidth(), icon = { Icon(Icons.Outlined.Share, null, tint = it, modifier = Modifier.size(18.dp)) })
            if (CAN_SAVE_TO_GALLERY) {
                HorizontalDivider(color = p.line)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onSave)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Download, null, tint = p.ink)
                    T(L.s("save_copies_gallery"), weight = FontWeight.Medium)
                }
            }
        }
    }
}
