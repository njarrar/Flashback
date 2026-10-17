package com.njarrar.backthen.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Size
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
import com.njarrar.backthen.data.EXTRA_DAY
import com.njarrar.backthen.data.EXTRA_MONTH
import com.njarrar.backthen.data.StateFile
import com.njarrar.backthen.data.visibleIndex
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                runCatching { draw(context.applicationContext, manager, ids) }
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
    data class One(val photo: Photo, val year: Int) : Shown
}

private suspend fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
    startLanguage(context)
    val today = MonthDay.today()
    val tap = PendingIntent.getActivity(
        context, 2,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_MONTH, today.month)
            .putExtra(EXTRA_DAY, today.day)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val shown = pick(context, today)
    for (id in ids) {
        val views = (shown as? Shown.One)?.let { one ->
            val (w, h) = sizeOf(context, manager.getAppWidgetOptions(id))
            load(context, one.photo, w, h)?.let { bitmap -> photoViews(context, one, bitmap) }
        } ?: messageViews(context, today, shown)
        views.setOnClickPendingIntent(R.id.widget_root, tap)
        manager.updateAppWidget(id, views)
    }
}

/** One photo from today in a past year, the same rules as Today: hidden days stay hidden. */
private suspend fun pick(context: Context, today: MonthDay): Shown {
    val state = StateFile(context).read()
    val library = DeviceLibrary(context)
    val device = state.sources.device && library.hasAccess()
    if (!state.onboarded || !device) return Shown.NeedsApp
    val years = yearsForDay(visibleIndex(state), today)
    if (years.isEmpty()) return Shown.Empty
    val byYear = runCatching { library.photosOn(years, today) }.getOrDefault(emptyMap())
    val photos = years.flatMap { y -> byYear[y].orEmpty().sortedBy { it.time }.map { it to y } }
    if (photos.isEmpty()) return Shown.Empty
    // A new photo each hour, in a set order through the day.
    val now = LocalDateTime.now()
    val (photo, year) = photos[((now.toLocalDate().toEpochDay() * 24 + now.hour) % photos.size).toInt()]
    return Shown.One(photo, year)
}

private fun photoViews(context: Context, one: Shown.One, bitmap: Bitmap) =
    RemoteViews(context.packageName, R.layout.widget_today).apply {
        setImageViewBitmap(R.id.widget_photo, bitmap)
        setContentDescription(R.id.widget_photo, L.s("photo_from", "date" to fullDate(one.year, MonthDay.of(localDate(one.photo.time)))))
        setTextViewText(R.id.widget_year, L.num(one.year))
        setTextViewText(R.id.widget_ago, agoLabel(one.year))
    }

private fun messageViews(context: Context, today: MonthDay, shown: Shown) =
    RemoteViews(context.packageName, R.layout.widget_today_empty).apply {
        setTextViewText(R.id.widget_day, today.label())
        setTextViewText(R.id.widget_note, L.s(if (shown == Shown.NeedsApp) "widget_open_app" else "widget_empty"))
    }

/** The widget's size in pixels, from what the launcher reports for the current orientation. */
private fun sizeOf(context: Context, options: Bundle): Pair<Int, Int> {
    val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    val wDp = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
    val hDp = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    val density = context.resources.displayMetrics.density
    val w = (wDp.takeIf { it > 0 } ?: 160) * density
    val h = (hDp.takeIf { it > 0 } ?: 160) * density
    return w.roundToInt() to h.roundToInt()
}

// Widget pictures cross to the launcher in one message, which can't be much over 1 MB.
// Cropped to the widget's shape, at most this many pixels on the long side, and 2 bytes a pixel.
private const val MAX_SIDE = 640

/** The photo cropped to the widget's shape and made small enough to send. Null if it can't be read. */
private fun load(context: Context, photo: Photo, widgetW: Int, widgetH: Int): Bitmap? = runCatching {
    val full = decode(context, photo) ?: return null
    val aspect = widgetW.toFloat() / max(1, widgetH)
    // Center crop to the widget's aspect.
    val (cw, ch) = if (full.width.toFloat() / full.height > aspect) {
        (full.height * aspect).roundToInt() to full.height
    } else {
        full.width to (full.width / aspect).roundToInt()
    }
    val scale = minOf(1f, MAX_SIDE.toFloat() / max(cw, ch))
    val matrix = Matrix().apply { setScale(scale, scale) }
    val cropped = Bitmap.createBitmap(full, (full.width - cw) / 2, (full.height - ch) / 2, cw, ch, matrix, true)
    cropped.copy(Bitmap.Config.RGB_565, false)
}.getOrNull()

/** Reads the photo at about twice [MAX_SIDE], turned the right way up. */
private fun decode(context: Context, photo: Photo): Bitmap? {
    val target = MAX_SIDE * 2
    val uri = Uri.parse(photo.uri)
    if (Build.VERSION.SDK_INT >= 29) {
        return context.contentResolver.loadThumbnail(uri, Size(target, target), null)
    }
    if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val s = max(info.size.width, info.size.height).toFloat() / target
            if (s > 1) decoder.setTargetSize((info.size.width / s).roundToInt(), (info.size.height / s).roundToInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    // Android 8 and 9: sample down while decoding, then turn by the photo's EXIF tag.
    fun open() = context.contentResolver.openInputStream(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
    val bitmap = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val degrees = when (open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    if (degrees == 0) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}
