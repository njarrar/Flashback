package com.njarrar.backthen.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size
import com.njarrar.backthen.ToastKind
import com.njarrar.backthen.ToastState
import com.njarrar.backthen.core.COLUMNS
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.formatDate
import com.njarrar.backthen.core.formatTime
import com.njarrar.backthen.core.layoutMosaic
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.masonryColumns
import com.njarrar.backthen.core.tileAspect
import com.njarrar.backthen.core.weekdayOf
import kotlin.math.roundToInt

/** Builds a size-capped Coil [ImageRequest] so thumbnails never decode full 12–48MP bitmaps. */
@Composable
fun rememberThumbnailRequest(uri: String?, maxPx: Int = 512, crossfade: Boolean = true): ImageRequest? {
    if (uri == null) return null
    val context = LocalContext.current
    val reduce = LocalReduceMotion.current
    return remember(context, uri, maxPx, crossfade, reduce) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(Size(maxPx, maxPx))
            .precision(Precision.INEXACT)
            .crossfade(crossfade && !reduce)
            .build()
    }
}

/** Subtle shimmer for skeleton placeholders; falls back to static [base] when Reduce Motion is on. */
fun Modifier.shimmer(base: Color, highlight: Color): Modifier = composed {
    if (LocalReduceMotion.current) {
        this.background(base)
    } else {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
            label = "shimmerAlpha",
        )
        this.drawBehind {
            val blended = Color(
                red = base.red + (highlight.red - base.red) * progress,
                green = base.green + (highlight.green - base.green) * progress,
                blue = base.blue + (highlight.blue - base.blue) * progress,
                alpha = base.alpha + (highlight.alpha - base.alpha) * progress,
            )
            drawRect(blended)
        }
    }
}

/** Isolates an LTR token (numbers, ratios, "+N") inside an RTL string using Unicode LRI/PDI. */
internal fun ltrIsolate(text: String): String = "\u2066$text\u2069"

/** First-strong BiDi isolate (FSI/PDI) for mixed-script spans. */
internal fun bidiIsolate(text: String): String = "\u2068$text\u2069"

