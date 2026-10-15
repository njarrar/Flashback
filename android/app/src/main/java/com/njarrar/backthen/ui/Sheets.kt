package com.njarrar.backthen.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
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
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.formatTime
import com.njarrar.backthen.core.plural
import com.njarrar.backthen.GoogleImport
import com.njarrar.backthen.data.SavedState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
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
            T("Add to album", size = 22, weight = FontWeight.Bold)
            T("${plural(photos.size, "photo")} selected", size = 14, muted = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("New album name", size = 13, weight = FontWeight.SemiBold, color = p.muted2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { T("e.g. Fall in the city", color = p.faint) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = p.ink),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = p.line, cursorColor = Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                    modifier = Modifier.weight(1f),
                )
                Pill("Create", create, height = 48.dp, enabled = name.isNotBlank())
            }
        }
        if (albums.isNotEmpty()) {
            Column {
                T("Or add to an existing album", size = 13, weight = FontWeight.SemiBold, color = p.muted2, modifier = Modifier.padding(bottom = 4.dp))
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
                                T(plural(a.photos.size, "photo"), size = 13, muted = true)
                            }
                            Icon(Icons.Filled.AddCircle, "Add to ${a.name}", tint = Accent)
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
    onConnectDevice: () -> Unit,
    onGoogle: () -> Unit,
    onReminder: (Boolean) -> Unit,
    onReminderTime: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    BottomSheet(onDismiss) {
        T("Photo sources", size = 13, weight = FontWeight.Bold, muted = true)
        SourceRow(Icons.Outlined.PhoneAndroid, "Photos on this phone", if (state.sources.device) "Connected" else "Not connected", state.sources.device, onConnectDevice)
        SourceRow(
            Icons.Outlined.PhotoLibrary, "Google Photos",
            if (state.sources.google) "${plural(state.googlePhotos.size, "photo")} picked" else "Not connected",
            state.sources.google, onGoogle,
        )
        HorizontalDivider(color = p.line)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Notifications, null, tint = p.ink)
            T("Daily reminder", weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (state.reminder.on) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(p.fill)
                        .clickable(role = Role.Button) {
                            val i = TIMES.indexOf(state.reminder.hour to state.reminder.minute)
                            val (h, m) = TIMES[(i + 1) % TIMES.size]
                            onReminderTime(h, m)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { T(formatTime(state.reminder.hour, state.reminder.minute), size = 15, weight = FontWeight.SemiBold) }
            }
            Switch(
                checked = state.reminder.on,
                onCheckedChange = onReminder,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White),
            )
        }
        T("Only on days that have photos from past years. Tap the time to change it.", size = 13, muted = true)
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

/** Explains what Google allows, runs the picker, or manages the link. */
@Composable
fun GoogleSheet(
    connected: Boolean,
    picked: Int,
    busy: GoogleImport?,
    onPick: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    BottomSheet(onDismiss) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(p.fill), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.PhotoLibrary, null, tint = p.ink)
        }
        if (busy != null) {
            T(if (busy.total > 0) "Saving your picks" else "Waiting for Google Photos", size = 24, weight = FontWeight.ExtraBold, tight = true)
            T(
                if (busy.total > 0) "${busy.done} of ${busy.total} photos" else "Pick photos in Google Photos, tap Done, then come back here.",
                size = 15, weight = FontWeight.Medium, muted = true,
            )
            LinearProgressIndicator(color = Accent, trackColor = p.fill, modifier = Modifier.fillMaxWidth())
            Pill("Cancel", onCancel, kind = Kind.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth())
        } else {
            T(if (connected) "Google Photos is connected" else "Add Google Photos", size = 24, weight = FontWeight.ExtraBold, tight = true)
            T(
                if (connected) "${plural(picked, "photo")} picked. Pick more any time."
                else "Google only lets apps see the photos you pick. Choose the ones you want Back Then to bring back, such as a whole year or a trip. Copies of shots already on this phone show once.",
                size = 15, weight = FontWeight.Medium, muted = true,
            )
            if (!connected) T("Photos backed up from this phone already show up through phone photos.", size = 13, muted = true)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(if (connected) "Pick more photos" else "Continue with Google", onPick, modifier = Modifier.fillMaxWidth())
                if (connected) Pill("Disconnect", onDisconnect, kind = Kind.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth())
                else Pill("Not now", onDismiss, kind = Kind.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Dark bar shown while picking photos, with Share and Album. */
@Composable
fun SelectBar(count: Int, onShare: () -> Unit, onAlbum: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val none = count == 0
    Row(
        modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(20.dp)).background(p.ink).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(if (none) "Tap photos to select" else "${plural(count, "photo")} selected", size = 15, weight = FontWeight.SemiBold, color = p.bg, modifier = Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(50)).clickable(enabled = !none, role = Role.Button, onClick = onShare).padding(horizontal = 14.dp, vertical = 12.dp)) {
            T("Share", size = 15, weight = FontWeight.SemiBold, color = p.bg.copy(alpha = if (none) 0.4f else 1f))
        }
        Pill("+ Album", onAlbum, kind = Kind.Accent, height = 44.dp, enabled = !none)
    }
}
