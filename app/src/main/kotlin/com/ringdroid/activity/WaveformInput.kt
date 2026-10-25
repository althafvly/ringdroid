package com.ringdroid.activity

import android.os.SystemClock
import com.ringdroid.data.EditorSelection
import com.ringdroid.view.WaveformView

/** Scrolls, flings and zooms the waveform. A short tap plays from there, or seeks while playing. */
internal class WaveformInput(
    private val selection: EditorSelection,
    private val waveformView: WaveformView,
    private val playback: EditorPlayback,
    private val onChanged: () -> Unit,
) : WaveformView.WaveformListener {
    private var touchStartMsec = 0L

    /** Each redraw animates the scroll position towards its goal. */
    override fun waveformDraw() {
        selection.scroll.width = waveformView.measuredWidth
        if (selection.scroll.isAnimating || playback.isPlaying) {
            onChanged()
        }
    }

    override fun waveformTouchStart(x: Float) {
        selection.scroll.touchStart(x)
        touchStartMsec = SystemClock.uptimeMillis()
    }

    override fun waveformTouchMove(x: Float) {
        selection.scroll.touchMove(x)
        onChanged()
    }

    override fun waveformTouchEnd() {
        val tapPos = selection.scroll.touchEnd()
        if (SystemClock.uptimeMillis() - touchStartMsec < TAP_TIMEOUT_MS) {
            playback.tap(tapPos)
        }
    }

    override fun waveformFling(x: Float) {
        selection.scroll.fling(x)
        onChanged()
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
        selection.syncZoom(
            waveformView.selectionStart,
            waveformView.selectionEnd,
            waveformView.maxPos,
            waveformView.offset,
        )
        onChanged()
    }

    private companion object {
        const val TAP_TIMEOUT_MS = 300
    }
}
