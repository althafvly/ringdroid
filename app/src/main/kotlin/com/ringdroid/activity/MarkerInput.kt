package com.ringdroid.activity

import android.os.Handler
import com.ringdroid.data.EditorSelection
import com.ringdroid.view.MarkerView

/** Moves the selection when a marker is dragged, nudged with the arrow keys or focused. */
internal class MarkerInput(
    private val selection: EditorSelection,
    private val startMarker: MarkerView,
    private val handler: Handler,
    private val onChanged: () -> Unit,
) : MarkerView.MarkerListener {
    override fun markerTouchStart(marker: MarkerView, pos: Float) = selection.markerTouchStart(pos)

    override fun markerTouchMove(marker: MarkerView, pos: Float) {
        selection.markerTouchMove(marker === startMarker, pos)
        onChanged()
    }

    override fun markerTouchEnd(marker: MarkerView) {
        selection.markerTouchEnd(marker === startMarker)
        onChanged()
    }

    override fun markerLeft(marker: MarkerView, velocity: Int) {
        selection.nudgeLeft(marker === startMarker, velocity)
        onChanged()
    }

    override fun markerRight(marker: MarkerView, velocity: Int) {
        selection.nudgeRight(marker === startMarker, velocity)
        onChanged()
    }

    override fun markerKeyUp() {
        selection.scroll.keyDown = false
        onChanged()
    }

    override fun markerFocus(marker: MarkerView) {
        selection.focusMarker(marker === startMarker)
        // Delay so that a touch causing the focus is handled first.
        handler.postDelayed(onChanged, FOCUS_UPDATE_DELAY_MS)
    }

    private companion object {
        const val FOCUS_UPDATE_DELAY_MS = 100L
    }
}
