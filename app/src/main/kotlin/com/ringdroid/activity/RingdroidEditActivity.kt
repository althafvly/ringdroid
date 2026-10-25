package com.ringdroid.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.view.doOnLayout
import com.ringdroid.R
import com.ringdroid.data.Clip
import com.ringdroid.data.EditorSelection
import com.ringdroid.databinding.EditorBinding
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.util.showErrorAlert
import com.ringdroid.util.showFinalAlert
import com.ringdroid.view.MarkerLayout
import com.ringdroid.view.WaveformView
import com.ringdroid.viewmodel.EditorViewModel
import com.ringdroid.viewmodel.SaveViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The editor: shows the waveform and the start / end markers, plays the selection and saves it.
 * Positions are in waveform pixels at the current zoom level.
 */
class RingdroidEditActivity : ComponentActivity() {
    private val saveFlow = SaveFlow(this, viewModels<SaveViewModel>())
    private val viewModelLazy = viewModels<EditorViewModel>()
    private val viewModel by viewModelLazy
    private val soundLoader =
        SoundLoader(
            this,
            viewModelLazy,
            object : SoundLoader.Callbacks {
                override fun onMetadata() {
                    val title = viewModel.songTitle.orEmpty()
                    val artist = viewModel.songArtist
                    setTitle(if (artist.isNullOrEmpty()) title else "$title - $artist")
                    toolbar.showTitle()
                }

                override fun onLoaded() {
                    val sound = viewModel.soundFile ?: return
                    showSound(sound, zoomLevel = null)
                    resetPositions()
                    updateDisplay()
                }

                override fun onFatalError(cause: Throwable?, message: CharSequence) {
                    if (cause != null) {
                        binding.info.text = cause.toString().take(MAX_INFO_LENGTH)
                    }
                    closeWithError(cause, message)
                }
            },
        )

    private val handler = Handler(Looper.getMainLooper())
    private val selection: EditorSelection get() = viewModel.selection

