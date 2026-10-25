package com.ringdroid.viewmodel

import android.app.Application
import android.database.Cursor
import android.database.MergeCursor
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import com.ringdroid.data.AudioQuery
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.util.ListenerEvents
import com.ringdroid.util.RingdroidUtils

/** Queries the audio list in the background and owns the resulting cursor across recreation. */
internal class SelectViewModel(application: Application) : AndroidViewModel(application) {
    fun interface Listener {
        fun onAudioLoaded()
    }

    val events = ListenerEvents<Listener>()

    var showAll = false

    /** Internal sounds, then external ones; null before the first load or when nothing matched. */
    var cursor: Cursor? = null
        private set
    var internalRowCount = 0
        private set
    var loaded = false
        private set

    private var generation = 0
    private var cleared = false

    fun load(filter: String?) {
        val loadGeneration = ++generation
        val query =
            AudioQuery.build(
                showAll,
                filter,
                SoundFile.getSupportedExtensions(),
                storageRoots(),
            )
        val showingAll = showAll
        val resolver = getApplication<Application>().contentResolver
        RingdroidUtils.BACKGROUND.execute {
            var internal: Cursor? = null
            var external: Cursor? = null

            fun failed(e: RuntimeException) {
                Log.e(TAG, "Loader error", e)
                internal?.close()
                external?.close()
            }
            try {
                internal = query.run(resolver, MediaStore.Audio.Media.INTERNAL_CONTENT_URI)
                external = query.run(resolver, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            } catch (e: SecurityException) {
                failed(e)
                return@execute
            } catch (e: IllegalArgumentException) {
                failed(e)
                return@execute
            } catch (e: IllegalStateException) {
                failed(e)
                return@execute
            } catch (e: UnsupportedOperationException) {
                failed(e)
                return@execute
            }
            val internalResult = internal
            val externalResult = external
            events.runOnMain {
                // Drop results of a search that has since been replaced.
                if (loadGeneration != generation || cleared) {
                    internalResult?.close()
                    externalResult?.close()
                } else {
                    show(internalResult, externalResult, showingAll)
                }
            }
        }
    }

    private fun show(internal: Cursor?, external: Cursor?, showingAll: Boolean) {
        val old = cursor
        val internalCount = internal?.count ?: 0
        if (internalCount == 0 && (external?.count ?: 0) == 0 && !showingAll) {
            internal?.close()
            external?.close()
            cursor = null
            internalRowCount = 0
        } else {
            cursor = MergeCursor(listOfNotNull(internal, external).toTypedArray())
            internalRowCount = internalCount
        }
        loaded = true
        events.listener?.onAudioLoaded()
        old?.close()
    }

    private fun storageRoots(): List<String> {
        val roots = mutableListOf(Environment.getExternalStorageDirectory().path)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getApplication<Application>()
                .getSystemService<StorageManager>()
                ?.storageVolumes
                ?.mapNotNullTo(
                    roots,
                ) {
                    it.directory?.absolutePath
                }
        }
        return roots
    }

    override fun onCleared() {
        cleared = true
        cursor?.close()
        cursor = null
    }

    private companion object {
        const val TAG = "SelectViewModel"
    }
}
