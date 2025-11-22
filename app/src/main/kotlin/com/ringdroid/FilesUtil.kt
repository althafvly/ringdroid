package com.ringdroid

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object FilesUtil {
    private fun getFullPathFromUri(
        context: Context,
        uri: Uri,
    ): String? {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }

        if ("content".equals(uri.scheme, ignoreCase = true)) {
            val authority = uri.authority
            if ("com.android.externalstorage.documents" == authority) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split: Array<String?> =
                    docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                if (split.size == 2 && "primary".equals(split[0], ignoreCase = true)) {
                    return Environment
                        .getExternalStorageDirectory()
                        .absolutePath + "/" + split[1]
                }
            }

            if ("com.android.providers.media.documents" == authority) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split: Array<String?> =
                    docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val mediaId = split[1]

                val contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val sel = MediaStore.Audio.Media._ID + "=?"
                val selArgs = arrayOf(mediaId)

                return getDataColumn(context, contentUri, sel, selArgs)
            }

            if ("com.android.providers.downloads.documents" == authority) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri =
                    ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        id.toLong(),
                    )
                return getDataColumn(context, contentUri, null, null)
            }

            return getDataColumn(context, uri, null, null)
        }

        return null
    }

    private fun getDataColumn(
        context: Context,
        uri: Uri,
        sel: String?,
        selArgs: Array<String?>?,
    ): String? {
        val projection = arrayOf<String?>(MediaStore.MediaColumns.DATA)
        try {
            context.contentResolver.query(uri, projection, sel, selArgs, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    return cursor.getString(idx)
                }
            }
        } catch (ignored: Exception) {
        }

        return null
    }

    @JvmStatic
    fun getFileFromUri(
        context: Context,
        uri: Uri,
    ): File? {
        val fullPath = getFullPathFromUri(context, uri)
        return if (fullPath != null) File(fullPath) else null
    }

    @JvmStatic
    fun getStackTrace(e: Exception): String {
        val writer = StringWriter()
        e.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}
