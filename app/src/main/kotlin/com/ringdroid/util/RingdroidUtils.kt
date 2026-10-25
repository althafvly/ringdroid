package com.ringdroid.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object RingdroidUtils {
    private const val TAG = "RingdroidUtils"

    val BACKGROUND: ExecutorService = Executors.newCachedThreadPool()

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
        return if ("file".equals(uri.scheme, ignoreCase = true)) {
            uri.path?.let { File(it) }
        } else {
            mediaStoreFile(context.contentResolver, uri) ?: copyToCache(context, uri)
        }
    }

    // Only MediaStore is trusted to report a real file path.
    private fun mediaStoreFile(
        resolver: ContentResolver,
        uri: Uri,
    ): File? =
        queryString(resolver, uri.takeIf { it.authority == MediaStore.AUTHORITY }, MediaStore.MediaColumns.DATA)
            ?.let { File(it) }
            ?.takeIf { it.exists() && it.canRead() }

    // Copies [uri] into the cache, keeping only the latest shared copy.
    private fun copyToCache(
        context: Context,
        uri: Uri,
    ): File? {
        val resolver = context.contentResolver
        var fileName = toSafeFileName(queryString(resolver, uri, OpenableColumns.DISPLAY_NAME), "shared_audio")
        if (!fileName.contains('.')) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri))?.let { fileName += ".$it" }
        }

        val sharedDir = File(context.cacheDir, "shared")
        sharedDir.listFiles()?.forEach { it.delete() }
        sharedDir.mkdirs()
        val cacheFile = File(sharedDir, fileName)
        return try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { input.copyTo(it) }
                cacheFile
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy content URI to cache: $uri", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to copy content URI to cache: $uri", e)
            null
        }
    }

    // The first row's value of [column], or null.
    private fun queryString(
        resolver: ContentResolver,
        uri: Uri?,
        column: String,
    ): String? =
        try {
            uri?.let { resolver.query(it, arrayOf(column), null, null, null) }?.use { cursor ->
                val idx = cursor.getColumnIndex(column)
                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
            }
        } catch (ignored: SecurityException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        }
}
