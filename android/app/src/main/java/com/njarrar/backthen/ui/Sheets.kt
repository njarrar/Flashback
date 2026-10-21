package com.njarrar.backthen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.label
import com.njarrar.backthen.data.YearLayout
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.njarrar.backthen.core.Album
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.formatTime
import com.njarrar.backthen.data.CAN_SAVE_TO_GALLERY
import com.njarrar.backthen.data.SavedState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    // Opens all the way, so nothing sits half hidden under the system buttons.
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = p.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).navigationBarsPadding(),
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
    var name by remember { mutableStateOf("") }
    val create = { if (name.isNotBlank()) { Haptics.confirm(view); onCreate(name.trim()) } }
    BottomSheet(onDismiss) {
        Column {
            T(L.s("add_to_album"), size = 22, weight = FontWeight.Bold)
            T(L.s("n_selected", "photos" to L.p("photos", photos.size)), size = 14, muted = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(L.s("new_album_name"), size = 13, weight = FontWeight.SemiBold, color = p.muted2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { T(L.s("album_name_example"), color = p.faint) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = p.ink),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = p.line, cursorColor = Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    modifier = Modifier.weight(1f),
                )
                Pill(L.s("create"), create, height = 48.dp, enabled = name.isNotBlank())
            }
        }
        if (albums.isNotEmpty()) {
            Column {
                T(L.s("add_to_existing"), size = 13, weight = FontWeight.SemiBold, color = p.muted2, modifier = Modifier.padding(bottom = 4.dp))
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(albums, key = { it.id }) { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { Haptics.confirm(view); onAdd(a) }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                a.photos.firstOrNull()?.uri, null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(p.fill),
                            )
                            Column(Modifier.weight(1f)) {
                                T(a.name, weight = FontWeight.SemiBold, maxLines = 1)
                                T(L.p("photos", a.photos.size), size = 13, muted = true)
                            }
                            Icon(Icons.Filled.AddCircle, L.s("add_to_named", "name" to a.name), tint = Accent)
                        }
                    }
                }
            }
        }
    }
}

private val TIMES = listOf(8 to 0, 9 to 0, 12 to 0, 18 to 0, 20 to 0)

/** Photo sources and the daily reminder. Opened from the overflow button. */
@Composable
fun SettingsSheet(
    state: SavedState,
    day: MonthDay,
    onConnectDevice: () -> Unit,
    onReminder: (Boolean) -> Unit,
    onReminderTime: (Int, Int) -> Unit,
    onLayout: (YearLayout) -> Unit,
    onHidden: (MonthDay, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onPlaces: ((Boolean) -> Unit)? = null,
) {
    val p = LocalPalette.current
    BottomSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            T(L.s("photo_sources"), size = 13, weight = FontWeight.Bold, muted = true)
            SourceRow(Icons.Outlined.PhoneAndroid, L.s("phone_photos"), L.s(if (state.sources.device) "connected" else "not_connected"), state.sources.device, onConnectDevice)
            HorizontalDivider(color = p.line)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Notifications, null, tint = p.ink)
                T(L.s("daily_reminder"), weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (state.reminder.on) {
                    // The chip looks small, but the tap area is the full 48 dp tall.
                    Box(
                        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button) {
                                val i = TIMES.indexOf(state.reminder.hour to state.reminder.minute)
                                val (h, m) = TIMES[(i + 1) % TIMES.size]
                                onReminderTime(h, m)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.clip(RoundedCornerShape(50)).background(p.fill).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            T(timeLabel(state.reminder.hour, state.reminder.minute), size = 15, weight = FontWeight.SemiBold)
                        }
                    }
                }
                Switch(
                    checked = state.reminder.on,
                    onCheckedChange = onReminder,
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White),
                )
            }
            T(L.s("reminder_settings_note"), size = 13, muted = true)
            HorizontalDivider(color = p.line)
            if (onPlaces != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Place, null, tint = p.ink)
                    T(L.s("places_title"), weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.places.on,
                        onCheckedChange = onPlaces,
                        colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White),
                    )
                }
                T(L.s("places_note"), size = 13, muted = true)
                HorizontalDivider(color = p.line)
            }
            T(L.s("layout"), size = 13, weight = FontWeight.Bold, muted = true)
            // Stacked, not side by side: three long names don't fit in one row.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.fill).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    YearLayout.MOSAIC to L.s("layout_all_years"),
                    YearLayout.STRIP to L.s("layout_one_year"),
                    YearLayout.CARDS to L.s("layout_cards"),
                ).forEach { (layout, label) ->
                    val on = state.layout == layout
                    Box(
                        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(50))
                            .background(if (on) p.surface else Color.Transparent)
                            .clickable(role = Role.RadioButton) { onLayout(layout) },
                        contentAlignment = Alignment.Center,
                    ) { T(label, size = 14, weight = FontWeight.SemiBold, color = if (on) p.ink else p.muted) }
                }
            }
            HorizontalDivider(color = p.line)
            val hidden = day.key in state.hiddenDates
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onHidden(day, !hidden) }.padding(vertical = 4.dp),
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
                        Pill(L.s("show"), { onHidden(md, false) }, kind = Kind.Secondary, height = 36.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, status: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = p.ink)
        Column(Modifier.weight(1f)) {
            T(title, weight = FontWeight.SemiBold)
            T(status, size = 13, weight = FontWeight.Medium, color = if (on) p.muted else Accent)
        }
        Icon(if (on) Icons.Filled.Check else Icons.Filled.AddCircle, null, tint = if (on) p.muted else Accent, modifier = Modifier.size(20.dp))
    }
}

