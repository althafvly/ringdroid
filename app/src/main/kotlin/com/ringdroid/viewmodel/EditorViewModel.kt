package com.ringdroid.viewmodel

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.ringdroid.R
import com.ringdroid.data.EditorSelection
import com.ringdroid.data.SongMetadataReader
import com.ringdroid.data.SourceFiles
import com.ringdroid.soundfile.SamplePlayer
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.soundfile.SoundRecorder
import com.ringdroid.util.ListenerEvents
import com.ringdroid.util.RingdroidUtils
import java.io.File
import java.io.IOException

/**
 * The editor's state, kept across activity recreation: the loaded sound and its player, the song
 * tags, the selection and zoom, and the load or record job in progress.
 */
internal class EditorViewModel(application: Application) : AndroidViewModel(application) {
    /** Receives job results on the main thread. Results are queued while no listener is set. */
    interface Listener {
        /** Fraction loaded (0..1), or seconds recorded. */
        fun onProgress(value: Double)

        fun onMetadata()

        fun onLoaded()

        fun onFailed(cause: Throwable?, message: CharSequence)

        fun onCanceled()
    }

    enum class Task { IDLE, LOADING, RECORDING }

    var task = Task.IDLE
        private set

    /** Whether a load or recording was started, so a recreated editor doesn't start another. */
    var started = false
        private set
    var soundFile: SoundFile? = null
        private set
    var player: SamplePlayer? = null
        private set
    var songTitle: String? = null
        private set
    var songArtist: String? = null
        private set
    var amplifyFactor = 1.0f
    var zoomLevel: Int? = null
    val selection = EditorSelection()

    val events = ListenerEvents<Listener>()

    private val app = application

    @Volatile private var keepGoing = false

    @Volatile private var canceled = false

    @Volatile private var lastProgressTime = 0L

    private var cleared = false

    /** Resolves [data] to a file, reads its tags and decodes it in the background. */
    fun load(data: Uri) {
        var file: File? = null
        runJob(Task.LOADING, R.string.read_error, { SourceFiles.unsupportedMessage(app, file) }) {
            val resolved = SourceFiles.resolve(app, data) ?: throw IOException("Cannot open $data")
            file = resolved
            val metadata = SongMetadataReader(resolved.path)
            events.post {
                songTitle = metadata.title
                songArtist = metadata.artist
                it.onMetadata()
            }
            SoundFile.create(app.cacheDir, resolved.absolutePath, ::reportProgress)
        }
    }

    /** Records from the microphone until [stopRecording] or [cancel]. */
    fun record() {
        runJob(Task.RECORDING, R.string.record_error, { app.getText(R.string.record_error) }) {
            SoundRecorder.record(::reportProgress)
        }
    }

    /**
     * Runs [produce] in the background and delivers its sound. A null result is reported with
     * [nullMessage]; exceptions with [errorMessage].
     */
    private fun runJob(
        newTask: Task,
        errorMessage: Int,
        nullMessage: () -> CharSequence,
        produce: () -> SoundFile?,
    ) {
        started = true
        task = newTask
        keepGoing = true
        canceled = false
        RingdroidUtils.BACKGROUND.execute {
            var sound: SoundFile? = null

            fun failed(e: Exception) {
                sound?.release()
                fail(e, app.getText(errorMessage))
            }
            try {
                sound = produce()
                when {
                    canceled -> {
                        sound?.release()
                        events.post {
                            task = Task.IDLE
                            it.onCanceled()
                        }
                    }

                    sound == null -> {
                        fail(null, nullMessage())
                    }

                    else -> {
                        deliver(sound, SamplePlayer(sound))
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

    /** Stops recording and keeps what was recorded. */
    fun stopRecording() {
        keepGoing = false
    }

    /** Stops loading or recording and discards the result. */
    fun cancel() {
        canceled = true
        keepGoing = false
    }

    override fun onCleared() {
        cleared = true
        cancel()
        player?.release()
        player = null
        soundFile?.release()
        soundFile = null
    }

    /** Called from the job's thread; returns true to stop. */
    private fun reportProgress(value: Double): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastProgressTime > PROGRESS_INTERVAL_MS) {
            lastProgressTime = now
            events.postIfAttached { it.onProgress(value) }
        }
        return !keepGoing
    }

    private fun deliver(loaded: SoundFile, newPlayer: SamplePlayer) {
        events.runOnMain {
            if (cleared) {
                newPlayer.release()
                loaded.release()
            } else {
                soundFile = loaded
                player = newPlayer
                task = Task.IDLE
            }
        }
        events.post { it.onLoaded() }
    }

    private fun fail(cause: Throwable?, message: CharSequence) {
        events.post {
            task = Task.IDLE
            it.onFailed(cause, message)
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 50L
    }
}
