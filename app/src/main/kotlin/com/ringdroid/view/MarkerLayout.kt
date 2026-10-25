package com.ringdroid.view

import android.widget.RelativeLayout

/**
 * Positions the start and end markers over the waveform, hiding a marker once it scrolls off the
 * left edge.
 */
internal class MarkerLayout(
    private val startMarker: MarkerView,
    private val endMarker: MarkerView,
    private val waveformView: WaveformView,
    density: Float,
) {
    private val leftInset = (46 * density).toInt()
    private val rightInset = (48 * density).toInt()
    private val topOffset = (10 * density).toInt()
    private val bottomOffset = (10 * density).toInt()
    private var startVisible = true
    private var endVisible = true

    init {
        startMarker.alpha = 1f
        endMarker.alpha = 1f
    }

    fun place(
        startPos: Int,
        endPos: Int,
        offset: Int,
    ) {
        startVisible = updateVisibility(startMarker, startVisible, startPos - offset - leftInset)
        endVisible = updateVisibility(endMarker, endVisible, endPos - offset - endMarker.width + rightInset)
        val startX = if (startVisible) startPos - offset - leftInset else 0
        val endX = if (endVisible) endPos - offset - endMarker.width + rightInset else 0
        startMarker.layoutParams =
            params().apply { setMargins(startX, topOffset, -startMarker.width, -startMarker.height) }
        endMarker.layoutParams =
            params().apply {
                setMargins(
                    endX,
                    waveformView.measuredHeight - endMarker.height - bottomOffset,
                    -startMarker.width,
                    -startMarker.height,
                )
            }
    }

    private fun updateVisibility(
        marker: MarkerView,
        visible: Boolean,
        x: Int,
    ): Boolean {
        val onScreen = x + marker.width >= 0
        if (onScreen && !visible) {
            // Shown on the next frame to avoid flicker.
            marker.post { marker.alpha = 1f }
        } else if (!onScreen && visible) {
            marker.alpha = 0f
        }
        return onScreen
    }

    private fun params() = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
}
