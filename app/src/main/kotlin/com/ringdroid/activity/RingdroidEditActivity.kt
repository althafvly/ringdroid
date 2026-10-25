package com.ringdroid.activity

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import com.ringdroid.R
import com.ringdroid.data.EditorSelection
import com.ringdroid.databinding.EditorBinding
import com.ringdroid.dialog.AmplifyDialog
import com.ringdroid.soundfile.SamplePlayer
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.util.RingdroidUtils
import com.ringdroid.view.MarkerLayout
import com.ringdroid.view.MarkerView
import com.ringdroid.view.WaveformView
import java.util.Locale

/**
 * The editor: shows the waveform and the start / end markers, plays the selection and saves it.
 * Positions are in waveform pixels at the current zoom level.
 */
class RingdroidEditActivity :
    ComponentActivity(),
    MarkerView.MarkerListener,
    WaveformView.WaveformListener {
    private val saveFlow = SaveFlow(this)
    private val soundLoader =
        SoundLoader(
            this,
            object : SoundLoader.Listener {
                override fun onMetadata(
                    title: String,
                    artist: String?,
                ) {
                    songTitle = title
                    songArtist = artist
                    setTitle(if (artist.isNullOrEmpty()) title else "$title - $artist")
                    updateToolbarTitle()
                }

                override fun onLoaded(
                    soundFile: SoundFile,
                    player: SamplePlayer,
                ) {
                    this@RingdroidEditActivity.soundFile = soundFile
                    this@RingdroidEditActivity.player = player
                    finishOpeningSoundFile(soundFile)
                }

                override fun onFatalError(
                    cause: Throwable?,
                    message: CharSequence,
                ) {
                    if (cause != null) {
                        binding.info.text = cause.toString().take(MAX_INFO_LENGTH)
                    }
                    showFinalAlert(cause, message)
                }
            },
        )

    private val handler = Handler(Looper.getMainLooper())

    private var soundFile: SoundFile? = null
    private var player: SamplePlayer? = null
    private var songTitle: String? = null
    private var songArtist: String? = null
    private var amplifyFactor = 1.0f

    private lateinit var binding: EditorBinding
    private lateinit var waveformView: WaveformView
    private lateinit var startMarker: MarkerView
    private lateinit var endMarker: MarkerView
    private lateinit var startText: TextView
    private lateinit var endText: TextView
    private var caption = ""

    private val selection = EditorSelection()
    private lateinit var markerLayout: MarkerLayout
    private var lastDisplayedStartPos = -1
    private var lastDisplayedEndPos = -1
    private var playStartMsec = 0
    private var playEndMsec = 0
    private var isPlaying = false
    private var waveformTouchStartMsec = 0L
    private var density = 1f

    private val textWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence,
                start: Int,
                count: Int,
                after: Int,
            ) {}

            override fun onTextChanged(
                s: CharSequence,
                start: Int,
                before: Int,
                count: Int,
            ) {}

            override fun afterTextChanged(s: Editable) {
                if (!waveformView.isInitialized) {
                    return
                }
                val seconds = s.toString().toDoubleOrNull() ?: return
                if (startText.hasFocus()) {
                    selection.startPos = selection.trap(waveformView.secondsToPixels(seconds))
                    updateDisplay()
                }
                if (endText.hasFocus()) {
                    selection.endPos = selection.trap(waveformView.secondsToPixels(seconds))
                    updateDisplay()
                }
            }
        }

    // Updating an EditText is slow, so only update the times when they changed.
    private val timerRunnable: Runnable =
        object : Runnable {
            override fun run() {
                if (selection.startPos != lastDisplayedStartPos && !startText.hasFocus()) {
                    startText.text = formatTime(selection.startPos)
                    lastDisplayedStartPos = selection.startPos
                }
                if (selection.endPos != lastDisplayedEndPos && !endText.hasFocus()) {
                    endText.text = formatTime(selection.endPos)
                    lastDisplayedEndPos = selection.endPos
                }
                handler.postDelayed(this, 100)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.data
        if (data == null) {
            Toast.makeText(this, R.string.alert_title_failure, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        loadGui(zoomLevel = null)
        handler.postDelayed(timerRunnable, 100)

        if (data.toString() == RECORD) soundLoader.record() else soundLoader.load(data)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        soundLoader.cancel()
        saveFlow.dismiss()
        player?.release()
        player = null
        if (::binding.isInitialized) {
            waveformView.listener = null
            startMarker.listener = null
            endMarker.listener = null
            waveformView.setSoundFile(null)
        }
        soundFile?.release()
        soundFile = null
        super.onDestroy()
    }

    /** Rotation, theme and locale changes rebuild the layout without restarting the editor. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::binding.isInitialized) {
            return
        }
        val zoomLevel = waveformView.zoomLevel
        loadGui(zoomLevel)
        handler.postDelayed({
            startMarker.requestFocus()
            markerFocus(startMarker)
            waveformView.recomputeHeights(density)
            updateDisplay()
        }, 500)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            onPlay(selection.startPos)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // WaveformListener

    /** Each redraw animates the scroll position towards its goal. */
    override fun waveformDraw() {
        selection.width = waveformView.measuredWidth
        if (selection.isAnimating || isPlaying) {
            updateDisplay()
        }
    }

    override fun waveformTouchStart(x: Float) {
        selection.waveformTouchStart(x)
        waveformTouchStartMsec = SystemClock.uptimeMillis()
    }

    override fun waveformTouchMove(x: Float) {
        selection.waveformTouchMove(x)
        updateDisplay()
    }

    override fun waveformTouchEnd() {
        val tapPos = selection.waveformTouchEnd()
        if (SystemClock.uptimeMillis() - waveformTouchStartMsec >= 300) {
            return
        }
        // A tap: seek within the playing range, or start playing from there.
        if (isPlaying) {
            val seekMsec = waveformView.pixelsToMillisecs(tapPos)
            if (seekMsec in playStartMsec until playEndMsec) {
                player?.seekTo(seekMsec)
            } else {
                handlePause()
            }
        } else {
            onPlay(tapPos)
        }
    }

    override fun waveformFling(x: Float) {
        selection.fling(x)
        updateDisplay()
    }

    override fun waveformZoomIn() {
        if (waveformView.isInitialized) {
            waveformView.zoomIn()
            syncZoom()
        }
    }

    override fun waveformZoomOut() {
        if (waveformView.isInitialized) {
            waveformView.zoomOut()
            syncZoom()
        }
    }

    private fun syncZoom() {
        selection.syncZoom(waveformView.selectionStart, waveformView.selectionEnd, waveformView.maxPos(), waveformView.offset)
        updateDisplay()
    }

    // MarkerListener

    override fun markerDraw() {}

    override fun markerTouchStart(
        marker: MarkerView,
        pos: Float,
    ) = selection.markerTouchStart(pos)

    override fun markerTouchMove(
        marker: MarkerView,
        pos: Float,
    ) {
        selection.markerTouchMove(marker === startMarker, pos)
        updateDisplay()
    }

    override fun markerTouchEnd(marker: MarkerView) {
        selection.markerTouchEnd(marker === startMarker)
        updateDisplay()
    }

    override fun markerLeft(
        marker: MarkerView,
        velocity: Int,
    ) {
        selection.nudgeLeft(marker === startMarker, velocity)
        updateDisplay()
    }

    override fun markerRight(
        marker: MarkerView,
        velocity: Int,
    ) {
        selection.nudgeRight(marker === startMarker, velocity)
        updateDisplay()
    }

    override fun markerEnter(marker: MarkerView) {}

    override fun markerKeyUp() {
        selection.keyUp()
        updateDisplay()
    }

    override fun markerFocus(marker: MarkerView) {
        selection.focusMarker(marker === startMarker)
        // Delay so that a touch causing the focus is handled first.
        handler.postDelayed({ updateDisplay() }, 100)
    }

    /** Builds the layout; [zoomLevel] restores the waveform zoom after a configuration change. */
    private fun loadGui(zoomLevel: Int?) {
        binding = EditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.editorToolbar
        RingdroidUtils.applyStatusBarInsets(toolbar.toolbarContainer)
        toolbar.btnBack.setOnClickListener { finish() }
        toolbar.btnAmplify.setOnClickListener { AmplifyDialog.show(this, amplifyFactor) { amplifyFactor = it } }
        toolbar.btnReset.setOnClickListener {
            resetPositions()
            selection.scrollToStart()
            updateDisplay()
        }
        toolbar.btnSave.setOnClickListener { onSave() }
        updateToolbarTitle()

        density = resources.displayMetrics.density

        startText = binding.starttext
        startText.addTextChangedListener(textWatcher)
        endText = binding.endtext
        endText.addTextChangedListener(textWatcher)

        binding.play.setOnClickListener { onPlay(selection.startPos) }
        binding.rew.setOnClickListener {
            val p = player
            if (isPlaying && p != null) {
                p.seekTo(maxOf(p.currentPosition - 5000, playStartMsec))
            } else {
                startMarker.requestFocus()
                markerFocus(startMarker)
            }
        }
        binding.ffwd.setOnClickListener {
            val p = player
            if (isPlaying && p != null) {
                p.seekTo(minOf(p.currentPosition + 5000, playEndMsec))
            } else {
                endMarker.requestFocus()
                markerFocus(endMarker)
            }
        }
        binding.zoomIn.setOnClickListener {
            waveformZoomIn()
            waveformFling(1f)
        }
        binding.zoomOut.setOnClickListener {
            waveformZoomOut()
            waveformFling(1f)
        }
        binding.markStart.setOnClickListener {
            val p = player
            if (isPlaying && p != null) {
                selection.startPos = waveformView.millisecsToPixels(p.currentPosition)
                updateDisplay()
            }
        }
        binding.markEnd.setOnClickListener {
            val p = player
            if (isPlaying && p != null) {
                selection.endPos = waveformView.millisecsToPixels(p.currentPosition)
                updateDisplay()
                handlePause()
            }
        }
        enableDisableButtons()

        waveformView = binding.waveform
        waveformView.listener = this
        binding.info.text = caption

        selection.maxPos = 0
        lastDisplayedStartPos = -1
        lastDisplayedEndPos = -1
        soundFile?.let { sound ->
            waveformView.setSoundFile(sound)
            if (zoomLevel != null) {
                waveformView.zoomLevel = zoomLevel
            }
            waveformView.recomputeHeights(density)
            selection.maxPos = waveformView.maxPos()
        }

        startMarker = binding.startmarker
        endMarker = binding.endmarker
        for (marker in listOf(startMarker, endMarker)) {
            marker.listener = this
            marker.isFocusable = true
            marker.isFocusableInTouchMode = true
        }
        markerLayout = MarkerLayout(startMarker, endMarker, waveformView, density)
        updateDisplay()
    }

    private fun updateToolbarTitle() {
        val toolbar = binding.editorToolbar
        val title = songTitle
        if (title.isNullOrEmpty()) {
            toolbar.toolbarTitle.setText(R.string.edit_intent)
            toolbar.toolbarSubtitle.isVisible = false
        } else {
            toolbar.toolbarTitle.text = title
            toolbar.toolbarSubtitle.text = songArtist
            toolbar.toolbarSubtitle.isVisible = !songArtist.isNullOrEmpty()
        }
    }

    private fun finishOpeningSoundFile(loaded: SoundFile) {
        waveformView.setSoundFile(loaded)
        waveformView.recomputeHeights(density)
        selection.maxPos = waveformView.maxPos()
        lastDisplayedStartPos = -1
        lastDisplayedEndPos = -1
        resetPositions()
        caption =
            loaded.filetype + ", " + loaded.sampleRate + " Hz, " + loaded.avgBitrateKbps + " kbps, " +
            formatTime(selection.maxPos) + " " + getString(R.string.time_seconds)
        binding.info.text = caption
        updateDisplay()
    }

    private fun updateDisplay() {
        val p = player
        if (isPlaying && p != null) {
            val now = p.currentPosition
            val playbackPos = waveformView.millisecsToPixels(now)
            waveformView.setPlayback(playbackPos)
            selection.scrollToCenter(playbackPos)
            if (now >= playEndMsec) {
                handlePause()
            }
        }
        selection.step()
        val startPos = selection.startPos
        val endPos = selection.endPos
        val offset = selection.offset
        waveformView.setParameters(startPos, endPos, offset)
        waveformView.invalidate()

        if (startPos != lastDisplayedStartPos) {
            startMarker.contentDescription = getString(R.string.start_marker) + " " + formatTime(startPos)
        }
        if (endPos != lastDisplayedEndPos) {
            endMarker.contentDescription = getString(R.string.end_marker) + " " + formatTime(endPos)
        }

        markerLayout.place(startPos, endPos, offset)
    }

    private fun enableDisableButtons() {
        if (isPlaying) {
            binding.play.setImageResource(R.drawable.baseline_pause_24)
            binding.play.contentDescription = getText(R.string.stop)
        } else {
            binding.play.setImageResource(R.drawable.baseline_play_arrow_24)
            binding.play.contentDescription = getText(R.string.play)
        }
    }

    private fun resetPositions() {
        selection.reset(waveformView.secondsToPixels(0.0), waveformView.secondsToPixels(15.0))
    }

    private fun formatTime(pixels: Int): String =
        if (::waveformView.isInitialized && waveformView.isInitialized) {
            "%.2f".format(Locale.US, waveformView.pixelsToSeconds(pixels))
        } else {
            ""
        }

    private fun handlePause() {
        player?.let { if (it.isPlaying) it.pause() }
        waveformView.setPlayback(-1)
        isPlaying = false
        enableDisableButtons()
    }

    private fun onPlay(startPosition: Int) {
        if (isPlaying) {
            handlePause()
            return
        }
        val p = player ?: return // Not loaded yet.
        try {
            playStartMsec = waveformView.pixelsToMillisecs(startPosition)
            // Play to the start marker, the end marker or the end of the file.
            playEndMsec =
                waveformView.pixelsToMillisecs(
                    when {
                        startPosition < selection.startPos -> selection.startPos
                        startPosition > selection.endPos -> selection.maxPos
                        else -> selection.endPos
                    },
                )
            p.onCompletionListener = { handlePause() }
            isPlaying = true
            p.seekTo(playStartMsec)
            p.start()
            updateDisplay()
            enableDisableButtons()
        } catch (e: IllegalStateException) {
            showFinalAlert(e, getText(R.string.play_error))
        } catch (e: IllegalArgumentException) {
            showFinalAlert(e, getText(R.string.play_error))
        }
    }

    /** Shows an error that closes the editor when dismissed. The cause, if any, is logged. */
    private fun showFinalAlert(
        cause: Throwable?,
        message: CharSequence,
    ) {
        Log.e(TAG, "Error: $message", cause)
        setResult(RESULT_CANCELED, Intent())
        if (isFinishing || isDestroyed) {
            return
        }
        AlertDialog
            .Builder(this)
            .setTitle(R.string.alert_title_failure)
            .setMessage(message)
            .setPositiveButton(R.string.alert_ok_button) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun onSave() {
        if (isPlaying) {
            handlePause()
        }
        val loaded = soundFile ?: return
        if (selection.endPos <= selection.startPos) {
            showErrorAlert(this, R.string.too_small_error)
            return
        }
        val startTime = waveformView.pixelsToSeconds(selection.startPos)
        val endTime = waveformView.pixelsToSeconds(selection.endPos)
        val startFrame = waveformView.secondsToFrames(startTime)
        val endFrame = waveformView.secondsToFrames(endTime)
        val durationMs = ((endTime - startTime) * 1000 + 0.5).toInt()
        saveFlow.start(loaded, songTitle, amplifyFactor, startFrame, endFrame - startFrame, durationMs)
    }

    companion object {
        /** Set when the list screen was opened to pick content for another app. */
        const val EXTRA_WAS_GET_CONTENT_INTENT = "was_get_content_intent"

        /** Editor data URI that starts a recording instead of opening a file. */
        const val RECORD = "record"

        private const val TAG = "RingdroidEditActivity"
        private const val MAX_INFO_LENGTH = 5000
    }
}
