package com.njarrar.backthen.ui

import android.content.Context
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.njarrar.backthen.ToastKind
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.ReelLook
import com.njarrar.backthen.core.ReelMove
import com.njarrar.backthen.core.ReelOptions
import com.njarrar.backthen.core.ReelPlan
import com.njarrar.backthen.core.ReelSize
import com.njarrar.backthen.core.ReelSpeed
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.reelOrder
import com.njarrar.backthen.data.CAN_SAVE_TO_GALLERY
import com.njarrar.backthen.data.ReelExport
import com.njarrar.backthen.data.decodePhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

enum class ReelTab { STAMP, MOVE, MORE, PHOTOS }

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
 * The reel editor: the reel plays right away, the options below (or beside in landscape)
 * change it as it plays, and Share makes the video on the phone and opens the share sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReelScreen(
    photos: List<Photo>,
    startTab: ReelTab,
    onToast: (String, ToastKind) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val fonts = remember { ReelFonts(context) }
    var options by remember { mutableStateOf(loadOptions(context)) }
    var tab by remember { mutableStateOf(startTab) }
    // Editable list of photos so the user can reorder or remove items in the Photos tab.
    var customOrder by remember(photos) { mutableStateOf<List<Photo>?>(null) }
    val ordered = remember(photos, options.newestFirst, customOrder) {
        (customOrder ?: reelOrder(photos, options.newestFirst)).take(ReelPlan.MAX_PHOTOS)
    }

    var small by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var blurred by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var looks by remember { mutableStateOf<Map<ReelLook, Pair<Bitmap, ImageBitmap>>>(emptyMap()) }
    // Playback time kept in a state object read ONLY by leaf Canvas/Scrubber (0 root recompositions/sec).
    val timeState = remember { mutableDoubleStateOf(0.0) }
    var playing by remember { mutableStateOf(true) }
    var exportProgress by remember { mutableStateOf<Float?>(null) }
    var makeJob by remember { mutableStateOf<Job?>(null) }

    BackHandler { if (exportProgress != null) makeJob?.cancel() else onClose() }
    DarkSystemBars()

    // Cancel any running export and recycle all decoded bitmaps when closing the editor.
    val smallRef = rememberUpdatedState(small)
    val blurredRef = rememberUpdatedState(blurred)
    val looksRef = rememberUpdatedState(looks)
    DisposableEffect(Unit) {
        onDispose {
            makeJob?.cancel()
            smallRef.value.values.forEach { if (!it.isRecycled) it.recycle() }
            blurredRef.value.values.forEach { if (!it.isRecycled) it.recycle() }
            looksRef.value.values.forEach { (bmp, _) -> if (!bmp.isRecycled) bmp.recycle() }
        }
    }

    // Re-keyed on ordered (covers both photos & options.newestFirst) with bounded parallelism (4).
    val decodeDispatcher = remember { Dispatchers.IO.limitedParallelism(4) }
    LaunchedEffect(ordered) {
        val neededKeys = ordered.map { it.key }.toSet()
        val missing = ordered.filter { it.key !in small }
        if (missing.isNotEmpty()) {
            val loaded = withContext(decodeDispatcher) {
                coroutineScope {
                    missing.map { p -> async { p.key to decodePhoto(context, Uri.parse(p.uri), 640) } }.awaitAll()
                }
            }
            val addedSmall = loaded.mapNotNull { (k, b) -> b?.let { k to it } }.toMap()
            val addedBlur = withContext(Dispatchers.Default) {
                addedSmall.mapNotNull { (k, b) -> ReelRenderer.blur(b)?.let { k to it } }.toMap()
            }
            small = small + addedSmall
            blurred = blurred + addedBlur
        }
        // Keep time clamped when photos are removed or reordered.
        if (neededKeys.isEmpty()) timeState.doubleValue = 0.0
    }

    fun buildRenderer(o: ReelOptions, list: List<Photo>, smallMap: Map<String, Bitmap>, blurMap: Map<String, Bitmap>): ReelRenderer =
        ReelRenderer(list, o, list.map { smallMap[it.key] }, fonts, reduce, list.map { blurMap[it.key] })

    val ready = ordered.isNotEmpty() && ordered.any { it.key in blurred }
    val renderer = remember(options, ordered, small, blurred, reduce) {
        if (!ready) null else buildRenderer(options, ordered, small, blurred)
    }
    val total = renderer?.plan?.total ?: 1.0
    val totalNow by rememberUpdatedState(total)

    // Render look preview thumbnails asynchronously on Dispatchers.Default (never on main thread during composition).
    LaunchedEffect(ordered, blurred, reduce) {
        if (!ready) return@LaunchedEffect
        val snapshotSmall = small
        val snapshotBlur = blurred
        val baseOpts = options
        val generated = withContext(Dispatchers.Default) {
            ReelLook.entries.associateWith { look ->
                val r = buildRenderer(baseOpts.copy(look = look, fill = false), ordered, snapshotSmall, snapshotBlur)
                val bmp = Bitmap.createBitmap(216, 384, Bitmap.Config.ARGB_8888)
                val sampleTime = r.plan.items.getOrNull(1)?.let { it.start + minOf(2.0, it.length * 0.5) } ?: 0.0
                r.draw(android.graphics.Canvas(bmp), 216f, 384f, sampleTime)
                bmp to bmp.asImageBitmap()
            }
        }
        val old = looks
        looks = generated
        old.values.forEach { (bmp, _) -> if (!bmp.isRecycled) bmp.recycle() }
    }

    // Plays the reel, holds the end a moment, then starts over. Mutates timeState without recomposing ReelScreen.
    LaunchedEffect(playing, exportProgress != null, renderer != null) {
        if (!playing || exportProgress != null || renderer == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val next = timeState.doubleValue + (now - last) / 1e9
            last = now
            timeState.doubleValue = if (next > totalNow + 0.9) 0.0 else next
        }
    }

    fun change(o: ReelOptions) {
        if (o.newestFirst != options.newestFirst) customOrder = null
        options = o
        saveOptions(context, o)
        playing = true
    }

    fun make(save: Boolean) {
        if (exportProgress != null || ordered.size < 2) return
        val o = options
        val list = ordered
        val r = buildRenderer(o, list, small, blurred)
        playing = false
        exportProgress = 0f
        makeJob = scope.launch {
            try {
                val file: File = ReelExport.make(
                    context,
                    r,
                    load = { i -> withContext(Dispatchers.IO) { list.getOrNull(i)?.let { decodePhoto(context, Uri.parse(it.uri), ReelExport.PHOTO_SIDE) } } },
                    progress = { p -> exportProgress = p.coerceIn(0f, 1f) },
                )
                exportProgress = null
                if (save) {
                    val savedOk = ReelExport.saveToGallery(context, file)
                    onToast(
                        L.s(if (savedOk) "toast_reel_saved" else "reel_failed"),
                        if (savedOk) ToastKind.Success else ToastKind.Error,
                    )
                } else {
                    if (!shareVideo(context, file, L.s("reel_title"))) {
                        onToast(L.s("share_failed"), ToastKind.Error)
                    }
                }
            } catch (_: CancellationException) {
                exportProgress = null
            } catch (_: Throwable) {
                exportProgress = null
                onToast(L.s("reel_failed"), ToastKind.Error)
            }
        }
    }

    val canExport = renderer != null && exportProgress == null && ordered.size >= 2

    Box(
        Modifier
            .fillMaxSize()
            .background(ReelBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // Top bar
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, L.s("close"), tint = ReelInk)
                }
                T(L.s("reel_title"), size = 17, weight = FontWeight.Bold, color = ReelInk, maxLines = 1, modifier = Modifier.weight(1f))
                if (CAN_SAVE_TO_GALLERY) {
                    Box(
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .clickable(enabled = canExport, role = Role.Button) { make(save = true) }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp)
                            .graphicsLayer { alpha = if (canExport) 1f else 0.4f },
                        contentAlignment = Alignment.Center,
                    ) { T(L.s("reel_save"), size = 15, weight = FontWeight.Bold, color = ReelInk, maxLines = 1) }
                }
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = canExport, role = Role.Button) { make(save = false) }
                        .graphicsLayer { alpha = if (canExport) 1f else 0.4f },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(50))
                            .background(ReelInk)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        T(L.s("share"), size = 15, weight = FontWeight.Bold, color = ReelBg, maxLines = 1)
                    }
                }
            }

            // Adaptive body: side-by-side 2-pane Row in landscape / tablets, vertical Column in portrait.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val wide = maxWidth > maxHeight
                val panelHeight = (maxHeight * 0.32f).coerceIn(200.dp, 260.dp)

                val playerSection: @Composable (Modifier) -> Unit = { mod ->
                    Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
                        BoxWithConstraints(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val aspect = options.size.width.toFloat() / options.size.height
                            val w = minOf(maxWidth, maxHeight * aspect)
                            Box(
                                Modifier
                                    .width(w)
                                    .aspectRatio(aspect)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .pointerInput(Unit) { detectTapGestures { playing = !playing } }
                                    .semantics {
                                        contentDescription = L.s("reel_preview")
                                        stateDescription = L.s(if (playing) "reel_playing" else "reel_paused")
                                        onClick(L.s(if (playing) "reel_pause" else "reel_play")) { playing = !playing; true }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (renderer != null) {
                                    ReelPlayerCanvas(
                                        renderer = renderer,
                                        total = total,
                                        timeProvider = { timeState.doubleValue },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    CircularProgressIndicator(color = ReelInk, modifier = Modifier.size(32.dp))
                                }
                                if (!playing && exportProgress == null && renderer != null) {
                                    Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                                    }
                                }
                            }
                        }
                        ReelScrubber(
                            timeProvider = { timeState.doubleValue },
                            total = total,
                            onSeek = { target ->
                                timeState.doubleValue = target.coerceIn(0.0, total)
                                playing = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                        )
                    }
                }

                val controlsSection: @Composable (Modifier) -> Unit = { mod ->
                    val panelScroll = rememberScrollState()
                    Column(mod) {
                        // Tabs
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(
                                ReelTab.STAMP to "reel_tab_stamp",
                                ReelTab.MOVE to "reel_tab_transition",
                                ReelTab.MORE to "more",
                                ReelTab.PHOTOS to "reel_photo",
                            ).forEach { (t, key) ->
                                val on = tab == t
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(role = Role.Tab) { tab = t }
                                        .semantics { selected = on },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (on) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        T(L.s(key), size = 13, weight = FontWeight.Bold, color = if (on) ReelInk else ReelInk.copy(alpha = 0.65f), maxLines = 1)
                                    }
                                }
                            }
                        }

                        // Scrollable Panel with bottom scroll fade indicator
                        Box(if (wide) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().height(panelHeight)) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(panelScroll)
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                when (tab) {
                                    ReelTab.STAMP -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        listOf(
                                            ReelLook.YEAR to "reel_look_year",
                                            ReelLook.FILM to "reel_look_film",
                                            ReelLook.CLEAN to "reel_look_clean",
                                        ).forEach { (look, key) ->
                                            val on = options.look == look
                                            Column(
                                                Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable(role = Role.Button) { change(options.copy(look = look)) }
                                                    .semantics { selected = on }
                                                    .padding(4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Box(
                                                    Modifier
                                                        .heightIn(max = 132.dp)
                                                        .aspectRatio(9f / 16f)
                                                        .border(2.dp, if (on) ReelInk else Color.Transparent, RoundedCornerShape(12.dp))
                                                        .padding(4.dp)
                                                        .clip(RoundedCornerShape(9.dp))
                                                        .background(Color.White.copy(alpha = 0.08f)),
                                                ) {
                                                    looks[look]?.second?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                                }
                                                T(L.s(key), size = 13, weight = FontWeight.Bold, color = if (on) ReelInk else ReelInk.copy(alpha = 0.7f), align = TextAlign.Center, maxLines = 2)
                                            }
                                        }
                                    }
                                    ReelTab.MOVE -> {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            ReelMove.entries.forEach { m ->
                                                ReelChip(L.s("reel_move_${m.name.lowercase()}"), options.move == m) { change(options.copy(move = m)) }
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        T(L.s("reel_move_${options.move.name.lowercase()}_note"), size = 14, color = ReelInk.copy(alpha = 0.72f))
                                        if (reduce && options.move == ReelMove.FLASH) {
                                            Spacer(Modifier.height(6.dp))
                                            T(L.s("reel_reduce_motion_note"), size = 13, color = ViewerHeart)
                                        }
                                    }
                                    ReelTab.MORE -> Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                                        OptionRow("reel_order", listOf(true to L.s("reel_newest_first"), false to L.s("reel_oldest_first")), options.newestFirst) { change(options.copy(newestFirst = it)) }
                                        OptionRow("reel_speed", ReelSpeed.entries.map { it to L.s("reel_speed_${it.name.lowercase()}") }, options.speed) { change(options.copy(speed = it)) }
                                        OptionRow("reel_size", ReelSize.entries.map { it to ltrIsolate(it.label) }, options.size) { change(options.copy(size = it)) }
                                        OptionRow("reel_photo", listOf(false to L.s("reel_whole"), true to L.s("reel_fill")), options.fill) { change(options.copy(fill = it)) }
                                        OptionRow("reel_end_card", listOf(true to L.s("reel_on"), false to L.s("reel_off")), options.endCard) { change(options.copy(endCard = it)) }
                                    }
                                    ReelTab.PHOTOS -> Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                                        T(L.p("photos", ordered.size), size = 13, weight = FontWeight.SemiBold, color = ReelInk.copy(alpha = 0.75f))
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(end = 8.dp),
                                        ) {
                                            itemsIndexed(ordered, key = { _, ph -> ph.key }) { idx, ph ->
                                                val req = rememberThumbnailRequest(ph.uri, maxPx = 220, crossfade = false)
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                ) {
                                                    Box(Modifier.size(72.dp, 90.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f))) {
                                                        AsyncImage(req, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                        if (ordered.size > 2) {
                                                            Box(
                                                                Modifier
                                                                    .align(Alignment.TopEnd)
                                                                    .size(32.dp)
                                                                    .clickable(role = Role.Button) {
                                                                        customOrder = ordered.filterNot { it.key == ph.key }
                                                                    },
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                                Box(
                                                                    Modifier.size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.65f)),
                                                                    contentAlignment = Alignment.Center,
                                                                ) {
                                                                    Icon(Icons.Filled.Close, L.s("close"), tint = Color.White, modifier = Modifier.size(14.dp))
                                                                }
                                                            }
                                                        }
                                                        Box(
                                                            Modifier
                                                                .align(Alignment.BottomStart)
                                                                .padding(4.dp)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Chip)
                                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                                        ) {
                                                            T(L.num(localDate(ph.time).year), size = 10, weight = FontWeight.Bold, color = ChipInk)
                                                        }
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Box(
                                                            Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .clickable(enabled = idx > 0, role = Role.Button) {
                                                                    val next = ordered.toMutableList()
                                                                    val tmp = next[idx]
                                                                    next[idx] = next[idx - 1]
                                                                    next[idx - 1] = tmp
                                                                    customOrder = next
                                                                },
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Icon(
                                                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                                L.s("previous_month"),
                                                                tint = ReelInk.copy(alpha = if (idx > 0) 0.9f else 0.25f),
                                                                modifier = Modifier.size(20.dp),
                                                            )
                                                        }
                                                        Box(
                                                            Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .clickable(enabled = idx < ordered.lastIndex, role = Role.Button) {
                                                                    val next = ordered.toMutableList()
                                                                    val tmp = next[idx]
                                                                    next[idx] = next[idx + 1]
                                                                    next[idx + 1] = tmp
                                                                    customOrder = next
                                                                },
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Icon(
                                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                                L.s("next_month"),
                                                                tint = ReelInk.copy(alpha = if (idx < ordered.lastIndex) 0.9f else 0.25f),
                                                                modifier = Modifier.size(20.dp),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (panelScroll.canScrollForward) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(28.dp)
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, ReelBg))),
                                )
                            }
                        }
                    }
                }

                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        playerSection(Modifier.weight(1f).fillMaxHeight())
                        controlsSection(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        playerSection(Modifier.weight(1f).fillMaxWidth())
                        controlsSection(Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // Export progress overlay
        exportProgress?.let { p ->
            val pct = (p * 100).roundToInt().coerceIn(0, 100)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    T(L.s("reel_making"), size = 19, weight = FontWeight.ExtraBold, color = ReelInk, align = TextAlign.Center)
                    LinearProgressIndicator(
                        progress = { p },
                        color = ReelInk,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.width(220.dp),
                    )
                    Text(
                        ltrIsolate("${L.num(pct)}%"),
                        color = ReelInk,
                        style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFeatureSettings = "tnum"),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    T(L.s("reel_making_note"), size = 14, color = ReelInk.copy(alpha = 0.75f), align = TextAlign.Center, modifier = Modifier.widthIn(max = 260.dp))
                    Box(
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button) { makeJob?.cancel() }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { T(L.s("cancel"), size = 15, weight = FontWeight.Bold, color = ReelInk) }
                }
            }
        }
    }
}

