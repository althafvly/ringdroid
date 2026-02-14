package com.ringdroid.core.media

import android.app.Activity
import android.os.Handler
import android.util.Log
import com.ringdroid.R
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.ui.dialog.DialogController
import java.io.File
import java.util.Locale

internal class SoundFileController(
    private val activity: Activity,
    private val handler: Handler,
    private val callback: Callback,
    private val dialogController: DialogController,
) {
    internal interface Callback {
        fun onSoundFileLoaded(
            soundFile: SoundFile,
            player: SamplePlayer,
            title: String?,
            artist: String?,
        )

        fun onLoadError(e: Exception, message: CharSequence)

        fun onFinishActivityRequested()

        fun onInfo(infoText: String)
    }

    private var loadSoundFileThread: Thread? = null
    private var recordAudioThread: Thread? = null
    private var loadingKeepGoing = false
    private var recordingKeepGoing = false
    private var loadingLastUpdateTime: Long = 0
    private var recordingLastUpdateTime: Long = 0
    private var recordingTime = 0.0
    private var finishActivity = false

    fun loadFromFile(filename: String) {
        val file = File(filename)

        val metadataReader = SongMetadataReader(activity, filename)
        val title = metadataReader.mTitle
        val artist = metadataReader.mArtist

        var titleLabel = title
        if (!artist.isNullOrEmpty()) {
            titleLabel += " - $artist"
        }
        activity.title = titleLabel

        loadingLastUpdateTime = currentTime()
        loadingKeepGoing = true
        finishActivity = false
        dialogController.showProgressDialog(
            titleRes = R.string.progress_dialog_loading,
            cancelable = false,
        ) {
            loadingKeepGoing = false
            finishActivity = true
        }

        val listener =
            SoundFile.ProgressListener { fractionComplete ->
                val now = currentTime()
                if (now - loadingLastUpdateTime > 100) {
                    activity.runOnUiThread {
                        dialogController.updateProgressDialog(fractionComplete.toFloat())
                    }
                    loadingLastUpdateTime = now
                }
                !loadingKeepGoing
            }

        loadSoundFileThread =
            Thread {
                    try {
                        val soundFile = SoundFile.create(file.absolutePath, listener)

                        if (soundFile == null) {
                            activity.runOnUiThread { dialogController.hideProgressDialog() }
                            val name = file.name.lowercase(Locale.getDefault())
                            val components = name.split(".").toTypedArray()
                            val err: String =
                                if (components.size < 2) {
                                    activity.resources.getString(R.string.no_extension_error)
                                } else {
                                    activity.resources.getString(R.string.bad_extension_error) +
                                        " " +
                                        components[components.size - 1]
                                }
                            val exception = Exception()
                            handler.post { callback.onLoadError(exception, err) }
                            return@Thread
                        }
                        val player = SamplePlayer(activity, soundFile)
                        activity.runOnUiThread { dialogController.hideProgressDialog() }
                        if (loadingKeepGoing) {
                            handler.post {
                                callback.onSoundFileLoaded(soundFile, player, title, artist)
                            }
                        } else if (finishActivity) {
                            handler.post { callback.onFinishActivityRequested() }
                        }
                    } catch (e: Exception) {
                        activity.runOnUiThread { dialogController.hideProgressDialog() }
                        Log.e(TAG, "Unexpected error: ${e.message}", e)
                        handler.post { callback.onInfo(e.toString()) }
                        handler.post {
                            callback.onLoadError(e, activity.resources.getText(R.string.read_error))
                        }
                    }
                }
                .also { it.start() }
    }

    fun recordAudio() {
        recordingLastUpdateTime = currentTime()
        recordingKeepGoing = true
        finishActivity = false
        dialogController.showRecordingDialog(
            timeText = String.format(Locale.getDefault(), "%d:%05.2f", 0, 0f),
            onStop = { recordingKeepGoing = false },
            onCancel = {
                recordingKeepGoing = false
                finishActivity = true
            },
        )

        val listener =
            SoundFile.ProgressListener { elapsedTime ->
                val now = currentTime()
                if (now - recordingLastUpdateTime > 5) {
                    recordingTime = elapsedTime
                    activity.runOnUiThread {
                        val min = (recordingTime / 60).toInt()
                        val sec = (recordingTime - 60 * min).toFloat()
                        dialogController.updateRecordingTime(
                            String.format(Locale.getDefault(), "%d:%05.2f", min, sec)
                        )
                    }
                    recordingLastUpdateTime = now
                }
                !recordingKeepGoing
            }

        recordAudioThread =
            Thread {
                    try {
                        val soundFile = SoundFile.record(listener)
                        if (soundFile == null) {
                            activity.runOnUiThread { dialogController.hideRecordingDialog() }
                            handler.post {
                                callback.onLoadError(
                                    Exception(),
                                    activity.resources.getText(R.string.record_error),
                                )
                            }
                            return@Thread
                        }
                        val player = SamplePlayer(activity, soundFile)
                        activity.runOnUiThread { dialogController.hideRecordingDialog() }
                        if (finishActivity) {
                            handler.post { callback.onFinishActivityRequested() }
                        } else {
                            handler.post {
                                callback.onSoundFileLoaded(soundFile, player, null, null)
                            }
                        }
                    } catch (e: Exception) {
                        activity.runOnUiThread { dialogController.hideRecordingDialog() }
                        Log.e(TAG, "Unexpected error: ${e.message}", e)
                        handler.post { callback.onInfo(e.toString()) }
                        handler.post {
                            callback.onLoadError(
                                e,
                                activity.resources.getText(R.string.record_error),
                            )
                        }
                    }
                }
                .also { it.start() }
    }

    fun stop() {
        loadingKeepGoing = false
        recordingKeepGoing = false
        closeThread(loadSoundFileThread)
        closeThread(recordAudioThread)
        dialogController.hideProgressDialog()
        dialogController.hideRecordingDialog()
    }

    private fun closeThread(thread: Thread?) {
        if (thread != null && thread.isAlive) {
            try {
                thread.join()
            } catch (_: InterruptedException) {}
        }
    }

    private fun currentTime(): Long = System.nanoTime() / 1_000_000

    companion object {
        private val TAG = SoundFileController::class.java.simpleName
    }
}
