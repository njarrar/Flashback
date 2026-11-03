package com.njarrar.backthen.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.njarrar.backthen.R
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.ReelLook
import com.njarrar.backthen.core.ReelOptions
import com.njarrar.backthen.core.ReelPlan
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.filmStamp
import com.njarrar.backthen.core.formatDate
import com.njarrar.backthen.core.localDate
import com.njarrar.backthen.core.reelEase
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** The words one photo shows in a reel, worked out once in the app's language. */
class ReelCard(photo: Photo, now: LocalDate = LocalDate.now()) {
    private val day = localDate(photo.time)
    val year: String = L.num(day.year)
    val date: String = formatDate(day, "MMMMd")
    val ago: String = agoLabel(day.year, now.year)
    val short: String = formatDate(day, "yMMMd")
    val film: String = filmStamp(photo.time)
}

/** The app font, as plain Android typefaces for drawing on a canvas. */
class ReelFonts(private val ctx: Context) {
    private val arabic = L.lang.arabicScript
    private fun font(nunito: Int, tajawal: Int, fallback: Int) =
        runCatching { ResourcesCompat.getFont(ctx, if (arabic) tajawal else nunito) }.getOrNull()
            ?: Typeface.create(Typeface.DEFAULT, fallback)
    val black = font(R.font.nunito_black, R.font.tajawal_black, Typeface.BOLD)
    val heavy = font(R.font.nunito_extrabold, R.font.tajawal_extrabold, Typeface.BOLD)
    val bold = font(R.font.nunito_bold, R.font.tajawal_bold, Typeface.BOLD)
    val semibold = font(R.font.nunito_semibold, R.font.tajawal_bold, Typeface.NORMAL)
    val icon: Bitmap? = runCatching { BitmapFactory.decodeResource(ctx.resources, R.drawable.reel_icon) }.getOrNull()
}

/**
 * Draws one frame of a reel on a canvas. The editor uses it for the live preview, and
 * ReelExport for every frame of the video.
 */
