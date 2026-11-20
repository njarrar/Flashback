package com.njarrar.backthen.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.njarrar.backthen.core.Photo
import java.io.File

/**
 * Opens the system share sheet with the photos themselves, so any app can take them.
 * Returns false if sharing fails (e.g., missing receiver, work-profile block, or revoked URI).
 */
fun sharePhotos(context: Context, photos: List<Photo>, title: String): Boolean {
    if (photos.isEmpty()) return false
    return runCatching {
        val uris = ArrayList(photos.map { Uri.parse(it.uri) })
        if (uris.isEmpty()) return@runCatching false
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = "image/*"
        send.putExtra(Intent.EXTRA_SUBJECT, title)
        send.clipData = ClipData.newRawUri(title, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    }.getOrDefault(false)
}

/**
 * Opens the system share sheet with a finished reel.
 * Returns false if FileProvider or startActivity fails.
 */
fun shareVideo(context: Context, file: File, title: String): Boolean {
    return runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.reels", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("video/mp4")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(title, uri)
        val chooser = Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    }.getOrDefault(false)
}