@Composable
fun T(
    text: String,
    size: Int = 16,
    weight: FontWeight = FontWeight.Normal,
    color: Color? = null,
    muted: Boolean = false,
    tight: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
) {
    val p = LocalPalette.current
    // Arabic letters reach higher and lower than Latin ones, so lines get more room.
    val arabic = L.lang.arabicScript
    Text(
        text,
        modifier = modifier,
        color = color ?: if (muted) p.muted else p.ink,
        fontFamily = Nunito,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * if (size >= 30) (if (arabic) 1.3 else 1.1) else (if (arabic) 1.6 else 1.4)).sp,
        letterSpacing = if (tight) tightSpacing(size).sp else 0.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

enum class Kind { Primary, Secondary, Ghost, Accent, Tint }

@Composable
fun Pill(
    label: String,
    onClick: () -> Unit,
    kind: Kind = Kind.Primary,
    height: Dp = 54.dp,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    iconAfter: Boolean = false,
    icon: (@Composable (Color) -> Unit)? = null,
) {
    val p = LocalPalette.current
    val (bg, fg) = when (kind) {
        Kind.Primary -> p.ink to p.bg
        Kind.Secondary -> p.surface to p.ink
        Kind.Ghost -> Color.Transparent to p.ink
        Kind.Accent -> AccentFill to Color.White
        Kind.Tint -> p.accTint to p.accInk
    }
    Row(
        modifier
            .minimumInteractiveComponentSize()
            // Grows past its usual height when large text wraps the label.
            .heightIn(min = height)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (kind == Kind.Secondary) Modifier.border(1.dp, p.line, RoundedCornerShape(50)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (height < 48.dp) 16.dp else 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (!iconAfter) icon?.invoke(fg.copy(alpha = if (enabled) 1f else 0.4f))
        T(label, size = if (height < 48.dp) 15 else 16, weight = FontWeight.SemiBold, color = fg.copy(alpha = if (enabled) 1f else 0.4f))
        if (iconAfter) icon?.invoke(fg.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

/** The app icon from the design: two tilted prints on blue, the front one smiling. */
@Composable
fun Logo(size: Dp = 36.dp) {
    // A drawing, not text: it stays the same way round in right to left languages.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { LogoDrawing(size) }
}

@Composable
private fun LogoDrawing(size: Dp) {
    val s = size.value / 180f
    fun u(v: Float) = (v * s).dp
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(Color(0xFF2E7BFF))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(this.size.width * 0.3f, this.size.height * 0.18f),
                        radius = this.size.width * 0.56f,
                    ),
                )
            },
    ) {
        Box(
            Modifier.offset(u(62f), u(30f)).size(u(86f), u(102f)).rotate(12f)
                .background(Color.White, RoundedCornerShape(u(14f)))
                .padding(start = u(8f), top = u(8f), end = u(8f), bottom = u(22f)),
        ) { Box(Modifier.fillMaxSize().background(Color(0xFFFFC93C), RoundedCornerShape(u(7f)))) }
        Box(
            Modifier.offset(u(34f), u(44f)).size(u(96f), u(114f)).rotate(-7f)
                .background(Color.White, RoundedCornerShape(u(15f))),
        ) {
            Box(Modifier.offset(u(9f), u(9f)).size(u(78f), u(78f)).background(Color(0xFFFF4B3E), RoundedCornerShape(u(8f)))) {
                val ink = Color(0xFF1D1B2E)
                Box(Modifier.offset(u(21f), u(29.6f)).size(u(9.5f), u(10.5f)).background(ink, RoundedCornerShape(50)))
                Box(Modifier.offset(u(47.4f), u(29.6f)).size(u(9.5f), u(10.5f)).background(ink, RoundedCornerShape(50)))
                Box(
                    Modifier.offset(u(27.5f), u(44.3f)).size(u(23f), u(11.5f))
                        .background(ink, RoundedCornerShape(bottomStart = u(11.5f), bottomEnd = u(11.5f))),
                )
                Box(Modifier.offset(u(10.6f), u(41f)).size(u(10.5f), u(7.4f)).background(Color(0xE6FF9EC0), RoundedCornerShape(50)))
                Box(Modifier.offset(u(56.8f), u(41f)).size(u(10.5f), u(7.4f)).background(Color(0xE6FF9EC0), RoundedCornerShape(50)))
            }
        }
    }
}

@Composable
fun Toast(
    state: ToastState?,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    var last by remember { mutableStateOf<ToastState?>(null) }
    if (state != null) last = state
    val shown = state ?: last
    AnimatedVisibility(
        visible = state != null,
        enter = if (reduce) EnterTransition.None else fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 },
        exit = if (reduce) ExitTransition.None else fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
        modifier = modifier,
    ) {
        if (shown != null) {
            val icon = when (shown.kind) {
                ToastKind.Success -> Icons.Filled.CheckCircle
                ToastKind.Info -> Icons.Outlined.Info
                ToastKind.Error -> Icons.Outlined.ErrorOutline
            }
            val iconTint = when (shown.kind) {
                ToastKind.Success -> p.toastCheck
                ToastKind.Info -> p.bg.copy(alpha = 0.85f)
                ToastKind.Error -> p.toastCheck
            }
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 520.dp)
                    .heightIn(min = 44.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(p.ink)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(start = 16.dp, end = if (shown.undo != null) 8.dp else 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
                T(
                    shown.text,
                    size = 15,
                    weight = FontWeight.SemiBold,
                    color = p.bg,
                    maxLines = 3,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val undo = shown.undo
                val undoLabel = shown.undoLabel
                if (undo != null && undoLabel != null) {
                    Box(
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button) {
                                undo()
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        T(undoLabel, size = 14, weight = FontWeight.ExtraBold, color = p.toastCheck, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Selection state shared by the photo grids. */
@Immutable
data class Selection(
    val on: Boolean,
    val keys: Set<String>,
    /** Long press on a photo while not selecting: start selecting with that photo picked. */
    val start: ((Photo) -> Unit)? = null,
    val toggle: (Photo) -> Unit,
    /** Batch toggle for selecting/deselecting an entire year in one state update and haptic. */
    val toggleAll: ((List<Photo>, Boolean) -> Unit)? = null,
    /** Long press on a year card while not selecting: enters select mode with all photos in one haptic. */
    val startAll: ((List<Photo>) -> Unit)? = null,
)

@Composable
fun PhotoTile(
    photo: Photo,
    radius: Dp,
    favorite: Boolean,
    selection: Selection?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    thumbPx: Int = 512,
    semanticsLabel: String? = null,
    semanticsState: String? = null,
    customActionsList: List<CustomAccessibilityAction> = emptyList(),
) {
    val p = LocalPalette.current
    val picked = selection?.keys?.contains(photo.key) == true
    val shape = RoundedCornerShape(radius)
    val startSelecting = selection?.takeIf { !it.on }?.start
    val selectingOn = selection?.on == true
    // Tells a drag across the photos where this one sits.
    val drag = if (selection == null) null else LocalDragSelect.current
    if (drag != null) DisposableEffect(drag, photo.key) { onDispose { drag.forget(photo.key) } }
    var loadFailed by remember(photo.uri) { mutableStateOf(false) }
    val request = rememberThumbnailRequest(photo.uri, maxPx = thumbPx)
    val langVersion = L.version.value
    val label = remember(photo.time, favorite, semanticsLabel, langVersion) {
        semanticsLabel ?: L.s(
            if (favorite) "favorite_photo_from" else "photo_from",
            "date" to formatDate(localDate(photo.time), "yMMMMd"),
        )
    }
    Box(
        modifier
            .then(if (drag != null) Modifier.onGloballyPositioned { drag.place(photo, it.boundsInRoot()) } else Modifier)
            .clip(shape)
            .background(p.fill)
            .combinedClickable(
                role = if (selectingOn) Role.Checkbox else Role.Button,
                onLongClickLabel = L.s("select"),
                onLongClick = startSelecting?.let { start -> { start(photo) } },
                onClick = { if (selectingOn) selection.toggle(photo) else onOpen() },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                if (selectingOn) selected = picked
                if (semanticsState != null) stateDescription = semanticsState
                if (customActionsList.isNotEmpty()) customActions = customActionsList
            },
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onLoading = { loadFailed = false },
            onSuccess = { loadFailed = false },
            onError = { loadFailed = true },
            modifier = Modifier.fillMaxSize(),
        )
        if (loadFailed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BrokenImage, null, tint = p.muted, modifier = Modifier.size(24.dp))
            }
        }
        if (favorite) {
            // Cached radial corner wash + soft circular shadow (works cleanly on API 26–36 without blur).
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(64.dp)
                    .drawWithCache {
                        val x = if (layoutDirection == LayoutDirection.Rtl) size.width else 0f
                        val wash = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.36f), Color.Transparent),
                            center = Offset(x, size.height),
                            radius = size.width * 0.9f,
                        )
                        onDrawBehind { drawRect(wash) }
                    },
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 5.dp, bottom = 4.dp)
                    .size(22.dp)
                    .drawWithCache {
                        val glow = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * 0.5f,
                        )
                        onDrawBehind { drawCircle(glow) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        if (selectingOn) {
            Box(
                Modifier.fillMaxSize()
                    .background(if (picked) Accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f))
                    .then(if (picked) Modifier.border(3.dp, Accent, shape) else Modifier),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.34f))
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (picked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (picked) Accent else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Formats "ago · weekday, photos" using the localized template with BiDi-safe bold emphasis on [ago]. */
@Composable
internal fun styledAgoSubtitle(template: String, ago: String): androidx.compose.ui.text.AnnotatedString {
    val p = LocalPalette.current
    val idx = template.indexOf(ago)
    return buildAnnotatedString {
        append(template)
        if (idx >= 0) {
            addStyle(SpanStyle(color = p.ink, fontWeight = FontWeight.Bold), idx, idx + ago.length)
        }
    }
}

/**
 * The big year header: year number, how long ago, and a share (or select-year) button.
 * Rendered normally inside its LazyColumn item (no negative layout offset).
 */
@Composable
fun YearHeader(
    year: Int,
    count: Int,
    day: MonthDay,
    onShare: () -> Unit,
    selecting: Boolean = false,
    allPicked: Boolean = false,
    onToggleYear: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    val ago = agoLabel(year)
    val subtitle = L.s(
        "ago_weekday_photos",
        "ago" to ago,
        "weekday" to weekdayOf(year, day),
        "photos" to L.p("photos", count),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(L.num(year), size = 40, weight = FontWeight.Black, tight = true, modifier = Modifier.weight(1f).semantics { heading() })
            if (selecting && onToggleYear != null) {
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (allPicked) p.accTint else p.fill)
                        .clickable(role = Role.Button, onClick = onToggleYear)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    T(
                        L.s(if (allPicked) "deselect_year" else "select_year"),
                        size = 13,
                        weight = FontWeight.Bold,
                        color = if (allPicked) p.accInk else p.ink,
                        maxLines = 1,
                    )
                }
            } else {
                IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Share, L.s("share_year", "year" to year), tint = p.ink, modifier = Modifier.size(22.dp))
                }
            }
        }
        Text(
            styledAgoSubtitle(subtitle, ago),
            color = p.muted,
            fontFamily = Nunito,
            fontSize = 14.sp,
            lineHeight = (if (L.lang.arabicScript) 22 else 20).sp,
        )
    }
}

