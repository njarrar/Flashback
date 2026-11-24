package com.njarrar.backthen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateFormat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.PhotoSizeSelectActual
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.formatDate
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.weekdayTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/** Double tap goes to 2.5x at the tap, or back to fit from any zoom. */
private val ToggleZoom = object : DoubleClickToZoomListener {
    override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
        if ((state.zoomFraction ?: 0f) > 0.01f) state.resetZoom() else state.zoomTo(2.5f, centroid)
    }
}

/**
 * Full-screen photo viewer: swipe between photos, pinch or double tap to zoom, swipe down
 * to close, tap to hide the controls. Heart, share, add to album, and photo info at the bottom.
 */
@Composable
fun ViewerScreen(
    photos: List<Photo>,
    start: Int,
    favorites: Set<String>,
    onFavorite: (Photo) -> Unit,
    onShare: (Photo) -> Unit,
    onAlbum: (Photo) -> Unit,
    onClose: () -> Unit,
    onShowDay: ((MonthDay) -> Unit)? = null,
    cityAt: (suspend (Double, Double) -> String?)? = null,
) {
    val reduce = LocalReduceMotion.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (photos.size - 1).coerceAtLeast(0))) { photos.size }
    val photo = photos.getOrNull(pager.currentPage) ?: return
    val date = localDate(photo.time)
    val context = LocalContext.current
    val use24 = DateFormat.is24HourFormat(context)
    val fav = photo.key in favorites
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragGen by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(false) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    // Track per-photo broken state so Share is disabled on missing files.
    val brokenKeys = remember { mutableStateMapOf<String, Boolean>() }

    // Drag offset kept in Animatable so concurrent drags cancel in-flight spring-backs cleanly.
    val dragY = remember { Animatable(0f) }
    val fullPull = with(density) { 300.dp.toPx() }
    val closeAt = with(density) { 120.dp.toPx() }
    val flick = with(density) { 800.dp.toPx() }
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx().coerceAtLeast(600f) }

    DarkSystemBars()

    PredictiveBackHandler(enabled = !showInfo) { progress ->
        try {
            progress.collect { event -> backProgress = if (reduce) 0f else event.progress }
            onClose()
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    var shown by remember { mutableStateOf(pager.currentPage) }
    LaunchedEffect(pager.currentPage) {
        dragGen++
        dragY.snapTo(0f)
        isDragging = false
        if (pager.currentPage != shown) {
            shown = pager.currentPage
            Haptics.tick(view)
        }
    }
    val chromeAlpha by animateFloatAsState(
        targetValue = if (chrome) 1f else 0f,
        animationSpec = if (reduce) snap() else tween(200),
        label = "chrome",
    )

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val pull = (dragY.value.coerceAtLeast(0f) / fullPull).coerceIn(0f, 1f)
                    val combined = maxOf(pull, backProgress * 0.6f)
                    val bgAlpha = (1f - combined).coerceIn(0f, 1f)
                    drawRect(ViewerBg.copy(alpha = bgAlpha))
                    // Subtle warm darkroom ambient glow behind framed photos.
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2B241E).copy(alpha = 0.34f * bgAlpha), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.45f),
                            radius = size.maxDimension * 0.65f,
                        ),
                    )
                },
        )
        if (!reduce) {
            val ambientReq = rememberThumbnailRequest(photo.uri, maxPx = 48, crossfade = true)
            AsyncImage(
                model = ambientReq,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pull = (dragY.value.coerceAtLeast(0f) / fullPull).coerceIn(0f, 1f)
                        val combined = maxOf(pull, backProgress * 0.6f)
                        alpha = 0.14f * (1f - combined).coerceIn(0f, 1f)
                        scaleX = 1.25f
                        scaleY = 1.25f
                    },
            )
        }
        HorizontalPager(
            state = pager,
            key = { photos[it].key },
            userScrollEnabled = !zoomed && !isDragging,
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = !zoomed,
                    onDragStarted = {
                        dragGen++
                    },
                    state = rememberDraggableState { d ->
                        val next = (dragY.value + d).coerceAtLeast(0f)
                        if (next > 0f || isDragging) {
                            isDragging = true
                            scope.launch { dragY.snapTo(next) }
                        }
                    },
                    onDragStopped = { velocity ->
                        val currentGen = dragGen
                        val curY = dragY.value
                        if (curY > 0f && (curY > closeAt || velocity > flick)) {
                            scope.launch {
                                if (!reduce) {
                                    dragY.animateTo(screenHeightPx, animationSpec = tween(180))
                                }
                                if (dragGen == currentGen) onClose()
                            }
                        } else if (curY > 0f || isDragging) {
                            scope.launch {
                                try {
                                    if (reduce) {
                                        dragY.snapTo(0f)
                                    } else {
                                        dragY.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                                        )
                                    }
                                } finally {
                                    if (dragGen == currentGen) isDragging = false
                                }
                            }
                        }
                    },
                ),
        ) { i ->
            val current = i == pager.currentPage
            val itemPhoto = photos[i]
            val zoom = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 4f)))
            LaunchedEffect(current) {
                if (current) {
                    snapshotFlow { (zoom.zoomableState.zoomFraction ?: 0f) > 0.01f }
                        .distinctUntilChanged()
                        .collect { zoomed = it }
                } else {
                    zoom.zoomableState.resetZoom(snap())
                }
            }
            var retryTick by remember(itemPhoto.key) { mutableIntStateOf(0) }
            // 0 = loading, 1 = success, 2 = error
            var loadState by remember(itemPhoto.key, retryTick) { mutableIntStateOf(0) }
            val imageRequest = remember(context, itemPhoto.uri, retryTick, reduce) {
                ImageRequest.Builder(context)
                    .data(itemPhoto.uri)
                    .crossfade(!reduce)
                    .listener(
                        onStart = { loadState = 0 },
                        onSuccess = { _, _ ->
                            loadState = 1
                            brokenKeys[itemPhoto.key] = false
                        },
                        onError = { _, _ ->
                            loadState = 2
                            brokenKeys[itemPhoto.key] = true
                        },
                    )
                    .build()
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (current) {
                            translationY = dragY.value
                            val pull = (dragY.value.coerceAtLeast(0f) / fullPull).coerceIn(0f, 1f)
                            val backScale = 1f - 0.12f * backProgress
                            val s = (1f - 0.15f * pull) * backScale
                            scaleX = s
                            scaleY = s
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                ZoomableAsyncImage(
                    model = imageRequest,
                    contentDescription = L.s("photo_from", "date" to formatDate(localDate(itemPhoto.time), "yMMMMd")),
                    state = zoom,
                    contentScale = ContentScale.Fit,
                    onClick = { chrome = !chrome },
                    onDoubleClick = ToggleZoom,
                    modifier = Modifier.fillMaxSize(),
                )
                if (loadState == 0 && !zoom.isImageDisplayed) {
                    CircularProgressIndicator(
                        color = ViewerInk.copy(alpha = 0.65f),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier
                            .size(32.dp)
                            .semantics { contentDescription = L.s("loading_hd") },
                    )
                } else if (loadState == 2) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .padding(32.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                    ) {
                        Icon(Icons.Outlined.BrokenImage, null, tint = ViewerInk.copy(alpha = 0.7f), modifier = Modifier.size(36.dp))
                        T(L.s("photo_unavailable"), size = 15, weight = FontWeight.SemiBold, color = ViewerInk, align = TextAlign.Center)
                        Pill(L.s("retry"), { retryTick++ }, kind = Kind.Secondary, height = 44.dp)
                    }
                }
            }
        }

        if (chromeAlpha > 0.01f) {
            val isBroken = brokenKeys[photo.key] == true
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pull = (dragY.value.coerceAtLeast(0f) / fullPull).coerceIn(0f, 1f)
                        alpha = chromeAlpha * (1f - pull) * (1f - backProgress)
                    }
                    .then(if (!chrome) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                // Top bar with protective gradient scrim over bright photos.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent)))
                        .statusBarsPadding(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onClose,
                            enabled = chrome,
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(ViewerInk.copy(alpha = 0.16f)),
                        ) {
                            Icon(Icons.Filled.Close, L.s("close"), tint = ViewerInk)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            T(formatDate(date, "yMMMMd"), size = 16, weight = FontWeight.Bold, color = ViewerInk, maxLines = 1)
                            T(
                                L.s("ago_weekday", "ago" to agoLabel(date.year), "weekday" to weekdayTime(photo.time, use24)),
                                size = 13,
                                color = ViewerInk.copy(alpha = 0.78f),
                                maxLines = 2,
                                align = TextAlign.Center,
                            )
                        }
                        if (photos.size > 1) {
                            Box(
                                Modifier
                                    .heightIn(min = 32.dp)
                                    .widthIn(min = 48.dp)
                                    .clip(CircleShape)
                                    .background(ViewerInk.copy(alpha = 0.16f))
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    L.s("n_of_total", "n" to pager.currentPage + 1, "total" to photos.size),
                                    color = ViewerInk,
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontFamily = Nunito,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        fontFeatureSettings = "tnum",
                                    ),
                                )
                            }
                        } else {
                            Spacer(Modifier.size(48.dp))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // Bottom filmstrip & actions with protective gradient scrim.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.74f))))
                        .navigationBarsPadding(),
                ) {
                    if (photos.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        Filmstrip(photos, pager.currentPage, enabled = chrome) { i ->
                            scope.launch {
                                if (reduce) pager.scrollToPage(i) else pager.animateScrollToPage(i)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    val heartScale by animateFloatAsState(
                        targetValue = if (fav) 1.15f else 1f,
                        animationSpec = if (reduce) snap() else spring(dampingRatio = 0.42f, stiffness = 520f),
                        label = "viewerHeart",
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xC71B1A19))
                            .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        Action(
                            if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            L.s("favorite_short"),
                            L.s(if (fav) "unfavorite" else "favorite"),
                            tint = if (fav) ViewerHeart else ViewerInk,
                            iconScale = if (fav) heartScale else 1f,
                            enabled = chrome,
                        ) {
                            if (!fav) Haptics.confirm(view) else Haptics.tick(view)
                            onFavorite(photo)
                        }
                        Action(
                            Icons.Outlined.Share,
                            L.s("share"),
                            L.s("share"),
                            tint = if (isBroken) ViewerInk.copy(alpha = 0.35f) else ViewerInk,
                            enabled = chrome && !isBroken,
                        ) { onShare(photo) }
                        Action(
                            Icons.Outlined.LibraryAdd,
                            L.s("add_to_album"),
                            L.s("add_to_album"),
                            enabled = chrome,
                        ) { onAlbum(photo) }
                        Action(
                            Icons.Outlined.Info,
                            L.s("info"),
                            L.s("info"),
                            enabled = chrome,
                        ) { showInfo = true }
                    }
                }
            }
        }
    }

    if (showInfo) {
        PhotoInfoSheet(
            photo = photo,
            use24 = use24,
            cityAt = cityAt,
            onShowDay = onShowDay?.let { openDay ->
                {
                    showInfo = false
                    onClose()
                    openDay(MonthDay.of(date))
                }
            },
            onDismiss = { showInfo = false },
        )
    }
}