    private lateinit var binding: EditorBinding
    private lateinit var waveformView: WaveformView
    private lateinit var markerLayout: MarkerLayout
    private lateinit var labels: PositionLabels
    private lateinit var playback: EditorPlayback
    private lateinit var toolbar: EditorToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.data
        val record = intent.action == MediaStore.Audio.Media.RECORD_SOUND_ACTION
        if (data == null && !record) {
            Toast.makeText(this, R.string.alert_title_failure, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        loadGui()
        labels.start()
        soundLoader.start(data)
        saveFlow.attach()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        soundLoader.detach()
        saveFlow.detach()
        if (::binding.isInitialized) {
            // The view model keeps the sound; only this editor's views and playback go.
            viewModel.zoomLevel = waveformView.zoomLevel
            playback.release()
            waveformView.listener = null
            binding.startmarker.listener = null
            binding.endmarker.listener = null
        }
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            playback.toggle(selection.startPos)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** Builds the layout, restoring the sound, zoom and selection kept by the view model. */
    private fun loadGui() {
        binding = EditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        toolbar =
            EditorToolbar(binding.editorToolbar, this, viewModel, onReset = {
                resetPositions()
                selection.scroll.scrollToStart()
                updateDisplay()
            }, onSave = ::onSave)

        waveformView = binding.waveform
        val markerInput = MarkerInput(selection, binding.startmarker, handler, ::updateDisplay)
        playback =
            EditorPlayback(binding, viewModel, markerInput, ::updateDisplay) {
                closeWithError(it, getText(R.string.play_error))
            }
        labels = PositionLabels(binding, selection, handler, ::updateDisplay)
        val waveformInput = WaveformInput(selection, waveformView, playback, ::updateDisplay)
        waveformView.listener = waveformInput
        binding.zoomIn.setOnClickListener {
            waveformInput.waveformZoomIn()
            waveformInput.waveformFling(1f)
        }
        binding.zoomOut.setOnClickListener {
            waveformInput.waveformZoomOut()
            waveformInput.waveformFling(1f)
        }
        viewModel.soundFile?.let { showSound(it, viewModel.zoomLevel) }

        for (marker in listOf(binding.startmarker, binding.endmarker)) {
            marker.listener = markerInput
            marker.isFocusable = true
            marker.isFocusableInTouchMode = true
        }
        markerLayout =
            MarkerLayout(
                binding.startmarker,
                binding.endmarker,
                waveformView,
                resources.displayMetrics.density,
            )
        // The markers are placed relative to the waveform's size, which is only known after layout.
        waveformView.doOnLayout { it.post(::updateDisplay) }
    }

    /** Shows [sound] at [zoomLevel], or at the zoom level that fits its length. */
    private fun showSound(sound: SoundFile, zoomLevel: Int?) {
        waveformView.setSoundFile(sound)
        zoomLevel?.let { waveformView.zoomLevel = it }
        waveformView.density = resources.displayMetrics.density
        selection.scroll.maxPos = waveformView.maxPos
        labels.invalidate()
        val seconds = "%.2f".format(Locale.US, sound.numSamples.toDouble() / sound.sampleRate)
        val caption =
            "${sound.filetype}, ${sound.sampleRate} Hz, ${sound.avgBitrateKbps} kbps, $seconds " +
                getString(R.string.time_seconds)
        binding.info.text = caption
    }

    private fun updateDisplay() {
        playback.update()
        val scroll = selection.scroll
        scroll.step()
        waveformView.setSelection(selection.startPos, selection.endPos, scroll.offset)
        waveformView.invalidate()
        markerLayout.place(selection.startPos, selection.endPos, scroll.offset)
    }

    private fun resetPositions() {
        val scale = waveformView.scale
        selection.reset(
            scale.secondsToPixels(0.0),
            scale.secondsToPixels(DEFAULT_SELECTION_SECONDS),
        )
    }

    /** Shows an error that closes the editor when dismissed. The cause, if any, is logged. */
    private fun closeWithError(cause: Throwable?, message: CharSequence) {
        Log.e(TAG, "Error: $message", cause)
        setResult(RESULT_CANCELED)
        showFinalAlert(this, message)
    }

    private fun onSave() {
        if (playback.isPlaying) {
            playback.pause()
        }
        val loaded = viewModel.soundFile ?: return
        if (selection.endPos <= selection.startPos) {
            showErrorAlert(this, R.string.too_small_error)
            return
        }
        val scale = waveformView.scale
        val startTime = scale.pixelsToSeconds(selection.startPos)
        val endTime = scale.pixelsToSeconds(selection.endPos)
        val startFrame = scale.secondsToFrames(startTime)
        val clip =
            Clip(
                startFrame,
                scale.secondsToFrames(endTime) - startFrame,
                ((endTime - startTime) * MILLIS_PER_SECOND).roundToInt(),
            )
        saveFlow.start(loaded, viewModel.songTitle, viewModel.amplifyFactor, clip)
    }

    companion object {
        /** Set when the list screen was opened to pick content for another app. */
        const val EXTRA_WAS_GET_CONTENT_INTENT = "was_get_content_intent"

        /** Opens [data] in the editor, or starts a recording when it's null. */
        fun newIntent(context: Context, data: Uri?, wasGetContentIntent: Boolean): Intent {
            val action =
                if (data ==
                    null
                ) {
                    MediaStore.Audio.Media.RECORD_SOUND_ACTION
                } else {
                    Intent.ACTION_EDIT
                }
            return Intent(action, data, context, RingdroidEditActivity::class.java)
                .putExtra(EXTRA_WAS_GET_CONTENT_INTENT, wasGetContentIntent)
                // Passes on access to a content URI shared with the list screen.
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        private const val TAG = "RingdroidEditActivity"
        private const val MAX_INFO_LENGTH = 5000
        private const val DEFAULT_SELECTION_SECONDS = 15.0
        private const val MILLIS_PER_SECOND = 1000
    }
}
