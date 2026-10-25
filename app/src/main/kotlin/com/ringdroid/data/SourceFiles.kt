package com.ringdroid.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ringdroid.R
import com.ringdroid.util.RingdroidUtils
import java.io.File
import java.io.IOException
import java.util.Locale

/** Finds the file the editor opens, and explains why a file can't be decoded. */
internal object SourceFiles {
    private const val TAG = "SourceFiles"

    /**
     * Returns the file for an editor URI: the MediaStore file of a list row, a file:// URI, or a
     * copy of another content:// URI. The app's own private files (other than shared copies in the
     * cache) are refused.
     */
    fun resolve(context: Context, data: Uri): File? {
        val file =
            when (data.scheme) {
                "content" -> RingdroidUtils.getFileFromUri(context, data)
                "file" -> data.path?.let { File(it) }
                else -> null
            } ?: return null
        return try {
            val path = file.canonicalPath
            val dataDir = File(context.applicationInfo.dataDir).canonicalPath + "/"
            val cache = context.cacheDir.canonicalPath + "/"
            if (path.startsWith(dataDir) && !path.startsWith(cache)) null else file
        } catch (e: IOException) {
            Log.w(TAG, "Can't resolve $data", e)
            null
        }
    }

    fun unsupportedMessage(context: Context, file: File?): String {
        val name = file?.name.orEmpty().lowercase(Locale.ROOT)
        return if (!name.contains('.')) {
            context.getString(R.string.no_extension_error)
        } else {
            context.getString(R.string.bad_extension_error) + " " + name.substringAfterLast('.')
        }
    }
}