/** Single floating compact year bar shown at the top of Today's list while a year is scrolled past. */
@Composable
fun YearBar(
    year: Int,
    count: Int,
    day: MonthDay,
    pinned: Boolean,
    onShare: () -> Unit,
    selecting: Boolean = false,
    allPicked: Boolean = false,
    onToggleYear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val alpha by animateFloatAsState(
        targetValue = if (pinned) 1f else 0f,
        animationSpec = if (reduce) snap() else tween(150),
        label = "yearBar",
    )
    if (alpha <= 0.01f && !pinned) return
    Box(
        modifier
            .zIndex(1f)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(p.bg.copy(alpha = 0.94f))
            .then(if (pinned) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 20.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T(L.num(year), size = 24, weight = FontWeight.Black, tight = true, maxLines = 1)
            T(
                L.s("ago_weekday_photos", "ago" to agoLabel(year), "weekday" to weekdayOf(year, day), "photos" to L.p("photos", count)),
                size = 14,
                weight = FontWeight.Medium,
                muted = true,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (selecting && onToggleYear != null) {
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (allPicked) p.accTint else p.fill)
                        .clickable(enabled = pinned, role = Role.Button, onClick = onToggleYear)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    T(
                        L.s(if (allPicked) "deselect_year" else "select_year"),
                        size = 13,
                        weight = FontWeight.Bold,
                        color = if (allPicked) p.accInk else p.ink,
                        maxLines = 1,
                    )
                }
            } else {
                IconButton(onClick = onShare, enabled = pinned, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Share, L.s("share_year", "year" to year), tint = p.ink, modifier = Modifier.size(20.dp))
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(p.line))
    }
}

