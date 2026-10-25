package com.ringdroid.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/** Shares the test audio like another app would; the last path segment is its display name. */
class SharedAudioProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
        addRow(arrayOf<Any?>(uri.lastPathSegment, audioFile().length()))
    }

    override fun getType(uri: Uri) = "audio/x-wav"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(audioFile(), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    @Synchronized
    private fun audioFile(): File {
        val context = requireNotNull(context)
        val file = File(context.cacheDir, "shared_test_audio.wav")
        if (!file.exists()) {
            val temp = File(context.cacheDir, "shared_test_audio.tmp")
            val input = context.assets.open("test_audio.wav")
            val output = FileOutputStream(temp)
            try {
                val buffer = ByteArray(BUFFER_SIZE)
                var read = input.read(buffer)
                while (read >= 0) {
                    output.write(buffer, 0, read)
                    read = input.read(buffer)
                }
            } finally {
                output.close()
                input.close()
            }
            temp.renameTo(file)
        }
        return file
    }

    companion object {
        private const val BUFFER_SIZE = 8192

        fun uri(testPackage: String, displayName: String): Uri =
            Uri.parse("content://$testPackage.sharedaudio/$displayName")
    }
}
