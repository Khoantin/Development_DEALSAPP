
package com.example.androidapp.utils

import java.text.DateFormat
import java.util.Date

object NoteTimestamp {

    fun now(): Long {
        return System.currentTimeMillis()
    }

    fun creationTime(
        existingCreatedAt: Long?,
        currentTime: Long
    ): Long {
        return existingCreatedAt ?: currentTime
    }

    fun format(timestamp: Long): String {
        return DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT
        ).format(Date(timestamp))
    }
}
