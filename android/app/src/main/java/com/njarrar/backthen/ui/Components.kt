package com.njarrar.backthen.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.njarrar.backthen.core.COLUMNS
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.formatTime
import com.njarrar.backthen.core.layoutMosaic
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.plural
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

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
    Text(
        text,
        modifier = modifier,
        color = color ?: if (muted) p.muted else p.ink,
        fontFamily = Nunito,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * if (size >= 30) 1.1 else 1.4).sp,
        letterSpacing = if (tight) (size * -0.03).sp else 0.sp,
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
@Immutable
data class Selection(val on: Boolean, val keys: Set<String>, val toggle: (Photo) -> Unit)

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
    val description = remember(photo.time) { "Photo from ${localDate(photo.time)}" }
    Box(
        modifier
            .clip(shape)
            .background(p.fill)
            .clickable(role = Role.Image) { if (selection?.on == true) selection.toggle(photo) else onOpen() },
    ) {
        AsyncImage(model = photo.uri, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
                contentDescription = if (picked) "Selected" else "Not selected",
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(22.dp),
            )
        }
    }
}

/** One year's photos for the chosen day: a big year number, then a mosaic. */
@Composable
fun YearSection(
    year: Int,
    photos: List<Photo>,
    day: MonthDay,
    favorites: Set<String>,
    selection: Selection,
    onOpen: (Int) -> Unit,
    onShare: () -> Unit,
) {
    val p = LocalPalette.current
    val weekday = remember(year, day) { weekdayName(year, day) }
    val ago = remember(year) { agoLabel(year) }
    val (tiles, rows) = remember(photos.size) { layoutMosaic(photos.size) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            T(year.toString(), size = 56, weight = FontWeight.Black, tight = true, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                T(ago, size = 14, weight = FontWeight.SemiBold)
                T("$weekday, ${plural(photos.size, "photo")}", size = 13, muted = true)
            }
        }
        // Places tiles in one measure pass, in place of a BoxWithConstraints that composes twice.
        Layout(
            content = {
                photos.forEachIndexed { i, photo ->
                    PhotoTile(photo, 10.dp, photo.key in favorites, selection, onOpen = { onOpen(i) })
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val gap = 4.dp.roundToPx()
            val row = 54.dp.roundToPx()
            val width = constraints.maxWidth
            val cell = (width - gap * (COLUMNS - 1)) / COLUMNS
            val placeables = measurables.mapIndexed { i, m ->
                val t = tiles[i]
                m.measure(Constraints.fixed(cell * t.cols + gap * (t.cols - 1), row * t.rows + gap * (t.rows - 1)))
            }
            layout(width, if (rows > 0) row * rows + gap * (rows - 1) else 0) {
                placeables.forEachIndexed { i, pl -> pl.placeRelative((cell + gap) * tiles[i].col, (row + gap) * tiles[i].row) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onShare).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.IosShare, null, tint = p.muted, modifier = Modifier.size(15.dp))
                T("Share $year", size = 13, weight = FontWeight.SemiBold, muted = true)
            }
        }
    }
}

/** A plain 3-column grid, used for Favorites and album pages. Adds one list item per row, so only rows on screen get drawn. */
fun LazyListScope.squareGridItems(photos: List<Photo>, favorites: Set<String>, showYear: Boolean, onOpen: (Int) -> Unit) {
    itemsIndexed(photos.chunked(3), key = { _, row -> row.first().key }, contentType = { _, _ -> "photo-row" }) { r, rowPhotos ->
        Row(
            Modifier.fillMaxWidth().padding(start = 3.dp, end = 3.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            rowPhotos.forEachIndexed { c, photo ->
                Box(Modifier.weight(1f).aspectRatio(1f)) {
                    PhotoTile(photo, 4.dp, !showYear && photo.key in favorites, null, onOpen = { onOpen(r * 3 + c) }, modifier = Modifier.fillMaxSize())
                    if (showYear) {
                        val year = remember(photo.time) { localDate(photo.time).year.toString() }
                        Box(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
                                .padding(start = 7.dp, end = 7.dp, top = 16.dp, bottom = 5.dp),
                        ) { T(year, size = 13, weight = FontWeight.Black, color = Color.White) }
                    }
                }
            }
            // Keeps the last row's tiles the same size as the others.
            repeat(3 - rowPhotos.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
        }
    }
}

/** A tilted print for the welcome screen. */
@Composable
fun Print(year: Int, label: String, color: Color, width: Dp, angle: Float, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.width(width).rotate(angle).background(p.surface, RoundedCornerShape(10.dp)).padding(start = 8.dp, end = 8.dp, top = 8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 5f).background(color.copy(alpha = 0.85f), RoundedCornerShape(6.dp)))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.Bottom) {
            T(year.toString(), size = if (width > 160.dp) 26 else 22, weight = FontWeight.Black, tight = true, modifier = Modifier.weight(1f))
            T(label, size = 11, weight = FontWeight.Medium, muted = true)
        }
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
            ) { T(y.toString(), size = 14, weight = FontWeight.SemiBold, color = if (on) p.bg else p.ink) }
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
    val weekday = remember(year, day) { weekdayName(year, day) }
    val aspects = remember(photos) { photos.map { tileAspect(it.width, it.height) } }
    val columns = remember(aspects) { masonryColumns(aspects) }
    Column(Modifier.padding(top = 12.dp)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(year.toString(), size = 96, weight = FontWeight.Black, tight = true, modifier = Modifier.semantics { heading() })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(agoLabel(year), size = 15, weight = FontWeight.Medium)
                T("$weekday, ${plural(photos.size, "photo")}", size = 15, weight = FontWeight.Medium, muted = true)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            columns.forEach { col ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    col.forEach { i ->
                        val ph = photos[i]
                        PhotoTile(
                            ph, 12.dp, ph.key in favorites, selection, onOpen = { onOpen(i) },
                            modifier = Modifier.fillMaxWidth().aspectRatio(aspects[i]),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                newer?.toString() ?: "Newest", { newer?.let(onPick) }, kind = Kind.Secondary, height = 44.dp, enabled = newer != null,
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = it, modifier = Modifier.size(15.dp)) },
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onShare).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.IosShare, null, tint = LocalPalette.current.muted, modifier = Modifier.size(16.dp))
                T("Share", size = 15, weight = FontWeight.SemiBold, muted = true)
            }
            Spacer(Modifier.weight(1f))
            Pill(
                older?.toString() ?: "Oldest", { older?.let(onPick) }, kind = Kind.Secondary, height = 44.dp, enabled = older != null,
                iconAfter = true,
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = it, modifier = Modifier.size(15.dp)) },
            )
        }
    }
}

/** "Monday" for this day in that year, or "" if the day doesn't exist then (Feb 29). */
private fun weekdayName(year: Int, day: MonthDay): String = runCatching {
    LocalDate.of(year, day.month + 1, day.day).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}.getOrDefault("")
