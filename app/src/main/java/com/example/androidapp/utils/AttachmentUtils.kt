package com.example.androidapp.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.androidapp.data.PendingAttachment

// Reads the display name, size and type of a file chosen with the system picker.
fun readPendingAttachment(context: Context, uri: Uri): PendingAttachment {
    val name = getFileNameFromUri(context, uri) ?: "file"

    var size = 0L
    try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    size = cursor.getLong(index)
                }
            }
    } catch (_: Exception) {
        // Unknown size: the Storage rules still enforce the 10 MB limit.
    }

    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    return PendingAttachment(uri.toString(), name, size, mimeType)
}