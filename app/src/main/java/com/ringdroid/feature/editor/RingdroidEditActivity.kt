package com.ringdroid.feature.editor

import android.Manifest
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewTreeObserver
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ringdroid.R
import com.ringdroid.core.media.PlaybackController
import com.ringdroid.core.media.RingtoneSaveManager
import com.ringdroid.core.media.SamplePlayer
import com.ringdroid.core.media.SoundFileController
import com.ringdroid.core.permissions.PermissionUtils
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.theme.AppTheme
import com.ringdroid.ui.dialog.DialogController
import com.ringdroid.ui.dialog.RingdroidDialogs
import com.ringdroid.ui.waveform.MarkerView
import com.ringdroid.ui.waveform.WaveformView
import java.util.Objects

class RingdroidEditActivity :
    ComponentActivity(), MarkerView.MarkerListener, WaveformView.WaveformListener {
    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                soundFileController.recordAudio()
            } else {
                Toast.makeText(this, R.string.required_mic_permission, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val chooseContactLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { finish() }

    private var soundFile: SoundFile? = null
    private var titleText: String? by mutableStateOf(null)
    private var wasGetContentIntent = false
    private var playbackController: PlaybackController? = null
    private lateinit var waveformView: WaveformView
    private lateinit var startMarker: MarkerView
    private lateinit var endMarker: MarkerView
    private var startText by mutableStateOf("")
    private var endText by mutableStateOf("")
    private var infoText by mutableStateOf("")
    private var startHasFocus by mutableStateOf(false)
    private var endHasFocus by mutableStateOf(false)
    private var isPlayingState by mutableStateOf(false)
    private var keyDown = false
    private var caption: String = ""
    private var width = 0
    private var maxPos = 0
    private var startPos = 0
    private var endPos = 0
    private var startVisible = false
    private var endVisible = false
    private var lastDisplayedStartPos = -1
    private var lastDisplayedEndPos = -1
    private var offset = 0
    private var offsetGoal = 0
    private var flingVelocity = 0
    private lateinit var handler: Handler
    private var touchDragging = false
    private var touchStart = 0f
    private var touchInitialOffset = 0
    private var touchInitialStartPos = 0
    private var touchInitialEndPos = 0
    private var waveformTouchStartMsec = 0L
    private var density = 0f
    private var markerLeftInset = 0
    private var markerRightInset = 0
    private var markerTopOffset = 0
    private var markerBottomOffset = 0
    private var pendingMarkerLayoutUpdate = true
    private val dialogController = DialogController()
    private lateinit var soundFileController: SoundFileController
    private lateinit var ringtoneSaveManager: RingtoneSaveManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        wasGetContentIntent = intent.getBooleanExtra("was_get_content_intent", false)

        val filename =
            Objects.requireNonNull(intent.data)
                .toString()
                .replaceFirst("file://".toRegex(), "")
                .replace("%20".toRegex(), " ")
        soundFile = null
        keyDown = false

        handler = Handler(Looper.getMainLooper())

        setupDisplayMetrics()
        setupEditorViews()
        setupPlaybackController()

        soundFileController =
            SoundFileController(
                this,
                handler,
                object : SoundFileController.Callback {
                    override fun onSoundFileLoaded(
                        soundFile: SoundFile,
                        player: SamplePlayer,
                        title: String?,
                        artist: String?,
                    ) {
                        this@RingdroidEditActivity.soundFile = soundFile
                        playbackController?.setPlayer(player)
                        titleText = title ?: resources.getString(R.string.edit_intent)
                        finishOpeningSoundFile()
                    }

                    override fun onLoadError(e: Exception, message: CharSequence) {
                        showFinalAlert(e, message)
                    }

                    override fun onFinishActivityRequested() {
                        finish()
                    }

                    override fun onInfo(infoText: String) {
                        this@RingdroidEditActivity.infoText = infoText
                    }
                },
                dialogController,
            )
        ringtoneSaveManager =
            RingtoneSaveManager(
                this,
                handler,
                object : RingtoneSaveManager.Callback {
                    override fun onInfo(infoText: String) {
                        this@RingdroidEditActivity.infoText = infoText
                    }

                    override fun onShowFinalAlert(e: Exception?, message: CharSequence) {
                        showFinalAlert(e, message)
                    }
                },
                chooseContactLauncher,
                dialogController,
            )

        setContent {
            AppTheme {
                Box {
                    RingdroidEditScreen(
                        title = titleText,
                        startText = startText,
                        endText = endText,
                        infoText = infoText,
                        isPlaying = isPlayingState,
                        onStartTextChange = {
                            startText = it
                            updatePositionFromText(it, true)
                        },
                        onStartFocusChange = { startHasFocus = it },
                        onEndTextChange = {
                            endText = it
                            updatePositionFromText(it, false)
                        },
                        onEndFocusChange = { endHasFocus = it },
                        onMarkStart = {
                            if (isPlaying()) {
                                startPos =
                                    waveformView.millisecsToPixels(
                                        playbackController?.currentPosition ?: 0
                                    )
                                updateDisplay()
                            }
                        },
                        onMarkEnd = {
                            if (isPlaying()) {
                                endPos =
                                    waveformView.millisecsToPixels(
                                        playbackController?.currentPosition ?: 0
                                    )
                                updateDisplay()
                                handlePause()
                            }
                        },
                        onPlayPause = { onPlay(startPos) },
                        onRewind = {
                            if (isPlaying()) {
                                var newPos = (playbackController?.currentPosition ?: 0) - 5000
                                if (newPos < (playbackController?.playStartMsec ?: 0)) {
                                    newPos = playbackController?.playStartMsec ?: newPos
                                }
                                playbackController?.seekTo(newPos)
                            } else {
                                startMarker.requestFocus()
                                markerFocus(startMarker)
                            }
                        },
                        onFastForward = {
                            if (isPlaying()) {
                                var newPos = (playbackController?.currentPosition ?: 0) + 5000
                                if (newPos > (playbackController?.playEndMsec ?: 0)) {
                                    newPos = playbackController?.playEndMsec ?: newPos
                                }
                                playbackController?.seekTo(newPos)
                            } else {
                                endMarker.requestFocus()
                                markerFocus(endMarker)
                            }
                        },
                        onZoomIn = {
                            waveformZoomIn()
                            waveformFling(1f)
                        },
                        onZoomOut = {
                            waveformZoomOut()
                            waveformFling(1f)
                        },
                        onReset = {
                            resetPositions()
                            offsetGoal = 0
                            updateDisplay()
                        },
                        onSave = { onSave() },
                        waveformContent = { WaveformArea() },
                    )
                    RingdroidDialogs(dialogController = dialogController)
                }
            }
        }

        handler.postDelayed(timerRunnable, 100)

        if (filename != "record") {
            soundFileController.loadFromFile(filename)
        } else {
            if (PermissionUtils.hasMicPermissions(this)) {
                soundFileController.recordAudio()
            } else {
                requestMicPermission()
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        soundFileController.stop()
        ringtoneSaveManager.stop()
        playbackController?.release()

        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val saveZoomLevel = waveformView.zoomLevel
        super.onConfigurationChanged(newConfig)

        setupDisplayMetrics()

        handler.postDelayed(
            {
                startMarker.requestFocus()
                markerFocus(startMarker)

                waveformView.setZoomLevel(saveZoomLevel)
                waveformView.recomputeHeights(density)

                updateDisplay()
            },
            500,
        )
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_SPACE) {
            onPlay(startPos)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun waveformDraw() {
        width = waveformView.measuredWidth
        if ((offsetGoal != offset && !keyDown) || isPlaying() || flingVelocity != 0) {
            updateDisplay()
        }
    }

    override fun waveformTouchStart(x: Float) {
        touchDragging = true
        touchStart = x
        touchInitialOffset = offset
        flingVelocity = 0
        waveformTouchStartMsec = currentTime()
    }

    override fun waveformTouchMove(x: Float) {
        offset = trap((touchInitialOffset + (touchStart - x)).toInt())
        updateDisplay()
    }

    override fun waveformTouchEnd() {
        touchDragging = false
        offsetGoal = offset

        val elapsedMsec = currentTime() - waveformTouchStartMsec
        if (elapsedMsec < 300) {
            if (isPlaying()) {
                val seekMsec = waveformView.pixelsToMillisecs((touchStart + offset).toInt())
                if (
                    seekMsec >= (playbackController?.playStartMsec ?: 0) &&
                        seekMsec < (playbackController?.playEndMsec ?: 0)
                ) {
                    playbackController?.seekTo(seekMsec)
                } else {
                    handlePause()
                }
            } else {
                onPlay((touchStart + offset).toInt())
            }
        }
    }

    override fun waveformFling(vx: Float) {
        touchDragging = false
        offsetGoal = offset
        flingVelocity = (-vx).toInt()
        updateDisplay()
    }

    override fun waveformZoomIn() {
        waveformView.zoomIn()
        startPos = waveformView.start
        endPos = waveformView.end
        maxPos = waveformView.maxPos()
        offset = waveformView.offset
        offsetGoal = offset
        updateDisplay()
    }

    override fun waveformZoomOut() {
        waveformView.zoomOut()
        startPos = waveformView.start
        endPos = waveformView.end
        maxPos = waveformView.maxPos()
        offset = waveformView.offset
        offsetGoal = offset
        updateDisplay()
    }

    override fun markerDraw() = Unit

    override fun markerTouchStart(marker: MarkerView, x: Float) {
        touchDragging = true
        touchStart = x
        touchInitialStartPos = startPos
        touchInitialEndPos = endPos
    }

    override fun markerTouchMove(marker: MarkerView, x: Float) {
        val delta = x - touchStart

        if (marker == startMarker) {
            startPos = trap((touchInitialStartPos + delta).toInt())
            endPos = trap((touchInitialEndPos + delta).toInt())
        } else {
            endPos = trap((touchInitialEndPos + delta).toInt())
            if (endPos < startPos) {
                endPos = startPos
            }
        }

        updateDisplay()
    }

    override fun markerTouchEnd(marker: MarkerView) {
        touchDragging = false
        if (marker == startMarker) {
            setOffsetGoalStart()
        } else {
            setOffsetGoalEnd()
        }
    }

    override fun markerLeft(marker: MarkerView, velocity: Int) {
        keyDown = true

        if (marker == startMarker) {
            val saveStart = startPos
            startPos = trap(startPos - velocity)
            endPos = trap(endPos - (saveStart - startPos))
            setOffsetGoalStart()
        }

        if (marker == endMarker) {
            if (endPos == startPos) {
                startPos = trap(startPos - velocity)
                endPos = startPos
            } else {
                endPos = trap(endPos - velocity)
            }

            setOffsetGoalEnd()
        }

        updateDisplay()
    }

    override fun markerRight(marker: MarkerView, velocity: Int) {
        keyDown = true

        if (marker == startMarker) {
            val saveStart = startPos
            startPos += velocity
            if (startPos > maxPos) {
                startPos = maxPos
            }
            endPos += startPos - saveStart
            if (endPos > maxPos) {
                endPos = maxPos
            }

            setOffsetGoalStart()
        }

        if (marker == endMarker) {
            endPos += velocity
            if (endPos > maxPos) {
                endPos = maxPos
            }

            setOffsetGoalEnd()
        }

        updateDisplay()
    }

    override fun markerKeyUp() {
        keyDown = false
        updateDisplay()
    }

    override fun markerEnter(marker: MarkerView) = Unit

    override fun markerFocus(marker: MarkerView) {
        keyDown = false
        if (marker == startMarker) {
            setOffsetGoalStartNoUpdate()
        } else {
            setOffsetGoalEndNoUpdate()
        }

        handler.postDelayed({ updateDisplay() }, 100)
    }

    private fun setupDisplayMetrics() {
        val metrics = resources.displayMetrics
        density = metrics.density

        markerLeftInset = (46 * density).toInt()
        markerRightInset = (48 * density).toInt()
        markerTopOffset = (10 * density).toInt()
        markerBottomOffset = (10 * density).toInt()
    }

    private fun setupEditorViews() {
        waveformView = WaveformView(this, null)
        waveformView.setListener(this)
        waveformView.isFocusable = false
        waveformView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (!pendingMarkerLayoutUpdate) return
                    if (soundFile == null) return
                    if (waveformView.height <= 0 || waveformView.width <= 0) return
                    updateDisplay()
                    pendingMarkerLayoutUpdate = false
                    waveformView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        )

        startMarker = MarkerView(this, null)
        startMarker.setImageResource(R.drawable.marker_left)
        startMarker.setListener(this)
        startMarker.alpha = 1f
        startMarker.isFocusable = true
        startMarker.isFocusableInTouchMode = true
        startVisible = true

        endMarker = MarkerView(this, null)
        endMarker.setImageResource(R.drawable.marker_right)
        endMarker.setListener(this)
        endMarker.alpha = 1f
        endMarker.isFocusable = true
        endMarker.isFocusableInTouchMode = true
        endVisible = true

        maxPos = 0
        lastDisplayedStartPos = -1
        lastDisplayedEndPos = -1
    }

    private fun setupPlaybackController() {
        if (playbackController == null) {
            playbackController =
                PlaybackController(waveformView).also { it.setCompletionCallback { handlePause() } }
        } else {
            playbackController?.setWaveformView(waveformView)
        }
    }

    private val timerRunnable =
        object : Runnable {
            override fun run() {
                if (startPos != lastDisplayedStartPos && !startHasFocus) {
                    val formatted = formatTime(startPos)
                    if (formatted != startText) {
                        startText = formatted
                    }
                    lastDisplayedStartPos = startPos
                }

                if (endPos != lastDisplayedEndPos && !endHasFocus) {
                    val formatted = formatTime(endPos)
                    if (formatted != endText) {
                        endText = formatted
                    }
                    lastDisplayedEndPos = endPos
                }

                handler.postDelayed(this, 100)
            }
        }

    private fun finishOpeningSoundFile() {
        waveformView.setSoundFile(soundFile)
        waveformView.recomputeHeights(density)

        maxPos = waveformView.maxPos()
        lastDisplayedStartPos = -1
        lastDisplayedEndPos = -1

        touchDragging = false

        offset = 0
        offsetGoal = 0
        flingVelocity = 0
        resetPositions()
        if (endPos > maxPos) {
            endPos = maxPos
        }

        soundFile?.let { file ->
            caption =
                "${file.filetype}, ${file.sampleRate} Hz, ${file.avgBitrateKbps} kbps, " +
                    "${formatTime(maxPos)} ${resources.getString(R.string.time_seconds)}"
            infoText = caption
        }

        updateDisplay()
    }

    @Synchronized
    private fun updateDisplay() {
        updatePlaybackState()

        if (!touchDragging) {
            updateOffset()
        }

        waveformView.setParameters(startPos, endPos, offset)
        waveformView.invalidate()

        updateMarkerLayout()
    }

    private fun updatePlaybackState() {
        isPlayingState = isPlaying()
        if (!isPlayingState) {
            return
        }

        val now = playbackController?.currentPosition ?: return
        val frames = waveformView.millisecsToPixels(now)
        waveformView.setPlayback(frames)
        setOffsetGoalNoUpdate(frames - width / 2)
        if (now >= (playbackController?.playEndMsec ?: 0)) {
            handlePause()
        }
    }

    private fun updateOffset() {
        if (flingVelocity != 0) {
            val offsetDelta = flingVelocity / 30
            if (flingVelocity > 80) {
                flingVelocity -= 80
            } else if (flingVelocity < -80) {
                flingVelocity += 80
            } else {
                flingVelocity = 0
            }

            offset += offsetDelta

            if (offset + width / 2 > maxPos) {
                offset = maxPos - width / 2
                flingVelocity = 0
            }
            if (offset < 0) {
                offset = 0
                flingVelocity = 0
            }
            offsetGoal = offset
            return
        }

        var offsetDelta = offsetGoal - offset

        if (offsetDelta > 10) {
            offsetDelta /= 10
        } else if (offsetDelta > 0) {
            offsetDelta = 1
        } else if (offsetDelta < -10) {
            offsetDelta /= 10
        } else if (offsetDelta < 0) {
            offsetDelta = -1
        }

        offset += offsetDelta
    }

    private fun updateMarkerLayout() {
        startMarker.contentDescription =
            resources.getText(R.string.start_marker).toString() + " " + formatTime(startPos)
        endMarker.contentDescription =
            resources.getText(R.string.end_marker).toString() + " " + formatTime(endPos)

        var startX = startPos - offset - markerLeftInset
        if (startX + startMarker.width >= 0) {
            if (!startVisible) {
                handler.postDelayed(
                    {
                        startVisible = true
                        startMarker.alpha = 1f
                    },
                    0,
                )
            }
        } else {
            if (startVisible) {
                startMarker.alpha = 0f
                startVisible = false
            }
            startX = 0
        }

        var endX = endPos - offset - endMarker.width + markerRightInset
        if (endX + endMarker.width >= 0) {
            if (!endVisible) {
                handler.postDelayed(
                    {
                        endVisible = true
                        endMarker.alpha = 1f
                    },
                    0,
                )
            }
        } else {
            if (endVisible) {
                endMarker.alpha = 0f
                endVisible = false
            }
            endX = 0
        }

        var params =
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            )
        params.setMargins(startX, markerTopOffset, -startMarker.width, -startMarker.height)
        startMarker.layoutParams = params

        params =
            RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT,
            )
        params.setMargins(
            endX,
            waveformView.measuredHeight - endMarker.height - markerBottomOffset,
            -startMarker.width,
            -startMarker.height,
        )
        endMarker.layoutParams = params
    }

    private fun isPlaying(): Boolean {
        return playbackController?.isPlaying ?: false
    }

    private fun resetPositions() {
        startPos = waveformView.secondsToPixels(0.0)
        endPos = waveformView.secondsToPixels(15.0).coerceAtMost(maxPos)
    }

    private fun trap(pos: Int): Int {
        if (pos < 0) return 0
        return pos.coerceAtMost(maxPos)
    }

    private fun setOffsetGoalStart() {
        setOffsetGoal(startPos - width / 2)
    }

    private fun setOffsetGoalStartNoUpdate() {
        setOffsetGoalNoUpdate(startPos - width / 2)
    }

    private fun setOffsetGoalEnd() {
        setOffsetGoal(endPos - width / 2)
    }

    private fun setOffsetGoalEndNoUpdate() {
        setOffsetGoalNoUpdate(endPos - width / 2)
    }

    private fun setOffsetGoal(offset: Int) {
        setOffsetGoalNoUpdate(offset)
        updateDisplay()
    }

    private fun setOffsetGoalNoUpdate(offset: Int) {
        if (touchDragging) {
            return
        }

        offsetGoal = offset
        if (offsetGoal + width / 2 > maxPos) {
            offsetGoal = maxPos - width / 2
        }
        if (offsetGoal < 0) {
            offsetGoal = 0
        }
    }

    private fun updatePositionFromText(text: String, isStart: Boolean) {
        val seconds = text.toDoubleOrNull() ?: return
        val position = waveformView.secondsToPixels(seconds)
        if (isStart) {
            startPos = position
        } else {
            endPos = position
        }
        updateDisplay()
    }

    private fun formatTime(pixels: Int): String {
        return if (waveformView.isInitialized) {
            formatDecimal(waveformView.pixelsToSeconds(pixels))
        } else {
            ""
        }
    }

    private fun formatDecimal(x: Double): String {
        var xWhole = x.toInt()
        var xFrac = (100 * (x - xWhole) + 0.5).toInt()

        if (xFrac >= 100) {
            xWhole++
            xFrac -= 100
            if (xFrac < 10) {
                xFrac *= 10
            }
        }

        return if (xFrac < 10) "$xWhole.0$xFrac" else "$xWhole.$xFrac"
    }

    @Synchronized
    private fun handlePause() {
        playbackController?.pause()
        isPlayingState = false
    }

    @Synchronized
    private fun onPlay(startPosition: Int) {
        if (playbackController == null) {
            return
        }

        if (isPlaying()) {
            handlePause()
            return
        }

        try {
            playbackController?.play(startPosition, startPos, endPos, maxPos)
            updateDisplay()
            isPlayingState = true
        } catch (e: Exception) {
            showFinalAlert(e, resources.getText(R.string.play_error))
        }
    }

    private fun showFinalAlert(e: Exception?, message: CharSequence) {
        val title: CharSequence =
            if (e != null) {
                resources.getText(R.string.alert_title_failure)
            } else {
                resources.getText(R.string.alert_title_success)
            }

        dialogController.showFinalAlert(title, message) { finish() }
    }

    private fun onSave() {
        if (isPlaying()) {
            handlePause()
        }

        dialogController.showFileSaveDialog(
            originalName = titleText ?: getString(R.string.edit_intent),
            onSave = { newTitle, newFileKind ->
                val file =
                    soundFile
                        ?: run {
                            showFinalAlert(Exception(), getString(R.string.write_error))
                            return@showFileSaveDialog
                        }
                ringtoneSaveManager.saveRingtone(
                    newTitle,
                    file,
                    waveformView,
                    startPos,
                    endPos,
                    wasGetContentIntent,
                    newFileKind,
                )
            },
            onDismiss = {},
        )
    }

    private fun requestMicPermission() {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun currentTime(): Long {
        return System.nanoTime() / 1_000_000
    }

    @Composable
    private fun WaveformArea() {
        AndroidView(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            factory = { context ->
                RelativeLayout(context).apply {
                    addView(
                        waveformView,
                        RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.MATCH_PARENT,
                            RelativeLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    addView(
                        startMarker,
                        RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                    addView(
                        endMarker,
                        RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
            },
        )
    }
}