/** One year's photos for the chosen day, as a mosaic of rounded tiles. With [more], the last tile says how many more there are. */
@Composable
fun YearMosaic(photos: List<Photo>, favorites: Set<String>, selection: Selection?, onOpen: (Int) -> Unit, more: Int = 0) {
    val (tiles, rows) = remember(photos.size) { layoutMosaic(photos.size) }
    if (rows == 0) return
    val gap = 4.dp
    val row = 54.dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Layout(
            content = {
                photos.forEachIndexed { i, photo ->
                    val isMoreTile = more > 0 && i == photos.lastIndex
                    val fav = photo.key in favorites
                    val baseLabel = L.s(
                        if (fav) "favorite_photo_from" else "photo_from",
                        "date" to formatDate(localDate(photo.time), "yMMMMd"),
                    )
                    val tileLabel = if (isMoreTile) "$baseLabel, +${L.p("photos", more)}" else baseLabel
                    Box(Modifier.fillMaxSize()) {
                        PhotoTile(
                            photo = photo,
                            radius = 12.dp,
                            favorite = fav,
                            selection = selection,
                            onOpen = { onOpen(i) },
                            semanticsLabel = tileLabel,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isMoreTile) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Chip)
                                    .clearAndSetSemantics {},
                                contentAlignment = Alignment.Center,
                            ) {
                                T(ltrIsolate("+" + L.num(more)), size = 18, weight = FontWeight.ExtraBold, color = ChipInk)
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        ) { measurables, constraints ->
            // One pass, no subcomposition. Column edges are rounded once so tiles meet both sides exactly.
            val gapPx = gap.toPx()
            val rowPx = row.roundToPx()
            val step = (constraints.maxWidth + gapPx) / COLUMNS
            fun edge(col: Int) = (col * step).roundToInt()
            val gapInt = gapPx.roundToInt()
            val placeables = measurables.mapIndexed { i, measurable ->
                val t = tiles[i]
                val w = edge(t.col + t.cols) - gapInt - edge(t.col)
                val h = rowPx * t.rows + gapInt * (t.rows - 1)
                measurable.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
            }
            val height = rowPx * rows + gapInt * (rows - 1)
            layout(constraints.maxWidth, height.coerceAtLeast(0)) {
                // placeRelative mirrors the tiles right to left, as the design asks for Arabic.
                placeables.forEachIndexed { i, placeable ->
                    val t = tiles[i]
                    placeable.placeRelative(edge(t.col), t.row * (rowPx + gapInt))
                }
            }
        }
    }
}

/** Small date label on a photo tile. */
@Composable
fun DateChip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(Chip).padding(horizontal = 7.dp, vertical = 2.dp).clearAndSetSemantics {}) {
        // Shrinks to fit the tile with big text, instead of cutting the year off.
        BasicText(
            text, maxLines = 1,
            style = TextStyle(color = ChipInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, lineHeight = 14.sp, letterSpacing = if (L.lang.arabicScript) 0.sp else 0.2.sp),
            autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
        )
    }
}