/** Leaf composable that reads playback time strictly during the draw phase (0 recompositions/sec). */
@Composable
private fun ReelPlayerCanvas(
    renderer: ReelRenderer,
    total: Double,
    timeProvider: () -> Double,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val t = minOf(timeProvider(), total)
        drawIntoCanvas { renderer.draw(it.nativeCanvas, size.width, size.height, t) }
    }
}

/** Accessible 48dp-tall scrubber with tap-to-seek, drag-to-seek, thumb knob, and tnum clocks. */
@Composable
private fun ReelScrubber(
    timeProvider: () -> Double,
    total: Double,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shownSeconds by remember(total) { derivedStateOf { minOf(timeProvider(), total).roundToInt() } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            ltrIsolate(clockSeconds(shownSeconds)),
            color = ReelInk.copy(alpha = 0.78f),
            style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFeatureSettings = "tnum"),
        )
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .padding(horizontal = 10.dp)
                .pointerInput(total, rtl) {
                    detectTapGestures { offset ->
                        val x = if (rtl) size.width - offset.x else offset.x
                        onSeek((x / size.width.coerceAtLeast(1)).toDouble().coerceIn(0.0, 1.0) * total)
                    }
                }
                .pointerInput(total, rtl) {
                    detectHorizontalDragGestures { change, _ ->
                        val x = if (rtl) size.width - change.position.x else change.position.x
                        onSeek((x / size.width.coerceAtLeast(1)).toDouble().coerceIn(0.0, 1.0) * total)
                    }
                }
                .semantics {
                    contentDescription = L.s("reel_preview")
                    val currentFrac = (shownSeconds / total.coerceAtLeast(0.01)).toFloat().coerceIn(0f, 1f)
                    progressBarRangeInfo = ProgressBarRangeInfo(currentFrac, 0f..1f)
                    setProgress { target ->
                        onSeek(target.coerceIn(0f, 1f).toDouble() * total)
                        true
                    }
                }
                .drawBehind {
                    val frac = (minOf(timeProvider(), total) / total.coerceAtLeast(0.01)).toFloat().coerceIn(0f, 1f)
                    val trackH = 4.dp.toPx()
                    val cy = size.height / 2f
                    val radius = CornerRadius(trackH / 2f, trackH / 2f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        topLeft = Offset(0f, cy - trackH / 2f),
                        size = Size(size.width, trackH),
                        cornerRadius = radius,
                    )
                    val fillW = size.width * frac
                    val fillStart = if (rtl) size.width - fillW else 0f
                    drawRoundRect(
                        color = ReelInk,
                        topLeft = Offset(fillStart, cy - trackH / 2f),
                        size = Size(fillW, trackH),
                        cornerRadius = radius,
                    )
                    val thumbX = if (rtl) size.width - fillW else fillW
                    drawCircle(
                        color = ReelInk,
                        radius = 7.dp.toPx(),
                        center = Offset(thumbX.coerceIn(7.dp.toPx(), (size.width - 7.dp.toPx()).coerceAtLeast(7.dp.toPx())), cy),
                    )
                },
        )
        Text(
            ltrIsolate(clockSeconds(total.roundToInt())),
            color = ReelInk.copy(alpha = 0.78f),
            style = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun ReelChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = on },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = 38.dp)
                .clip(RoundedCornerShape(50))
                .background(if (on) ReelInk else Color.Transparent)
                .border(1.dp, if (on) Color.Transparent else Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) { T(label, size = 14, weight = FontWeight.Bold, color = if (on) ReelBg else ReelInk, maxLines = 1) }
    }
}

/** One option: its name, then its choices. Wraps cleanly at large font scales with 48dp touch targets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <V> OptionRow(key: String, choices: List<Pair<V, String>>, value: V, onPick: (V) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        T(L.s(key), size = 14, weight = FontWeight.Bold, color = ReelInk.copy(alpha = 0.85f), modifier = Modifier.padding(end = 8.dp))
        FlowRow(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.09f)).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            choices.forEach { (v, label) ->
                val on = v == value
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) ReelInk else Color.Transparent)
                        .clickable(role = Role.Button) { onPick(v) }
                        .semantics { selected = on }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { T(label, size = 13, weight = FontWeight.Bold, color = if (on) ReelBg else ReelInk.copy(alpha = 0.8f), maxLines = 1) }
            }
        }
    }
}

private fun clockSeconds(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val sec = s % 60
    return "${L.num(s / 60)}:${if (sec < 10) L.num(0) else ""}${L.num(sec)}"
}
