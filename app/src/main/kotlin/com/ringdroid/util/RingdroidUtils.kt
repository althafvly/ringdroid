package com.ringdroid.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ringdroid.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object RingdroidUtils {
    private const val TAG = "RingdroidUtils"

    val BACKGROUND: ExecutorService = Executors.newCachedThreadPool()

    fun setTransparentBackground(dialog: Dialog) {
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    }

    val externalAudioCollectionUri: Uri
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

    val internalAudioCollectionUri: Uri
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_INTERNAL)
            } else {
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI
            }

    fun applyStatusBarInsets(toolbarContainer: View?) {
        if (toolbarContainer == null) return
        ViewCompat.setOnApplyWindowInsetsListener(toolbarContainer) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            v.setPadding(v.paddingLeft, insets.top, v.paddingRight, v.paddingBottom)
            windowInsets
        }
    }

    fun applyNavigationBarInsets(
        view: View?,
        extraPaddingBottomDp: Int,
    ) {
        if (view == null) return
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val extraPx = (extraPaddingBottomDp * v.resources.displayMetrics.density).toInt()
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, insets.bottom + extraPx)
            windowInsets
        }
    }

    fun showKeyboard(view: View?) {
        if (view == null) return
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard(view: View?) {
        if (view == null) return
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // Strips any path from a file name supplied by another app.
    fun toSafeFileName(
        name: String?,
        fallback: String,
    ): String {
        val safeName = name?.substringAfterLast('/') ?: return fallback
        return if (safeName.isEmpty() || safeName == "." || safeName == "..") fallback else safeName
    }

    /**
     * Returns a readable file for [uri]: the file itself for file:// and MediaStore URIs, otherwise
     * a copy in the cache. Only the latest shared copy is kept.
     */
    fun getFileFromUri(
        context: Context?,
        uri: Uri?,
    ): File? {
        if (context == null || uri == null) return null
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path?.let { File(it) }
        }
        val resolver = context.contentResolver

        // Only MediaStore is trusted to report a real file path.
        if (uri.authority == MediaStore.AUTHORITY) {
            try {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (cursor.moveToFirst() && idx >= 0) {
                        val file = cursor.getString(idx)?.let { File(it) }
                        if (file != null && file.exists() && file.canRead()) {
                            return file
                        }
                    }
                }
            } catch (ignored: SecurityException) {
            } catch (ignored: IllegalArgumentException) {
            }
        }

        var fileName = "shared_audio"
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) {
                    fileName = toSafeFileName(cursor.getString(idx), fileName)
                }
            }
        } catch (ignored: SecurityException) {
        } catch (ignored: IllegalArgumentException) {
        }
        if (!fileName.contains('.')) {
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri))
            if (extension != null) {
                fileName += ".$extension"
            }
        }

        val sharedDir = File(context.cacheDir, "shared")
        sharedDir.listFiles()?.forEach { it.delete() }
        sharedDir.mkdirs()
        val cacheFile = File(sharedDir, fileName)
        return try {
            val input = resolver.openInputStream(uri) ?: return null
            input.use { inp -> FileOutputStream(cacheFile).use { out -> inp.copyTo(out) } }
            cacheFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy content URI to cache: $uri", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to copy content URI to cache: $uri", e)
            null
        }
    }
}
