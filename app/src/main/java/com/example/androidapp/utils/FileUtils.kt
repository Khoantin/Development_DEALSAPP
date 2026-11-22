package com.example.androidapp.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

// This gets the file name from URi, this is good for our value
fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    if (fileName == null) {
        fileName = uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        }
    }
    return fileName
}