/** A tilted print for the welcome screen. */
@Composable
fun Print(year: Int, label: String, @DrawableRes drawing: Int, width: Dp, angle: Float, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .width(width)
            .rotate(angle)
            .shadow(12.dp, shape, ambientColor = Color(0x28141416), spotColor = Color(0x28141416))
            .background(p.surface, shape)
            .border(1.dp, p.line, shape)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp),
    ) {
        Image(
            painterResource(drawing), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 5f).clip(RoundedCornerShape(6.dp)),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.Bottom) {
            T(L.num(year), size = if (width > 160.dp) 26 else 22, weight = FontWeight.Black, tight = true, modifier = Modifier.weight(1f))
            T(label, size = 11, weight = FontWeight.Medium, muted = true)
        }
    }
}

/** Light haptics, one per action, fired with the visual change. */
object Haptics {
    fun tick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun confirm(view: View) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** A reminder time in the phone's 12 or 24 hour style. */
@Composable
fun timeLabel(hour: Int, minute: Int): String =
    formatTime(hour, minute, android.text.format.DateFormat.is24HourFormat(LocalContext.current))

/** Strip layout: a scrollable row of year buttons that stays at the top while scrolling. */
@Composable
fun YearPills(
    years: List<Int>,
    selected: Int,
    onBounds: ((Rect?) -> Unit)? = null,
    onPick: (Int) -> Unit,
) {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val listState = rememberLazyListState()
    if (onBounds != null) {
        DisposableEffect(Unit) {
            onDispose { onBounds(null) }
        }
    }
    LaunchedEffect(selected, years) {
        val idx = years.indexOf(selected)
        if (idx >= 0) {
            if (reduce) listState.scrollToItem(idx) else listState.animateScrollToItem(idx)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.bg)
            .then(if (onBounds != null) Modifier.onGloballyPositioned { onBounds(it.boundsInRoot()) } else Modifier),
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(years, key = { it }) { y ->
                val on = y == selected
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(role = Role.Tab) { onPick(y) }
                        .semantics { this.selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (on) p.ink else p.surface)
                            .border(1.dp, if (on) p.ink else p.line, RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        T(L.num(y), size = 14, weight = FontWeight.SemiBold, color = if (on) p.bg else p.ink, maxLines = 1)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    }
}

/** Strip layout: one year, a huge auto-sized year number, two columns of photos, and buttons to step between years. */
@Composable
fun StripYear(
    year: Int,
    photos: List<Photo>,
    day: MonthDay,
    favorites: Set<String>,
    selection: Selection,
    newer: Int?,
    older: Int?,
    onOpen: (Int) -> Unit,
    onShare: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val p = LocalPalette.current
    val weekday = weekdayOf(year, day)
    val compactBar = LocalDensity.current.fontScale >= 1.35f || L.lang.arabicScript
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    text = L.num(year),
                    maxLines = 1,
                    style = TextStyle(
                        color = p.ink,
                        fontFamily = Nunito,
                        fontWeight = FontWeight.Black,
                        letterSpacing = tightSpacing(96).sp,
                    ),
                    autoSize = TextAutoSize.StepBased(minFontSize = 48.sp, maxFontSize = 96.sp, stepSize = 2.sp),
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(agoLabel(year), size = 15, weight = FontWeight.Medium)
                    T(L.s("weekday_photos", "weekday" to weekday, "photos" to L.p("photos", photos.size)), size = 15, weight = FontWeight.Medium, muted = true)
                }
            }
            val columns = remember(photos) { masonryColumns(photos.map { tileAspect(it.width, it.height) }) }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                columns.forEach { col ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        col.forEach { i ->
                            val ph = photos[i]
                            PhotoTile(
                                ph, 12.dp, ph.key in favorites, selection, onOpen = { onOpen(i) },
                                modifier = Modifier.fillMaxWidth().aspectRatio(tileAspect(ph.width, ph.height)),
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(
                    newer?.let(L::num) ?: L.s("newest"), { newer?.let(onPick) }, kind = Kind.Secondary, height = 44.dp, enabled = newer != null,
                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = it, modifier = Modifier.size(15.dp)) },
                )
                Spacer(Modifier.weight(1f))
                if (compactBar) {
                    IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Share, L.s("share_year", "year" to year), tint = p.muted, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Row(
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button, onClick = onShare)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Share, null, tint = p.muted, modifier = Modifier.size(16.dp))
                        T(L.s("share"), size = 15, weight = FontWeight.SemiBold, muted = true)
                    }
                }
                Spacer(Modifier.weight(1f))
                Pill(
                    older?.let(L::num) ?: L.s("oldest"), { older?.let(onPick) }, kind = Kind.Secondary, height = 44.dp, enabled = older != null,
                    iconAfter = true,
                    icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = it, modifier = Modifier.size(15.dp)) },
                )
            }
        }
    }
}
