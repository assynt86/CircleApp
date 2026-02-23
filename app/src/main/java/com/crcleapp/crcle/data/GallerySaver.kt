package com.crcleapp.crcle.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

fun saveJpegToGallery(
    context: Context,
    bytes: ByteArray,
    displayNameNoExt: String,
    relativePath: String
): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayNameNoExt.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
    }

    return try {
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?.also { uri ->
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytes)
                }
            }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
