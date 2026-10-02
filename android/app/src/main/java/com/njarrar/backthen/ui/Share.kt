package com.njarrar.backthen.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.PhotoSource
import java.io.File

/** Opens the system share sheet with the photos themselves, so any app can take them. */
fun sharePhotos(context: Context, photos: List<Photo>, title: String) {
    if (photos.isEmpty()) return
    val uris = ArrayList(photos.map { p ->
        when (p.source) {
            PhotoSource.DEVICE -> Uri.parse(p.uri)
            // Imported Google photos live in app storage, so hand them out through our FileProvider.
            PhotoSource.GOOGLE -> FileProvider.getUriForFile(context, "${context.packageName}.files", File(Uri.parse(p.uri).path!!))
        }
    })
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    send.type = "image/*"
    send.putExtra(Intent.EXTRA_SUBJECT, title)
    send.clipData = ClipData.newRawUri(title, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title))
}
