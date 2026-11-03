package com.njarrar.backthen.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

/** Reads a photo with its long side at about [target] pixels, turned the right way up. */
fun decodePhoto(context: Context, uri: Uri, target: Int): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 29) {
        return@runCatching context.contentResolver.loadThumbnail(uri, Size(target, target), null)
    }
    if (Build.VERSION.SDK_INT >= 28) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return@runCatching ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
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
    val bitmap = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
    val degrees = when (open()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    if (degrees == 0) return@runCatching bitmap
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}.getOrNull()
