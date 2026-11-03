package com.njarrar.backthen.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.ReelLook
import com.njarrar.backthen.core.ReelMove
import com.njarrar.backthen.core.ReelOptions
import com.njarrar.backthen.core.ReelPlan
import com.njarrar.backthen.core.ReelSize
import com.njarrar.backthen.core.ReelSpeed
import com.njarrar.backthen.core.reelOrder
import com.njarrar.backthen.data.CAN_SAVE_TO_GALLERY
import com.njarrar.backthen.data.ReelExport
import com.njarrar.backthen.data.decodePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

enum class ReelTab { STAMP, MOVE, MORE }

private val ReelInk = ViewerInk
private val ReelBg = ViewerBg

/** Whether the phone asks for less motion (animations turned off in the phone's settings). */
fun reduceMotion(context: Context): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

private const val PREFS = "reel"

private fun loadOptions(context: Context): ReelOptions =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("options", null)
        ?.let { runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(ReelOptions.serializer(), it) }.getOrNull() } ?: ReelOptions()

private fun saveOptions(context: Context, o: ReelOptions) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("options", Json.encodeToString(ReelOptions.serializer(), o)).apply()
}

/**
 * The reel editor: the reel plays right away, the options below change it as it plays,
 * and Share makes the video on the phone and opens the share sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReelScreen(photos: List<Photo>, startTab: ReelTab, say: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduce = remember { reduceMotion(context) }
    val fonts = remember { ReelFonts(context) }
    var options by remember { mutableStateOf(loadOptions(context)) }
    var tab by remember { mutableStateOf(startTab) }
    var small by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var blurred by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var time by remember { mutableDoubleStateOf(0.0) }
    var playing by remember { mutableStateOf(true) }
    var making by remember { mutableStateOf<Float?>(null) }
    var makeJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }

    BackHandler { if (making != null) makeJob?.cancel() else onClose() }
    LightBars()

    LaunchedEffect(photos) {
        val loaded = withContext(Dispatchers.IO) {
            reelOrder(photos, true).map { p -> async { p.key to decodePhoto(context, Uri.parse(p.uri), 640) } }.awaitAll()
        }
        small = loaded.mapNotNull { (k, b) -> b?.let { k to it } }.toMap()
        blurred = withContext(Dispatchers.Default) { small.mapNotNull { (k, b) -> ReelRenderer.blur(b)?.let { k to it } }.toMap() }
    }

    fun renderer(o: ReelOptions): ReelRenderer {
        val ordered = reelOrder(photos, o.newestFirst)
        return ReelRenderer(ordered, o, ordered.map { small[it.key] }, fonts, reduce, ordered.map { blurred[it.key] })
    }

    val renderer = remember(options, blurred) { if (blurred.isEmpty()) null else renderer(options) }
    val total = renderer?.plan?.total ?: 1.0
    val totalNow by rememberUpdatedState(total)
    val looks: Map<ReelLook, ImageBitmap> = remember(options.newestFirst, options.speed, blurred) {
        if (blurred.isEmpty()) emptyMap() else ReelLook.entries.associateWith { look ->
            val r = renderer(options.copy(look = look, fill = false))
            val bmp = Bitmap.createBitmap(216, 384, Bitmap.Config.ARGB_8888)
            r.draw(android.graphics.Canvas(bmp), 216f, 384f, r.plan.items[1].start + 2)
            bmp.asImageBitmap()
        }
    }

    // Plays the reel, holds the end a moment, then starts over.
    LaunchedEffect(playing, making, renderer != null) {
        if (!playing || making != null || renderer == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val next = time + (now - last) / 1e9
            last = now
            time = if (next > totalNow + 0.9) 0.0 else next
        }
    }

    fun change(o: ReelOptions) {
        options = o
        saveOptions(context, o)
        playing = true
    }

    fun make(save: Boolean) {
        if (making != null) return
        val o = options
        val ordered = reelOrder(photos, o.newestFirst)
        val r = renderer(o)
        playing = false
        making = 0f
        makeJob = scope.launch {
            try {
                val file: File = ReelExport.make(
                    context, r,
                    load = { i -> withContext(Dispatchers.IO) { ordered.getOrNull(i)?.let { decodePhoto(context, Uri.parse(it.uri), ReelExport.PHOTO_SIDE) } } },
                    progress = { progress = it },
                )
                making = null
                if (save) {
                    say(L.s(if (ReelExport.saveToGallery(context, file)) "toast_reel_saved" else "reel_failed"))
                } else {
                    shareVideo(context, file, L.s("reel_title"))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                making = null
            } catch (e: Throwable) {
                making = null
                say(L.s("reel_failed"))
            }
        }
    }

    Box(Modifier.fillMaxSize().background(ReelBg).clickable(enabled = false) {}) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // Top bar
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L.s("close"), tint = ReelInk) }
                T(L.s("reel_title"), size = 17, weight = FontWeight.Bold, color = ReelInk, maxLines = 1, modifier = Modifier.weight(1f))
                if (CAN_SAVE_TO_GALLERY) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).clickable(enabled = renderer != null && making == null, role = Role.Button) { make(save = true) }
                            .heightIn(min = 44.dp).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { T(L.s("reel_save"), size = 15, weight = FontWeight.Bold, color = ReelInk, maxLines = 1) }
                }
                Box(
                    Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50)).background(ReelInk)
                        .clickable(enabled = renderer != null && making == null, role = Role.Button) { make(save = false) }
                        .heightIn(min = 40.dp).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { T(L.s("share"), size = 15, weight = FontWeight.Bold, color = ReelBg, maxLines = 1) }
            }

            // Player
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                val aspect = options.size.width.toFloat() / options.size.height
                val w = minOf(maxWidth, maxHeight * aspect)
                Box(
                    Modifier.width(w).aspectRatio(aspect).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                        .pointerInput(Unit) { detectTapGestures { playing = !playing } }
                        .semantics {
                            contentDescription = L.s("reel_preview")
                            stateDescription = L.s(if (playing) "reel_play" else "reel_pause")
                            onClick(L.s(if (playing) "reel_pause" else "reel_play")) { playing = !playing; true }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (renderer != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            val t = time
                            drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width, size.height, minOf(t, total)) }
                        }
                    } else {
                        CircularProgressIndicator(color = ReelInk, modifier = Modifier.size(32.dp))
                    }
                    if (!playing && making == null && renderer != null) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }

            // Scrubber
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(clock(minOf(time, total)), size = 12, weight = FontWeight.Bold, color = ReelInk.copy(alpha = 0.7f))
                Box(
                    Modifier.weight(1f).height(28.dp).padding(horizontal = 10.dp)
                        .pointerInput(total, rtl) {
                            detectHorizontalDragGestures { change, _ ->
                                val x = if (rtl) size.width - change.position.x else change.position.x
                                time = (x / size.width).toDouble().coerceIn(0.0, 1.0) * total
                                playing = false
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)))
                    Box(Modifier.fillMaxWidth((minOf(time, total) / total).toFloat().coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(ReelInk))
                }
                T(clock(total), size = 12, weight = FontWeight.Bold, color = ReelInk.copy(alpha = 0.7f))
            }

            // Tabs
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(ReelTab.STAMP to "reel_tab_stamp", ReelTab.MOVE to "reel_tab_transition", ReelTab.MORE to "more").forEach { (t, key) ->
                    val on = tab == t
                    Box(
                        Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable(role = Role.Tab) { tab = t }
                            .semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) { T(L.s(key), size = 14, weight = FontWeight.Bold, color = if (on) ReelInk else ReelInk.copy(alpha = 0.6f), align = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 4.dp)) }
                }
            }

            // Panel
            Column(Modifier.fillMaxWidth().height(200.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
                when (tab) {
                    ReelTab.STAMP -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(ReelLook.YEAR to "reel_look_year", ReelLook.FILM to "reel_look_film", ReelLook.CLEAN to "reel_look_clean").forEach { (look, key) ->
                            val on = options.look == look
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button) { change(options.copy(look = look)) }
                                    .semantics { selected = on }
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    Modifier.heightIn(max = 132.dp).aspectRatio(9f / 16f)
                                        .border(2.dp, if (on) ReelInk else Color.Transparent, RoundedCornerShape(12.dp))
                                        .padding(4.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f)),
                                ) {
                                    looks[look]?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                }
                                T(L.s(key), size = 13, weight = FontWeight.Bold, color = if (on) ReelInk else ReelInk.copy(alpha = 0.65f), align = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                    ReelTab.MOVE -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReelMove.entries.forEach { m ->
                                ReelChip(L.s("reel_move_${m.name.lowercase()}"), options.move == m) { change(options.copy(move = m)) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        T(L.s("reel_move_${options.move.name.lowercase()}_note"), size = 14, color = ReelInk.copy(alpha = 0.65f))
                    }
                    ReelTab.MORE -> Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        OptionRow("reel_order", listOf(true to L.s("reel_newest_first"), false to L.s("reel_oldest_first")), options.newestFirst) { change(options.copy(newestFirst = it)) }
                        OptionRow("reel_speed", ReelSpeed.entries.map { it to L.s("reel_speed_${it.name.lowercase()}") }, options.speed) { change(options.copy(speed = it)) }
                        OptionRow("reel_size", ReelSize.entries.map { it to it.label }, options.size) { change(options.copy(size = it)) }
                        OptionRow("reel_photo", listOf(false to L.s("reel_whole"), true to L.s("reel_fill")), options.fill) { change(options.copy(fill = it)) }
                        OptionRow("reel_end_card", listOf(true to L.s("reel_on"), false to L.s("reel_off")), options.endCard) { change(options.copy(endCard = it)) }
                    }
                }
            }
        }

        // Making the video
        if (making != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)).clickable(enabled = true, onClick = {}), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(24.dp)) {
                    T(L.s("reel_making"), size = 19, weight = FontWeight.ExtraBold, color = ReelInk, align = TextAlign.Center)
                    LinearProgressIndicator(progress = { progress }, color = ReelInk, trackColor = Color.White.copy(alpha = 0.15f), modifier = Modifier.width(200.dp))
                    T(L.s("reel_making_note"), size = 14, color = ReelInk.copy(alpha = 0.7f), align = TextAlign.Center, modifier = Modifier.widthIn(max = 260.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button) { makeJob?.cancel() }.heightIn(min = 44.dp).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { T(L.s("cancel"), size = 15, weight = FontWeight.Bold, color = ReelInk) }
                }
            }
        }
    }
}

@Composable
private fun ReelChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50))
            .background(if (on) ReelInk else Color.Transparent)
            .border(1.dp, if (on) Color.Transparent else Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { T(label, size = 14, weight = FontWeight.Bold, color = if (on) ReelBg else ReelInk, maxLines = 1) }
}

/** One option: its name, then its choices. The choices go under the name when they don't fit beside it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <V> OptionRow(key: String, choices: List<Pair<V, String>>, value: V, onPick: (V) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        T(L.s(key), size = 14, weight = FontWeight.Bold, color = ReelInk.copy(alpha = 0.8f), modifier = Modifier.padding(end = 8.dp))
        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.09f)).padding(2.dp)) {
            choices.forEach { (v, label) ->
                val on = v == value
                Box(
                    Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (on) ReelInk else Color.Transparent)
                        .clickable(role = Role.Button) { onPick(v) }
                        .semantics { selected = on }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { T(label, size = 13, weight = FontWeight.Bold, color = if (on) ReelBg else ReelInk.copy(alpha = 0.75f), maxLines = 1) }
            }
        }
    }
}

private fun clock(t: Double): String {
    val s = t.roundToInt()
    val sec = s % 60
    return "${L.num(s / 60)}:${if (sec < 10) L.num(0) else ""}${L.num(sec)}"
}

/** Light status bar icons on the dark editor, back to what they were when it closes. */
@Composable
private fun LightBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        var c = view.context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val bars = (c as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val was = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = false
        onDispose { if (bars != null && was != null) bars.isAppearanceLightStatusBars = was }
    }
}
