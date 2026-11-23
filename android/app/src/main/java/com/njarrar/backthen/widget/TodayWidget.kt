package com.njarrar.backthen.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.util.SizeF
import android.widget.RemoteViews
import com.njarrar.backthen.MainActivity
import com.njarrar.backthen.R
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.MonthDay
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.fullDate
import com.njarrar.backthen.core.label
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.startLanguage
import com.njarrar.backthen.core.yearsForDay
import com.njarrar.backthen.data.DeviceLibrary
import com.njarrar.backthen.data.StateFile
import com.njarrar.backthen.data.decodePhoto
import com.njarrar.backthen.data.visibleIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Home screen widget: a photo from this day in a past year, with its year and how long ago.
 * A tap opens Today. It picks another photo from the day every hour, and redraws when the
 * app goes to the background, so hidden days and new photos show up.
 */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(UPDATE_TIMEOUT_MS) {
                    runCatching { draw(context.applicationContext, manager, ids) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        // Resized: crop the photo again for the new shape.
        onUpdate(context, manager, intArrayOf(id))
    }

    companion object {
        private const val UPDATE_TIMEOUT_MS = 8_000L

        /** Redraws every widget on the home screen. Does nothing when there are none. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, TodayWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}

/** What the widget has to show today. */
private sealed interface Shown {
    /** Not set up yet, or no photo source the widget can read. */
    data object NeedsApp : Shown
    /** No photos from this day, or the day is hidden. */
    data object Empty : Shown
    data class Candidates(val photos: List<Pair<Photo, Int>>, val startIndex: Int) : Shown
}

private data class DecodedCandidate(val photo: Photo, val year: Int, val bitmap: Bitmap)

private suspend fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
    startLanguage(context)
    val today = MonthDay.today()
    // Ask MainActivity to open whatever "today" is when tapped, never a stale cached date.
    val tap = PendingIntent.getActivity(
        context, 2,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val rootId = if (Build.VERSION.SDK_INT >= 31) android.R.id.background else R.id.widget_root
    val shown = pick(context, today)
    // Decode once across all widget instances, trying up to 5 candidates before falling back to Empty.
    val signal = CancellationSignal()
    val handle = coroutineContext.job.invokeOnCompletion { signal.cancel() }
    val decoded = try {
        (shown as? Shown.Candidates)?.let { pickDecoded(context, it, signal) }
    } finally {
        handle.dispose()
    }
    val density = context.resources.displayMetrics.density
    val preApi31CornerPx = if (Build.VERSION.SDK_INT < 31) 20f * density else 0f
    try {
        for (id in ids) {
            runCatching {
                var cropped: Bitmap? = null
                try {
                    val views = if (decoded != null) {
                        val (w, h) = sizeOf(context, manager.getAppWidgetOptions(id))
                        cropped = cropForWidget(decoded.bitmap, w, h, preApi31CornerPx)
                        if (cropped != null) {
                            photoViews(context, decoded.photo, decoded.year, cropped)
                        } else {
                            messageViews(context, today, Shown.Empty)
                        }
                    } else {
                        messageViews(context, today, if (shown is Shown.NeedsApp) Shown.NeedsApp else Shown.Empty)
                    }
                    views.setOnClickPendingIntent(rootId, tap)
                    manager.updateAppWidget(id, views)
                } finally {
                    cropped?.let { if (!it.isRecycled) it.recycle() }
                }
            }
        }
    } finally {
        decoded?.bitmap?.recycle()
    }
}

private fun pickDecoded(context: Context, candidates: Shown.Candidates, signal: CancellationSignal): DecodedCandidate? {
    val attempts = minOf(MAX_DECODE_CANDIDATES, candidates.photos.size)
    for (offset in 0 until attempts) {
        if (signal.isCanceled) return null
        val idx = (candidates.startIndex + offset) % candidates.photos.size
        val (photo, year) = candidates.photos[idx]
        val bmp = decode(context, photo, signal)
        if (bmp != null) return DecodedCandidate(photo, year, bmp)
    }
    return null
}

/** Candidate photos from today in past years, the same rules as Today: hidden days stay hidden. */
private suspend fun pick(context: Context, today: MonthDay): Shown {
    val state = StateFile(context).read()
    val library = DeviceLibrary(context)
    val device = state.sources.device && library.hasAccess()
    if (!state.onboarded || !device) return Shown.NeedsApp
    val years = yearsForDay(visibleIndex(state), today)
    if (years.isEmpty()) return Shown.Empty
    val byYear = runCatching { library.photosOn(years, today) }.getOrDefault(emptyMap())
    // Oldest year first (aligned with iOS widget rotation).
    val photos = years.reversed().flatMap { y -> byYear[y].orEmpty().sortedBy { it.time }.map { it to y } }
    if (photos.isEmpty()) return Shown.Empty
    // A new photo each hour, in a set order through the day.
    val now = LocalDateTime.now()
    val hourSlot = now.toLocalDate().toEpochDay() * 24L + now.hour
    val startIndex = Math.floorMod(hourSlot, photos.size.toLong()).toInt()
    return Shown.Candidates(photos, startIndex)
}

private fun photoViews(context: Context, photo: Photo, year: Int, bitmap: Bitmap) =
    RemoteViews(context.packageName, R.layout.widget_today).apply {
        setImageViewBitmap(R.id.widget_photo, bitmap)
        setContentDescription(R.id.widget_photo, L.s("photo_from", "date" to fullDate(year, MonthDay.of(localDate(photo.time)))))
        setTextViewText(R.id.widget_year, L.num(year))
        setTextViewText(R.id.widget_ago, agoLabel(year))
    }

private fun messageViews(context: Context, today: MonthDay, shown: Shown) =
    RemoteViews(context.packageName, R.layout.widget_today_empty).apply {
        setTextViewText(R.id.widget_day, today.label())
        setTextViewText(R.id.widget_note, L.s(if (shown == Shown.NeedsApp) "widget_open_app" else "widget_empty"))
    }

/** The widget's size in pixels, from OPTION_APPWIDGET_SIZES (API 31+) or min/max launcher bounds. */
private fun sizeOf(context: Context, options: Bundle): Pair<Int, Int> {
    val density = context.resources.displayMetrics.density
    if (Build.VERSION.SDK_INT >= 31) {
        @Suppress("DEPRECATION")
        val sizes = if (Build.VERSION.SDK_INT >= 33) {
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
        } else {
            options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        }
        val best = sizes?.maxByOrNull { it.width * it.height }
        if (best != null && best.width > 0f && best.height > 0f) {
            return (best.width * density).roundToInt() to (best.height * density).roundToInt()
        }
    }
    val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    val wDp = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
    val hDp = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    val w = (wDp.takeIf { it > 0 } ?: 160) * density
    val h = (hDp.takeIf { it > 0 } ?: 160) * density
    return w.roundToInt() to h.roundToInt()
}

// Widget pictures cross to the launcher in one Binder IPC transaction (shared ~1 MB limit).
// Keep cropped RGB_565 bitmaps <= 500 KB and <= 640 px on the long side.
private const val MAX_SIDE = 640
private const val MAX_BYTES = 500_000
private const val MAX_DECODE_CANDIDATES = 5

/** Crops and scales [full] to the widget's shape within Binder byte budget, recycling intermediates. */
private fun cropForWidget(full: Bitmap, widgetW: Int, widgetH: Int, cornerRadiusPx: Float = 0f): Bitmap? = runCatching {
    if (full.width <= 0 || full.height <= 0) return null
    val safeW = max(1, widgetW)
    val safeH = max(1, widgetH)
    val aspect = safeW.toFloat() / safeH
    val (rawCw, rawCh) = if (full.width.toFloat() / full.height > aspect) {
        (full.height * aspect).roundToInt() to full.height
    } else {
        full.width to (full.width / aspect).roundToInt()
    }
    val cw = rawCw.coerceIn(1, full.width)
    val ch = rawCh.coerceIn(1, full.height)
    val needsAlpha = cornerRadiusPx > 0f
    val bytesPerPixel = if (needsAlpha) 4L else 2L
    val targetLong = minOf(MAX_SIDE, max(safeW, safeH).coerceAtLeast(160))
    var scale = minOf(1f, targetLong.toFloat() / max(cw, ch))
    val outW = max(1, (cw * scale).roundToInt())
    val outH = max(1, (ch * scale).roundToInt())
    val bytes = outW.toLong() * outH.toLong() * bytesPerPixel
    if (bytes > MAX_BYTES) {
        scale *= sqrt(MAX_BYTES.toDouble() / bytes.toDouble()).toFloat()
    }
    val matrix = Matrix().apply { setScale(scale, scale) }
    val x = ((full.width - cw) / 2).coerceIn(0, full.width - cw)
    val y = ((full.height - ch) / 2).coerceIn(0, full.height - ch)
    val cropped = Bitmap.createBitmap(full, x, y, cw, ch, matrix, true)
    try {
        if (needsAlpha) {
            val rounded = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(rounded)
            val radius = cornerRadiusPx * (cropped.width.toFloat() / safeW)
            val path = Path().apply {
                addRoundRect(RectF(0f, 0f, cropped.width.toFloat(), cropped.height.toFloat()), radius, radius, Path.Direction.CW)
            }
            canvas.clipPath(path)
            canvas.drawBitmap(cropped, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            rounded
        } else {
            cropped.copy(Bitmap.Config.RGB_565, false)
        }
    } finally {
        if (cropped !== full) cropped.recycle()
    }
}.getOrNull()

/** Reads the photo at about twice [MAX_SIDE], turned the right way up. */
private fun decode(context: Context, photo: Photo, signal: CancellationSignal): Bitmap? =
    decodePhoto(context, Uri.parse(photo.uri), MAX_SIDE * 2, signal)
