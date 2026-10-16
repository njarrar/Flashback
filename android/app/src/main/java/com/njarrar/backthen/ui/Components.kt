package com.njarrar.backthen.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.njarrar.backthen.core.masonryColumns
import com.njarrar.backthen.core.tileAspect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.njarrar.backthen.core.COLUMNS
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.formatDate
import com.njarrar.backthen.core.formatTime
import com.njarrar.backthen.core.layoutMosaic
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.weekdayOf

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
        Kind.Accent -> Accent to Color.White
        Kind.Tint -> p.accTint to p.accInk
    }
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (kind == Kind.Secondary) Modifier.border(1.dp, p.line, RoundedCornerShape(50)) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (height < 48.dp) 16.dp else 20.dp),
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
            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), radius = size.value * 1.6f)),
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
fun Toast(message: String?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(p.ink).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T("✓", size = 15, weight = FontWeight.Bold, color = Color(0xFFFF8B76))
            T(message ?: "", size = 15, weight = FontWeight.SemiBold, color = p.bg)
        }
    }
}

/** Selection state shared by the photo grids. */
class Selection(val on: Boolean, val keys: Set<String>, val toggle: (Photo) -> Unit)

@Composable
fun PhotoTile(
    photo: Photo,
    radius: Dp,
    favorite: Boolean,
    selection: Selection?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val picked = selection?.keys?.contains(photo.key) == true
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(p.fill)
            .clickable(role = Role.Image) { if (selection?.on == true) selection.toggle(photo) else onOpen() },
    ) {
        AsyncImage(model = photo.uri, contentDescription = L.s("photo_from", "date" to formatDate(localDate(photo.time), "yMMMMd")), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (favorite) {
            Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 7.dp).size(16.dp))
        }
        if (selection?.on == true) {
            Box(
                Modifier.fillMaxSize()
                    .background(if (picked) Accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f))
                    .then(if (picked) Modifier.border(3.dp, Accent, shape) else Modifier),
            )
            Icon(
                if (picked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = L.s(if (picked) "selected" else "not_selected"),
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(22.dp),
            )
        }
    }
}

private val PinnedBarHeight = 44.dp

/**
 * The big year header: year number, how long ago, and a share button. It draws itself over
 * the space of the [YearBar] above it, which stays hidden until that year pins to the top.
 */
@Composable
fun YearHeader(year: Int, count: Int, day: MonthDay, onShare: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val up = PinnedBarHeight.roundToPx()
                layout(placeable.width, (placeable.height - up).coerceAtLeast(0)) { placeable.place(0, -up) }
            }
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        T(L.num(year), size = 56, weight = FontWeight.Black, tight = true, modifier = Modifier.weight(1f).semantics { heading() })
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                T(agoLabel(year), size = 14, weight = FontWeight.SemiBold)
                T(L.s("weekday_photos", "weekday" to weekdayOf(year, day), "photos" to L.p("photos", count)), size = 13, muted = true)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(p.surface).border(1.dp, p.line, CircleShape)
                    .clickable(role = Role.Button, onClick = onShare),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.IosShare, L.s("share_year", "year" to year), tint = p.ink, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** Compact year bar that shows while its year is pinned to the top of Today's list. */
@Composable
fun YearBar(year: Int, count: Int, day: MonthDay, pinned: Boolean, onShare: () -> Unit) {
    val p = LocalPalette.current
    val alpha by animateFloatAsState(if (pinned) 1f else 0f, tween(150), label = "yearBar")
    Box(
        Modifier.zIndex(1f).fillMaxWidth().height(PinnedBarHeight)
            .graphicsLayer { this.alpha = alpha }
            .background(p.bg.copy(alpha = 0.92f))
            .then(if (pinned) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T(L.num(year), size = 26, weight = FontWeight.Black, tight = true)
            T(L.s("ago_weekday_photos", "ago" to agoLabel(year), "weekday" to weekdayOf(year, day), "photos" to L.p("photos", count)), size = 14, weight = FontWeight.Medium, muted = true, maxLines = 1, modifier = Modifier.weight(1f))
            IconButton(onClick = onShare, enabled = pinned, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.IosShare, L.s("share_year", "year" to year), tint = p.ink, modifier = Modifier.size(20.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(p.line))
    }
}

/** One year's photos for the chosen day, as a mosaic of rounded tiles. */
@Composable
fun YearMosaic(photos: List<Photo>, favorites: Set<String>, selection: Selection, onOpen: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp)) {
        val gap = 4.dp
        val row = 54.dp
        val cell = (maxWidth - gap * (COLUMNS - 1)) / COLUMNS
        val (tiles, rows) = layoutMosaic(photos.size)
        Box(Modifier.fillMaxWidth().height(row * rows + gap * (rows - 1))) {
            photos.forEachIndexed { i, photo ->
                val t = tiles[i]
                PhotoTile(
                    photo, 12.dp, photo.key in favorites, selection, onOpen = { onOpen(i) },
                    modifier = Modifier
                        .offset((cell + gap) * t.col, (row + gap) * t.row)
                        .size(cell * t.cols + gap * (t.cols - 1), row * t.rows + gap * (t.rows - 1)),
                )
            }
        }
    }
}

