package com.ringdroid.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.ringdroid.data.RingtoneSaver
import com.ringdroid.dialog.FileSaveDialog
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.util.ListenerEvents
import com.ringdroid.util.RingdroidUtils

/** Saves the editor's selection in the background and keeps the result across recreation. */
internal class SaveViewModel(
    application: Application,
) : AndroidViewModel(application) {
    fun interface Listener {
        fun onSaveStateChanged()
    }

    val events = ListenerEvents<Listener>()

    var isSaving = false
        private set
    var fileKind = FileSaveDialog.FILE_KIND_RINGTONE
        private set

    /** The saved file, until the user has chosen what to do with it. */
    var savedUri: Uri? = null
    private var errorId = 0

    /** Returns the message of a failed save once, or null. */
    fun takeError(): Int? = errorId.takeIf { it != 0 }?.also { errorId = 0 }

    fun save(
        soundFile: SoundFile,
        title: CharSequence,
        gain: Float,
        kind: Int,
        startFrame: Int,
        numFrames: Int,
        durationMs: Int,
    ) {
        isSaving = true
        fileKind = kind
        savedUri = null
        val saver = RingtoneSaver(getApplication(), soundFile, gain, kind)
        RingdroidUtils.BACKGROUND.execute {
            try {
                val uri = saver.save(title, startFrame, numFrames, durationMs)
                finish { savedUri = uri }
            } catch (e: RingtoneSaver.SaveException) {
                Log.e(TAG, "Save failed", e.cause)
                finish { errorId = e.messageId }
            }
        }
    }

    private fun finish(update: () -> Unit) {
        events.runOnMain {
            isSaving = false
            update()
        }
        events.postIfAttached { it.onSaveStateChanged() }
    }

    private companion object {
        const val TAG = "SaveViewModel"
    }
}