/** Dark bar shown while picking photos, with Share and Album. */
@Composable
fun SelectBar(count: Int, onShare: () -> Unit, onAlbum: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val none = count == 0
    Row(
        modifier.fillMaxWidth().heightIn(min = 62.dp).clip(RoundedCornerShape(20.dp)).background(p.ink).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(if (none) L.s("select_hint") else L.s("n_selected", "photos" to L.p("photos", count)), size = 15, weight = FontWeight.SemiBold, color = p.bg, modifier = Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(50)).clickable(enabled = !none, role = Role.Button, onClick = onShare).padding(horizontal = 14.dp, vertical = 12.dp)) {
            T(L.s("share"), size = 15, weight = FontWeight.SemiBold, color = p.bg.copy(alpha = if (none) 0.4f else 1f))
        }
        Pill(
            L.s("album"), onAlbum, kind = Kind.Accent, height = 44.dp, enabled = !none,
            icon = { Icon(Icons.Filled.Add, null, tint = it, modifier = Modifier.size(16.dp)) },
        )
    }
}

/** What to share: a year, a selection, or an album. */
data class ShareRequest(val title: String, val photos: List<Photo>)

/** The design's share sheet: a preview, then send through any app or save copies. */
@Composable
fun ShareSheet(request: ShareRequest, onSend: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    BottomSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp)) {
                request.photos.take(3).withIndex().reversed().forEach { (i, ph) ->
                    AsyncImage(
                        ph.uri, null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .offset((i * 6).dp, (i * 5).dp)
                            .size(52.dp)
                            .rotate(listOf(-6f, 4f, -1f)[i])
                            .clip(RoundedCornerShape(10.dp))
                            .background(p.fill)
                            .border(2.dp, p.surface, RoundedCornerShape(10.dp)),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                T(request.title, size = 18, weight = FontWeight.Bold, maxLines = 2)
                T(L.p("photos", request.photos.size), size = 14, muted = true)
            }
        }
        Pill(L.s("share"), onSend, modifier = Modifier.fillMaxWidth(), icon = { Icon(Icons.Outlined.IosShare, null, tint = it, modifier = Modifier.size(18.dp)) })
        if (CAN_SAVE_TO_GALLERY) {
            HorizontalDivider(color = p.line)
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onSave).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.Download, null, tint = p.ink)
                T(L.s("save_copies_gallery"), weight = FontWeight.Medium)
            }
        }
    }
}