/** A browse grid, used for Favorites and album pages: 3 columns, edge to edge, square tiles. */
@Composable
fun SquareGrid(photos: List<Photo>, favorites: Set<String>, showYear: Boolean, onOpen: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 2.dp
        val size = (maxWidth - gap * 2) / 3
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            photos.chunked(3).forEachIndexed { r, rowPhotos ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowPhotos.forEachIndexed { c, photo ->
                        Box(Modifier.size(size)) {
                            PhotoTile(photo, 0.dp, !showYear && photo.key in favorites, null, onOpen = { onOpen(r * 3 + c) }, modifier = Modifier.fillMaxSize())
                            if (showYear) YearChip(localDate(photo.time).year, Modifier.align(Alignment.BottomStart).padding(6.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Small year label on a photo tile. */
@Composable
fun YearChip(year: Int, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(Chip).padding(horizontal = 7.dp, vertical = 2.dp).clearAndSetSemantics {}) {
        Text(
            L.num(year), color = ChipInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = if (L.lang.arabicScript) 0.sp else 0.2.sp,
        )
    }
}

/** A tilted print for the welcome screen. */
@Composable
fun Print(year: Int, label: String, @DrawableRes drawing: Int, width: Dp, angle: Float, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.width(width).rotate(angle).background(p.surface, RoundedCornerShape(10.dp)).padding(start = 8.dp, end = 8.dp, top = 8.dp),
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

    fun confirm(view: View) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** A reminder time in the phone's 12 or 24 hour style. */
@Composable
fun timeLabel(hour: Int, minute: Int): String =
    formatTime(hour, minute, android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current))

/** Strip layout: a row of year buttons that stays at the top while scrolling. */
@Composable
fun YearPills(years: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().background(p.bg).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        years.forEach { y ->
            val on = y == selected
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(50))
                    .background(if (on) p.ink else p.surface)
                    .border(1.dp, if (on) p.ink else p.line, RoundedCornerShape(50))
                    .clickable(role = Role.Tab) { onPick(y) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { T(L.num(y), size = 14, weight = FontWeight.SemiBold, color = if (on) p.bg else p.ink) }
        }
    }
}

/** Strip layout: one year, a huge year number, then two columns of photos and buttons to step between years. */
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
    val weekday = weekdayOf(year, day)
    Column(Modifier.padding(top = 12.dp)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(L.num(year), size = 96, weight = FontWeight.Black, tight = true, modifier = Modifier.semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(agoLabel(year), size = 15, weight = FontWeight.Medium)
                T(L.s("weekday_photos", "weekday" to weekday, "photos" to L.p("photos", photos.size)), size = 15, weight = FontWeight.Medium, muted = true)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            masonryColumns(photos.map { tileAspect(it.width, it.height) }).forEach { col ->
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
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                newer?.let(L::num) ?: L.s("newest"), { newer?.let(onPick) }, kind = Kind.Secondary, height = 44.dp, enabled = newer != null,
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = it, modifier = Modifier.size(15.dp)) },
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onShare).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.IosShare, null, tint = LocalPalette.current.muted, modifier = Modifier.size(16.dp))
                T(L.s("share"), size = 15, weight = FontWeight.SemiBold, muted = true)
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