private data class PhotoExifInfo(
    val camera: String?,
    val details: String?,
    val latLong: DoubleArray?,
)

@Composable
private fun PhotoInfoSheet(
    photo: Photo,
    use24: Boolean,
    cityAt: (suspend (Double, Double) -> String?)?,
    onShowDay: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val date = localDate(photo.time)
    var exif by remember(photo.key) { mutableStateOf<PhotoExifInfo?>(null) }
    var city by remember(photo.key) { mutableStateOf<String?>(null) }

    LaunchedEffect(photo.key) {
        val parsed = withContext(Dispatchers.IO) {
            runCatching {
                val baseUri = Uri.parse(photo.uri)
                val hasMediaLocation = Build.VERSION.SDK_INT < 29 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
                val preferredUri = if (Build.VERSION.SDK_INT >= 29 && hasMediaLocation) {
                    runCatching { MediaStore.setRequireOriginal(baseUri) }.getOrDefault(baseUri)
                } else {
                    baseUri
                }
                val stream = try {
                    context.contentResolver.openInputStream(preferredUri)
                } catch (_: SecurityException) {
                    if (preferredUri != baseUri) context.contentResolver.openInputStream(baseUri) else null
                }
                stream?.use { input ->
                    val ex = ExifInterface(input)
                    val make = ex.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
                    val model = ex.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
                    val camera = when {
                        model.isBlank() -> make.ifBlank { null }
                        make.isNotBlank() && !model.startsWith(make, ignoreCase = true) -> "$make $model"
                        else -> model
                    }
                    val parts = buildList {
                        ex.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()?.let { add(String.format(java.util.Locale.ROOT, "f/%.1f", it)) }
                        ex.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()?.let { exp ->
                            if (exp > 0 && exp < 1.0) add("1/${(1.0 / exp).toInt()}s") else if (exp >= 1.0) add("${exp.toInt()}s")
                        }
                        ex.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { add("ISO $it") }
                    }
                    val latLong = if (hasMediaLocation) ex.latLong else null
                    PhotoExifInfo(camera, parts.joinToString(" · ").ifBlank { null }, latLong)
                }
            }.getOrNull()
        }
        exif = parsed
        val coords = parsed?.latLong
        if (cityAt != null && coords != null && coords.size >= 2) {
            city = runCatching { cityAt(coords[0], coords[1]) }.getOrNull()
        }
    }

    BottomSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(L.s("info"), size = 22, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, L.s("close"), tint = p.muted)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoRow(
                Icons.Outlined.CalendarToday,
                formatDate(date, "yMMMMd"),
                L.s("ago_weekday", "ago" to agoLabel(date.year), "weekday" to weekdayTime(photo.time, use24)),
            )
            if (!city.isNullOrBlank()) {
                InfoRow(Icons.Outlined.Place, city!!, null)
            }
            if (photo.width > 0 && photo.height > 0) {
                InfoRow(
                    Icons.Outlined.PhotoSizeSelectActual,
                    ltrIsolate("${L.num(photo.width)} × ${L.num(photo.height)}"),
                    null,
                )
            }
            exif?.let { info ->
                if (info.camera != null || info.details != null) {
                    InfoRow(Icons.Outlined.CameraAlt, info.camera ?: info.details!!, if (info.camera != null) info.details else null)
                }
            }
            if (onShowDay != null) {
                Pill(L.s("show_this_day"), onShowDay, kind = Kind.Secondary, height = 48.dp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, primary: String, secondary: String?) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(p.fill), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.muted2, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            T(primary, size = 15, weight = FontWeight.SemiBold)
            if (!secondary.isNullOrBlank()) {
                T(secondary, size = 13, muted = true)
            }
        }
    }
}

