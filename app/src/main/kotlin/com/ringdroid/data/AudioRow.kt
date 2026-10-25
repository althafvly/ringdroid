package com.ringdroid.data

import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** One audio file in the list, read from an [AudioQuery] cursor. */
internal data class AudioRow(
    val uri: Uri,
    val title: String?,
    val path: String?,
    val isRingtone: Boolean,
    val isNotification: Boolean,
    val isInternal: Boolean,
) {
    /**
     * Built-in sounds can't be deleted. From Android 10 MediaStore decides (asking the user where
     * needed); before that the folder must be writable.
     */
    fun canDelete(): Boolean {
        val file = path?.let { File(it) }
        if (isInternal || file == null || !file.exists()) {
            return false
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || file.parentFile?.canWrite() == true
    }

    companion object {
        /** Reads the cursor's current row; [isInternal] tells which MediaStore volume it is from. */
        fun from(
            cursor: Cursor,
            isInternal: Boolean,
        ): AudioRow {
            val collection = if (isInternal) MediaStore.Audio.Media.INTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            return AudioRow(
                uri = ContentUris.withAppendedId(collection, cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))),
                title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)),
                path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)),
                isRingtone = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE)) != 0,
                isNotification = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION)) != 0,
                isInternal = isInternal,
            )
        }
    }
}
