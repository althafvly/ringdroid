package com.ringdroid.activity

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.ringdroid.R
import com.ringdroid.data.SongMetadataReader
import com.ringdroid.dialog.LoadingDialog
import com.ringdroid.dialog.RecordingDialog
import com.ringdroid.soundfile.SamplePlayer
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.soundfile.SoundRecorder
import com.ringdroid.util.PermissionUtils
import com.ringdroid.util.RingdroidUtils
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Opens the editor's sound: decodes a file or records from the microphone on a background
 * thread, with a progress dialog that can cancel. Must be created while the activity is being
 * initialized.
 */
internal class SoundLoader(
    private val activity: ComponentActivity,
    private val listener: Listener,
) {
    interface Listener {
        fun onMetadata(
            title: String,
            artist: String?,
        )

        fun onLoaded(
            soundFile: SoundFile,
            player: SamplePlayer,
        )

        /** Shows [message] and closes the editor. */
        fun onFatalError(
            cause: Throwable?,
            message: CharSequence,
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val micPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (PermissionUtils.hasMicPermissions(activity)) {
                startRecording()
            } else {
                Toast.makeText(activity, R.string.required_mic_permission, Toast.LENGTH_LONG).show()
                activity.finish()
            }
        }

    @Volatile private var keepGoing = false

    @Volatile private var finishRequested = false
    private var loadingDialog: LoadingDialog? = null
    private var recordingDialog: RecordingDialog? = null

    /** Stops any loading or recording; called when the editor is destroyed. */
    fun cancel() {
        keepGoing = false
        loadingDialog?.dismiss()
        recordingDialog?.dismiss()
    }

    /** Resolves [data] to a file, reads its tags and decodes it, all off the main thread. */
    fun load(data: Uri) {
        keepGoing = true
        finishRequested = false
        val dialog = LoadingDialog(activity) { requestFinish() }
        loadingDialog = dialog
        if (!activity.isFinishing && !activity.isDestroyed) {
            dialog.show()
        }
        val progressListener =
            SoundFile.ProgressListener { fraction ->
                dialog.setProgress(fraction)
                !keepGoing
            }
        RingdroidUtils.BACKGROUND.execute {
            val file = resolveFile(data)
            if (file == null) {
                dialog.dismiss()
                fail(null, activity.getText(R.string.read_error))
                return@execute
            }
            val metadata = SongMetadataReader(file.path)
            dialog.setSubtitle(metadata.title)
            handler.post { listener.onMetadata(metadata.title, metadata.artist) }

            var loaded: SoundFile? = null

            fun failed(e: Exception) {
                loaded?.release()
                dialog.dismiss()
                fail(e, activity.getText(R.string.read_error))
            }
            try {
                loaded = SoundFile.create(activity.cacheDir, file.absolutePath, progressListener)
                dialog.dismiss()
                when {
                    loaded == null && keepGoing -> {
                        fail(null, unsupportedMessage(file))
                    }

                    loaded != null && keepGoing -> {
                        deliver(loaded, SamplePlayer(loaded))
                    }

                    else -> {
                        loaded?.release()
                        if (finishRequested) {
                            handler.post { activity.finish() }
                        }
                    }
                }
            } catch (e: IOException) {
                failed(e)
            } catch (e: SoundFile.InvalidInputException) {
                failed(e)
            } catch (e: IllegalArgumentException) {
                failed(e)
            } catch (e: IllegalStateException) {
                failed(e)
            } catch (e: UnsupportedOperationException) {
                failed(e)
            } catch (e: SecurityException) {
                failed(e)
            }
        }
    }

    /** Records from the microphone, asking for the permission first if needed. */
    fun record() {
        if (PermissionUtils.hasMicPermissions(activity)) {
            startRecording()
        } else {
            micPermissionLauncher.launch(PermissionUtils.micPermission)
        }
    }

    private fun startRecording() {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        keepGoing = true
        finishRequested = false
        val dialog =
            RecordingDialog(
                activity,
                onStop = { keepGoing = false },
                onCancel = { requestFinish() },
            )
        recordingDialog = dialog
        dialog.show()
        val progressListener =
            SoundFile.ProgressListener { elapsedSeconds ->
                dialog.setElapsedSeconds(elapsedSeconds)
                !keepGoing
            }
        RingdroidUtils.BACKGROUND.execute {
            var recorded: SoundFile? = null

            fun failed(e: Exception?) {
                recorded?.release()
                dialog.dismiss()
                fail(e, activity.getText(R.string.record_error))
            }
            try {
                recorded = SoundRecorder.record(progressListener)
                if (recorded == null) {
                    failed(null)
                    return@execute
                }
                val player = SamplePlayer(recorded)
                dialog.dismiss()
                if (finishRequested) {
                    player.release()
                    recorded.release()
                    handler.post { activity.finish() }
                } else {
                    deliver(recorded, player)
                }
            } catch (e: IllegalArgumentException) {
                failed(e)
            } catch (e: IllegalStateException) {
                failed(e)
            } catch (e: UnsupportedOperationException) {
                failed(e)
            } catch (e: SecurityException) {
                failed(e)
            }
        }
    }

    private fun requestFinish() {
        keepGoing = false
        finishRequested = true
    }

    /** Hands the sound to the UI thread, or releases it if the editor is gone. */
    private fun deliver(
        soundFile: SoundFile,
        player: SamplePlayer,
    ) {
        handler.post {
            if (activity.isDestroyed) {
                player.release()
                soundFile.release()
            } else {
                listener.onLoaded(soundFile, player)
            }
        }
    }

    private fun fail(
        cause: Throwable?,
        message: CharSequence,
    ) {
        handler.post { listener.onFatalError(cause, message) }
    }

    private fun unsupportedMessage(file: File): String {
        val name = file.name.lowercase(Locale.ROOT)
        return if (!name.contains('.')) {
            activity.getString(R.string.no_extension_error)
        } else {
            activity.getString(R.string.bad_extension_error) + " " + name.substringAfterLast('.')
        }
    }

    /**
     * Returns the file for an editor URI: plain paths from the list screen, file:// URIs, or a
     * copy of a content:// URI. The app's own private files (other than shared copies in the
     * cache) are refused.
     */
    private fun resolveFile(data: Uri): File? {
        val file =
            when (data.scheme) {
                "content" -> RingdroidUtils.getFileFromUri(activity, data)
                "file" -> data.path?.let { File(it) }
                else -> File(data.toString())
            } ?: return null
        return try {
            val path = file.canonicalPath
            val dataDir = File(activity.applicationInfo.dataDir).canonicalPath + "/"
            val cache = activity.cacheDir.canonicalPath + "/"
            if (path.startsWith(dataDir) && !path.startsWith(cache)) null else file
        } catch (e: IOException) {
            null
        }
    }
}