/** An icon with a short word under it. */
@Composable
private fun Action(
    icon: ImageVector,
    word: String,
    label: String,
    tint: Color = ViewerInk,
    iconScale: Float = 1f,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .widthIn(min = 68.dp)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            null,
            tint = tint,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
        )
        T(word, size = 11, weight = FontWeight.SemiBold, color = ViewerInk.copy(alpha = if (enabled) 0.85f else 0.4f), maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** Small thumbnails of every photo, with the current one wider and outlined, plus tappable year pills. */
@Composable
private fun Filmstrip(photos: List<Photo>, index: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    val reduce = LocalReduceMotion.current
    val strip = rememberLazyListState(initialFirstVisibleItemIndex = index)
    LaunchedEffect(index) {
        if (reduce) strip.scrollToItem(index) else strip.animateScrollToItem(index)
    }
    val years = remember(photos) { photos.map { localDate(it.time).year } }
    val multiYear = remember(years) { years.distinct().size > 1 }
    BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp)) {
        val side = (maxWidth / 2 - 34.dp).coerceAtLeast(0.dp)
        LazyRow(
            state = strip,
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(photos, key = { _, p -> p.key }) { i, p ->
                val on = i == index
                val yearBoundary = multiYear && (i == 0 || years[i] != years[i - 1])
                val request = rememberThumbnailRequest(p.uri, maxPx = 160, crossfade = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (yearBoundary) {
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(ViewerInk.copy(alpha = 0.18f))
                                .clickable(enabled = enabled, role = Role.Button) { onPick(i) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                .semantics { contentDescription = L.num(years[i]) },
                        ) {
                            T(L.num(years[i]), size = 11, weight = FontWeight.ExtraBold, color = ViewerInk)
                        }
                    }
                    Box(
                        Modifier
                            .height(48.dp)
                            .padding(horizontal = if (on) 4.dp else 0.dp)
                            .width(if (on) 60.dp else 44.dp)
                            .clickable(enabled = enabled, role = Role.Tab) { onPick(i) }
                            .semantics {
                                contentDescription = L.s("photo_n_of", "n" to i + 1, "total" to photos.size)
                                selected = on
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            request,
                            null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(if (on) 60.dp else 44.dp, 44.dp)
                                .drawWithContent {
                                    drawContent()
                                    if (on) {
                                        val o = 2.dp.toPx()
                                        drawRoundRect(
                                            ViewerInk,
                                            topLeft = Offset(-o, -o),
                                            size = Size(size.width + 2 * o, size.height + 2 * o),
                                            cornerRadius = CornerRadius(4.dp.toPx()),
                                            style = Stroke(2.dp.toPx()),
                                        )
                                    }
                                }
                                .clip(RoundedCornerShape(2.dp))
                                .graphicsLayer { alpha = if (on) 1f else 0.7f },
                        )
                    }
                }
            }
        }
    }
}
