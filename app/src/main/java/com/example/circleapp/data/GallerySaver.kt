package com.example.circleapp.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * Saves JPEG bytes into the user's Gallery using MediaStore.
 * Returns the Uri of the saved image (or null if failed).
 */
fun saveJpegToGallery(
    context: Context,
    bytes: ByteArray,
    displayNameNoExt: String,
    relativePath: String = "Pictures/Circle"
): Uri? {
    val resolver = context.contentResolver

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayNameNoExt.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        // This puts it in Gallery under: Pictures/Circle
        put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
    }

    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return null

    resolver.openOutputStream(uri)?.use { output ->
        output.write(bytes)
        output.flush()
    } ?: return null

    return uri
}
