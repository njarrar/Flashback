package com.njarrar.backthen.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

/** Reads a photo with its long side at about [target] pixels, turned the right way up. */
fun decodePhoto(
    context: Context,
    uri: Uri,
    target: Int,
    signal: CancellationSignal? = null,
): Bitmap? {
    if (target <= 0 || signal?.isCanceled == true) return null
    return try {
        if (Build.VERSION.SDK_INT >= 29 && target <= 640) {
            val thumb = runCatching {
                context.contentResolver.loadThumbnail(uri, Size(target, target), signal)
            }.getOrNull()
            if (thumb != null) return thumb
        }
        if (Build.VERSION.SDK_INT >= 28) {
            if (signal?.isCanceled == true) return null
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val s = max(info.size.width, info.size.height).toFloat() / target
                if (s > 1f) {
                    val tw = max(1, (info.size.width / s).roundToInt())
                    val th = max(1, (info.size.height / s).roundToInt())
                    decoder.setTargetSize(tw, th)
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        // Android 8 and 9: sample down while decoding, then turn/flip and scale in one pass.
        if (signal?.isCanceled == true) return null
        fun open() = context.contentResolver.openInputStream(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        if (bounds.outWidth.toLong() * bounds.outHeight.toLong() / (sample.toLong() * sample.toLong()) > 8_000_000L) {
            sample *= 2
        }
        if (signal?.isCanceled == true) return null
        val bitmap = open()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val orientation = open()?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        var needsTransform = true
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> needsTransform = false
        }
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide > target) {
            val scale = target.toFloat() / longSide
            matrix.postScale(scale, scale)
            needsTransform = true
        }
        if (!needsTransform) return bitmap
        val transformed = try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (t: Throwable) {
            bitmap.recycle()
            throw t
        }
        if (transformed !== bitmap) bitmap.recycle()
        transformed
    } catch (oom: OutOfMemoryError) {
        Log.w("Flashback", "decodePhoto OOM for target=$target", oom)
        null
    } catch (_: Throwable) {
        null
    }
}