class ReelRenderer(
    photos: List<Photo>,
    val options: ReelOptions,
    /** Small copies, used for the intro prints and while a full one loads. */
    private val small: List<Bitmap?>,
    private val fonts: ReelFonts,
    private val reduceMotion: Boolean,
    /** Soft blurred copies for the background behind a whole photo. */
    blurred: List<Bitmap?>? = null,
    now: LocalDate = LocalDate.now(),
) {
    val plan = ReelPlan(photos.size, options, reduceMotion)
    val cards = photos.take(ReelPlan.MAX_PHOTOS).map { ReelCard(it, now) }
    val blurred: List<Bitmap?> = blurred ?: small.map { b -> b?.let { blur(it) } }
    private val introTitle: String
    private val introSubtitle: String
    private val appName = L.s("app_name")
    private val rtl = L.lang.rtl
    private val arabic = L.lang.arabicScript

    /** Photo `i` at full size for this frame, or null to use its small copy. */
    @Volatile var image: (Int) -> Bitmap? = { null }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        val shown = photos.take(ReelPlan.MAX_PHOTOS)
        val days = shown.map { formatDate(localDate(it.time), "MMMMd") }.toSet()
        introTitle = days.singleOrNull() ?: L.s("app_name")
        val years = shown.map { localDate(it.time).year }
        val lo = years.minOrNull()
        val hi = years.maxOrNull()
        introSubtitle = if (lo != null && hi != null && lo != hi) {
            L.s("reel_years_range", "years" to L.p("years", years.toSet().size), "from" to L.num(lo), "to" to L.num(hi))
        } else lo?.let { L.num(it) } ?: ""
    }

    fun draw(c: Canvas, w: Float, h: Float, time: Double) {
        val m = plan.moment(time)
        val b = m.b
        if (b != null) move(c, w, h, m.a, b, m.progress, time) else item(c, w, h, m.a, time - m.a.start)
    }

    private fun move(c: Canvas, w: Float, h: Float, a: ReelPlan.Item, b: ReelPlan.Item, p: Double, t: Double) {
        val e = reelEase(p).toFloat()
        val ta = t - a.start
        val tb = t - b.start
        when (plan.move) {
            com.njarrar.backthen.core.ReelMove.SLIDE -> {
                // Photos come in from the side the reading goes toward.
                val dir = if (rtl) -1f else 1f
                c.save(); c.translate(-e * w * dir, 0f); item(c, w, h, a, ta); c.restore()
                c.save(); c.translate((1 - e) * w * dir, 0f); item(c, w, h, b, tb); c.restore()
            }
            com.njarrar.backthen.core.ReelMove.ZOOM -> {
                item(c, w, h, b, tb)
                layer(c, w, h, 1 - e) {
                    c.scale(1 + 0.22f * e, 1 + 0.22f * e, w / 2, h / 2)
                    item(c, w, h, a, ta)
                }
            }
            com.njarrar.backthen.core.ReelMove.FLASH -> {
                if (p < 0.5) item(c, w, h, a, ta) else item(c, w, h, b, tb)
                val white = (1 - abs(2 * p - 1)).pow(1.4) * 0.96
                c.drawColor(Color.argb((white * 255).toInt(), 255, 255, 255))
            }
            com.njarrar.backthen.core.ReelMove.FADE -> {
                item(c, w, h, a, ta)
                layer(c, w, h, e) { item(c, w, h, b, tb) }
            }
        }
    }

    private fun item(c: Canvas, w: Float, h: Float, it: ReelPlan.Item, lt: Double) {
        c.save()
        c.clipRect(0f, 0f, w, h)
        val u = (lt / it.length).coerceIn(0.0, 1.0)
        when (val k = it.kind) {
            ReelPlan.Kind.Intro -> intro(c, w, h, u)
            ReelPlan.Kind.Outro -> outro(c, w, h, u)
            is ReelPlan.Kind.Photo -> photo(c, w, h, k.index, lt, u)
        }
        c.restore()
    }

    /** Draws [body] as one layer at the given opacity. */
    private inline fun layer(c: Canvas, w: Float, h: Float, alpha: Float, body: () -> Unit) {
        if (alpha <= 0.002f) return
        val n = c.saveLayerAlpha(0f, 0f, w, h, (alpha.coerceIn(0f, 1f) * 255).toInt())
        body()
        c.restoreToCount(n)
    }

    // Cards

    private fun background(c: Canvas, w: Float, h: Float, i: Int, dark: Float) {
        c.drawColor(INK)
        blurred.getOrNull(i)?.let { cover(c, it, RectF(0f, 0f, w, h), 1.15f) }
        c.drawColor(Color.argb((dark * 255).toInt(), 13, 13, 14))
    }

    private fun intro(c: Canvas, w: Float, h: Float, u: Double) {
        background(c, w, h, 0, 0.55f)
        val a = if (reduceMotion) 1f else reelEase(min(u / 0.45, 1.0)).toFloat()
        val tall = w / h < 0.7f
        val cx = w / 2
        val cy = h * if (tall) 0.36f else 0.34f
        val pw = w * if (tall) 0.34f else 0.28f
        val ph = pw * 1.18f
        val fan = (0 until min(3, small.size)).reversed().toList()
        val n = fan.size.toFloat()
        fan.forEachIndexed { j, i ->
            val img = image(i) ?: small[i] ?: return@forEachIndexed
            val k = j - (n - 1) / 2
            layer(c, w, h, min(u * 3, 1.0).toFloat()) {
                c.translate(cx + k * pw * 0.42f * a, cy + abs(k) * w * 0.012f)
                c.rotate(k * 9 * a)
                val card = RectF(-pw / 2, -ph / 2, pw / 2, ph / 2)
                paint.reset(); paint.isAntiAlias = true
                paint.color = PAPER
                paint.setShadowLayer(w * 0.02f, 0f, w * 0.012f, Color.argb(115, 0, 0, 0))
                c.drawRoundRect(card, w * 0.012f, w * 0.012f, paint)
                paint.clearShadowLayer()
                val m = pw * 0.06f
                val pic = RectF(card.left + m, card.top + m, card.right - m, card.top + pw - m)
                c.save()
                path.reset(); path.addRoundRect(pic, w * 0.006f, w * 0.006f, Path.Direction.CW); c.clipPath(path)
                cover(c, img, pic)
                c.restore()
            }
        }
        val ta = if (reduceMotion) min(u * 3, 1.0).toFloat() else reelEase(((u - 0.15) / 0.4).coerceIn(0.0, 1.0)).toFloat()
        layer(c, w, h, ta) {
            c.translate(0f, (1 - ta) * w * 0.03f)
            var ts = w * 0.115f
            text(fonts.black, ts, Color.WHITE, -0.03f)
            val tw = textPaint.measureText(introTitle)
            if (tw > w * 0.9f) { ts *= w * 0.9f / tw; textPaint.textSize = ts }
            val ty = cy + pw * 0.75f + ts * 1.15f
            textPaint.textAlign = Paint.Align.CENTER
            c.drawText(introTitle, cx, ty, textPaint)
            text(fonts.bold, w * 0.045f, Color.argb(209, 255, 255, 255))
            textPaint.textAlign = Paint.Align.CENTER
            c.drawText(introSubtitle, cx, ty + w * 0.075f, textPaint)
        }
    }

    private fun outro(c: Canvas, w: Float, h: Float, u: Double) {
        background(c, w, h, max(0, small.size - 1), 0.72f)
        val a = if (reduceMotion) 1f else reelEase(min(u / 0.4, 1.0)).toFloat()
        layer(c, w, h, a) {
            c.translate(0f, (1 - a) * w * 0.03f)
            val s = w * 0.24f
            val cx = w / 2
            val cy = h * 0.44f
            val r = RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
            paint.reset(); paint.isAntiAlias = true; paint.color = Color.WHITE
            paint.setShadowLayer(w * 0.025f, 0f, w * 0.015f, Color.argb(102, 0, 0, 0))
            c.drawRoundRect(r, s * 0.23f, s * 0.23f, paint)
            paint.clearShadowLayer()
            fonts.icon?.let { icon ->
                c.save()
                path.reset(); path.addRoundRect(r, s * 0.23f, s * 0.23f, Path.Direction.CW); c.clipPath(path)
                paint.isFilterBitmap = true
                c.drawBitmap(icon, null, r, paint)
                c.restore()
            }
            val fs = w * 0.07f
            text(fonts.black, fs, Color.WHITE, -0.03f)
            textPaint.textAlign = Paint.Align.CENTER
            c.drawText(appName, cx, cy + s / 2 + fs * 1.4f, textPaint)
        }
    }

    private fun photo(c: Canvas, w: Float, h: Float, i: Int, lt: Double, u: Double) {
        val img = image(i) ?: small.getOrNull(i)
        if (img == null) { background(c, w, h, i, 0.45f); return }
        val kb = if (reduceMotion) 0.5f else u.toFloat()
        val dir = if (i % 2 == 1) 1f else -1f
        val tall = w / h < 0.7f
        val r: RectF
        var radius = 0f
        if (options.fill) {
            c.drawColor(INK)
            r = RectF(0f, 0f, w, h)
            cover(c, img, r, 1.05f + 0.07f * kb, dir * (kb - 0.5f) * 0.7f)
        } else {
            background(c, w, h, i, 0.45f)
            val iw = max(img.width, 1).toFloat()
            val ih = max(img.height, 1).toFloat()
            val s = min(w * 0.86f / iw, h * (if (tall) 0.54f else 0.64f) / ih)
            val pw = iw * s
            val ph = ih * s
            val cy = h * if (tall) 0.39f else 0.44f
            r = RectF((w - pw) / 2, cy - ph / 2, (w + pw) / 2, cy + ph / 2)
            radius = w * 0.028f
            paint.reset(); paint.isAntiAlias = true; paint.color = Color.BLACK
            paint.setShadowLayer(w * 0.03f, 0f, w * 0.02f, Color.argb(128, 0, 0, 0))
            c.drawRoundRect(r, radius, radius, paint)
            paint.clearShadowLayer()
            c.save()
            path.reset(); path.addRoundRect(r, radius, radius, Path.Direction.CW); c.clipPath(path)
            cover(c, img, r, 1.02f + 0.06f * kb, dir * (kb - 0.5f) * 0.5f)
            c.restore()
        }
        val a = if (reduceMotion) ((lt - 0.1) / 0.3).coerceIn(0.0, 1.0).toFloat()
        else reelEase(((lt - 0.2) / 0.55).coerceIn(0.0, 1.0)).toFloat()
        val card = cards.getOrNull(i) ?: return
        when (options.look) {
            ReelLook.YEAR -> bigYear(c, w, h, r, radius, card, a)
            ReelLook.FILM -> film(c, w, h, r, radius, card, a)
            ReelLook.CLEAN -> clean(c, w, h, r, card, a)
        }
    }

    // Date stamps. They stay in the top 80%, clear of the buttons social apps lay over the bottom.

    private fun safeBottom(r: RectF, h: Float) = min(r.bottom, h * 0.8f)

    private fun bigYear(c: Canvas, w: Float, h: Float, r: RectF, radius: Float, card: ReelCard, a: Float) {
        val ys = w * 0.2f
        val ds = w * 0.046f
        val pad = w * 0.065f
        val below = !options.fill && h * 0.8f - r.bottom > ys * 1.1f + ds * 1.6f
        val left: Float
        val right: Float
        val baseline: Float
        if (below) {
            left = r.left; right = r.right
            baseline = r.bottom + ys * 0.98f
        } else {
            val bottom = safeBottom(r, h)
            c.save()
            if (radius > 0) { path.reset(); path.addRoundRect(r, radius, radius, Path.Direction.CW); c.clipPath(path) }
            val top = bottom - ys * 2.2f
            paint.reset()
            paint.shader = LinearGradient(0f, top, 0f, r.bottom, Color.TRANSPARENT, Color.argb(153, 0, 0, 0), Shader.TileMode.CLAMP)
            c.drawRect(r.left, top, r.right, r.bottom, paint)
            paint.shader = null
            c.restore()
            left = r.left + pad; right = r.right - pad
            baseline = bottom - pad - ds * 1.7f
        }
        layer(c, w, h, a) {
            c.translate(0f, (1 - a) * w * 0.035f)
            val x = if (rtl) right else left
            val align = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            text(fonts.black, ys, Color.WHITE, -0.035f)
            textPaint.textAlign = align
            textPaint.setShadowLayer(w * 0.02f, 0f, 0f, Color.argb(64, 0, 0, 0))
            c.drawText(card.year, x, baseline, textPaint)
            text(fonts.bold, ds, Color.argb(230, 255, 255, 255))
            textPaint.textAlign = align
            textPaint.setShadowLayer(w * 0.02f, 0f, 0f, Color.argb(64, 0, 0, 0))
            c.drawText("${card.date} · ${card.ago}", x, baseline + ds * 1.55f, textPaint)
            textPaint.clearShadowLayer()
        }
    }

    private fun clean(c: Canvas, w: Float, h: Float, r: RectF, card: ReelCard, a: Float) {
        val fs = w * 0.037f
        val pad = w * 0.04f
        val first = card.short
        val second = "  ·  ${card.ago}"
        text(fonts.heavy, fs, Color.WHITE)
        val w1 = textPaint.measureText(first)
        text(fonts.semibold, fs, Color.WHITE)
        val w2 = textPaint.measureText(second)
        val ph = fs * 2.1f
        val pw = min(w1 + w2 + fs * 1.6f, r.width() - pad * 2)
        val y = max(r.top + pad, if (options.fill) h * 0.1f else 0f)
        val x = if (rtl) r.right - pad - pw else r.left + pad
        layer(c, w, h, a) {
            paint.reset(); paint.isAntiAlias = true; paint.color = Color.argb(107, 0, 0, 0)
            c.drawRoundRect(RectF(x, y, x + pw, y + ph), ph / 2, ph / 2, paint)
            c.save()
            c.clipRect(x + fs * 0.8f, y, x + pw - fs * 0.8f, y + ph)
            val base = y + ph / 2 + fs * 0.35f
            // Date first in reading order: on the left, or on the right in a right to left language.
            text(fonts.heavy, fs, Color.WHITE)
            textPaint.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            val start = if (rtl) x + pw - fs * 0.8f else x + fs * 0.8f
            c.drawText(first, start, base, textPaint)
            text(fonts.semibold, fs, Color.argb(209, 255, 255, 255))
            textPaint.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            c.drawText(second, if (rtl) start - w1 else start + w1, base, textPaint)
            c.restore()
        }
    }

    private fun film(c: Canvas, w: Float, h: Float, r: RectF, radius: Float, card: ReelCard, a: Float) {
        // A warm tint, a dark edge and a little grain, like a print from film.
        c.save()
        if (radius > 0) { path.reset(); path.addRoundRect(r, radius, radius, Path.Direction.CW); c.clipPath(path) } else c.clipRect(r)
        paint.reset()
        paint.color = Color.rgb(255, 236, 212)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        c.drawRect(r, paint)
        paint.xfermode = null
        paint.shader = RadialGradient(
            r.centerX(), r.centerY(), max(1f, hypot(r.width(), r.height()) * 0.62f),
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(97, 0, 0, 0)),
            floatArrayOf(0f, min(r.width(), r.height()) * 0.35f / max(1f, hypot(r.width(), r.height()) * 0.62f), 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(r, paint)
        paint.shader = null
        val tile = 128 * max(1f, w / 540f)
        val shader = BitmapShader(NOISE, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        shader.setLocalMatrix(android.graphics.Matrix().apply {
            setScale(tile / 128, tile / 128)
            postTranslate(-Random.nextFloat() * tile, -Random.nextFloat() * tile)
        })
        paint.shader = shader
        paint.alpha = 18
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        c.drawRect(r, paint)
        paint.reset()
        c.restore()

        val sh = w * 0.052f
        val pad = w * 0.055f
        val x = r.right - pad - sevenWidth(card.film, sh)
        val y = safeBottom(r, h) - pad - sh
        layer(c, w, h, a) {
            // Leans right like the real thing.
            c.translate((y + sh) * 0.1f, 0f)
            c.skew(-0.1f, 0f)
            paint.reset(); paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = sh * 0.13f
            paint.color = Color.argb(140, 255, 120, 20)
            paint.setShadowLayer(sh * 0.5f, 0f, 0f, Color.argb(230, 255, 110, 20))
            seven(c, card.film, x, y, sh)
            paint.clearShadowLayer()
            paint.color = Color.rgb(255, 176, 74)
            seven(c, card.film, x, y, sh)
            paint.reset()
        }
    }

    private fun sevenWidth(s: String, h: Float): Float {
        val adv = h * 0.52f + h * 0.32f
        var x = 0f
        for (ch in s) x += when (ch) { ' ' -> adv * 0.8f; '\'' -> h * 0.32f; else -> adv }
        return x - h * 0.32f
    }

    /** Digits drawn as seven bars each, like a camera's date display. */
    private fun seven(c: Canvas, s: String, x0: Float, y: Float, h: Float) {
        val w = h * 0.52f
        val th = h * 0.13f
        val gap = th * 0.55f
        val adv = w + h * 0.32f
        var x = x0
        for (ch in s) {
            if (ch == ' ') { x += adv * 0.8f; continue }
            if (ch == '\'') {
                c.drawLine(x + th, y - h * 0.05f, x + th * 0.6f, y + h * 0.22f, paint)
                x += h * 0.32f; continue
            }
            for (seg in SEGMENTS[ch] ?: "") {
                val (x1, y1, x2, y2) = when (seg) {
                    'a' -> floatArrayOf(0f, 0f, w, 0f)
                    'b' -> floatArrayOf(w, 0f, w, h / 2)
                    'c' -> floatArrayOf(w, h / 2, w, h)
                    'd' -> floatArrayOf(0f, h, w, h)
                    'e' -> floatArrayOf(0f, h / 2, 0f, h)
                    'f' -> floatArrayOf(0f, 0f, 0f, h / 2)
                    else -> floatArrayOf(0f, h / 2, w, h / 2)
                }
                val len = max(hypot(x2 - x1, y2 - y1), 0.001f)
                val ux = (x2 - x1) / len * gap
                val uy = (y2 - y1) / len * gap
                c.drawLine(x + x1 + ux, y + y1 + uy, x + x2 - ux, y + y2 - uy, paint)
            }
            x += adv
        }
    }

    // Helpers

    private fun text(face: Typeface, size: Float, color: Int, spacing: Float = 0f) {
        textPaint.reset()
        textPaint.isAntiAlias = true
        textPaint.typeface = face
        textPaint.textSize = size
        textPaint.color = color
        // Arabic letters join, so they keep normal spacing.
        textPaint.letterSpacing = if (arabic) 0f else spacing
    }

    private fun cover(c: Canvas, img: Bitmap, r: RectF, scale: Float = 1f, px: Float = 0f) {
        val iw = img.width.toFloat()
        val ih = img.height.toFloat()
        if (iw <= 0 || ih <= 0) return
        val s = max(r.width() / iw, r.height() / ih) * scale
        val dw = iw * s
        val dh = ih * s
        val left = r.left + (r.width() - dw) / 2 + px * (dw - r.width()) / 2
        val top = r.top + (r.height() - dh) / 2
        paint.reset()
        paint.isFilterBitmap = true
        paint.isAntiAlias = true
        c.drawBitmap(img, null, RectF(left, top, left + dw, top + dh), paint)
    }

    companion object {
        private val INK = Color.rgb(13, 13, 14)
        private val PAPER = Color.rgb(244, 244, 242)

        private val SEGMENTS = mapOf(
            '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
            '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
        )

        private val NOISE: Bitmap by lazy {
            val side = 128
            val px = IntArray(side * side) { val v = Random.nextInt(256); Color.rgb(v, v, v) }
            Bitmap.createBitmap(px, side, side, Bitmap.Config.ARGB_8888)
        }

        /** A soft copy for the background: shrunk hard, then grown back with smoothing. */
        fun blur(src: Bitmap): Bitmap? = runCatching {
            val aspect = src.height.toFloat() / max(1, src.width)
            val tiny = Bitmap.createScaledBitmap(src, 12, max(1, (12 * aspect).toInt()), true)
            val soft = Bitmap.createScaledBitmap(tiny, 96, max(1, (96 * aspect).toInt()), true)
            tiny.recycle()
            soft
        }.getOrNull()
    }
}
