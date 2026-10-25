package com.ringdroid.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.core.content.ContextCompat
import com.ringdroid.R
import kotlin.math.ceil

/** Draws the time grid, the waveform with its selection and playback position, and the timecodes. */
internal class WaveformRenderer(
    private val context: Context,
) {
    private val gridPaint = paint(R.color.grid_line, false)
    private val selectedLinePaint = paint(R.color.waveform_selected, false)
    private val unselectedLinePaint = paint(R.color.waveform_unselected, false)
    private val unselectedBkgndLinePaint = paint(R.color.waveform_unselected_bkgnd_overlay, false)
    private val borderLinePaint =
        paint(R.color.selection_border, true).apply {
            strokeWidth = BORDER_WIDTH
            pathEffect = DashPathEffect(floatArrayOf(BORDER_DASH, BORDER_GAP), 0.0f)
        }
    private val playbackLinePaint = paint(R.color.playback_indicator, true)
    private val timecodePaint =
        paint(R.color.timecode, true).apply {
            textSize = TIMECODE_TEXT_SIZE
            setShadowLayer(2f, 1f, 1f, ContextCompat.getColor(context, R.color.timecode_shadow))
        }

    var density = 1f
        set(value) {
            field = value
            timecodePaint.textSize = (TIMECODE_TEXT_SIZE * value).toInt().toFloat()
            playbackLinePaint.strokeWidth = PLAYBACK_LINE_WIDTH * value
        }

    fun draw(
        canvas: Canvas,
        view: WaveformView,
        heights: IntArray,
    ) {
        val secondsPerPixel = view.scale.pixelsToSeconds(1)
        val width = minOf(heights.size - view.offset, view.measuredWidth)
        drawGrid(canvas, view, width, secondsPerPixel)
        drawWaveform(canvas, view, heights, width)
        drawBorders(canvas, view)
        drawTimecodes(canvas, view.offset, width, secondsPerPixel)
    }

    // Grid lines every second, or every five seconds when zoomed out.
    private fun drawGrid(
        canvas: Canvas,
        view: WaveformView,
        width: Int,
        secondsPerPixel: Double,
    ) {
        val height = view.measuredHeight.toFloat()
        val zoomedOut = secondsPerPixel * MIN_PIXELS_PER_SECOND > 1
        var seconds = view.offset * secondsPerPixel
        var wholeSeconds = seconds.toInt()
        for (i in 1..width) {
            seconds += secondsPerPixel
            if (seconds.toInt() != wholeSeconds) {
                wholeSeconds = seconds.toInt()
                if (!zoomedOut || wholeSeconds % ZOOMED_OUT_GRID_SECONDS == 0) {
                    canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height, gridPaint)
                }
            }
        }
    }

    private fun drawWaveform(
        canvas: Canvas,
        view: WaveformView,
        heights: IntArray,
        width: Int,
    ) {
        val height = view.measuredHeight.toFloat()
        val center = (view.measuredHeight / 2).toFloat()
        val start = view.offset
        for (i in 0 until width) {
            val x = i.toFloat()
            val paint =
                if (i + start in view.selectionStart until view.selectionEnd) {
                    selectedLinePaint
                } else {
                    canvas.drawLine(x, 0f, x, height, unselectedBkgndLinePaint)
                    unselectedLinePaint
                }
            val h = heights[start + i]
            canvas.drawLine(x, center - h, x, center + 1 + h, paint)
            if (i + start == view.playbackPos) {
                canvas.drawLine(x, 0f, x, height, playbackLinePaint)
            }
        }
        // Past the right edge of the waveform, draw the area as unselected.
        for (i in maxOf(width, 0) until view.measuredWidth) {
            canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height, unselectedBkgndLinePaint)
        }
    }

    private fun drawBorders(
        canvas: Canvas,
        view: WaveformView,
    ) {
        val height = view.measuredHeight.toFloat()
        val startX = view.selectionStart - view.offset + HALF_PIXEL
        val endX = view.selectionEnd - view.offset + HALF_PIXEL
        canvas.drawLine(startX, BORDER_INSET, startX, height, borderLinePaint)
        canvas.drawLine(endX, 0f, endX, height - BORDER_INSET, borderLinePaint)
    }

    // Timecodes, spaced so that they don't overlap.
    private fun drawTimecodes(
        canvas: Canvas,
        offset: Int,
        width: Int,
        secondsPerPixel: Double,
    ) {
        val minimumSpacing = timecodePaint.measureText("0:00") + TIMECODE_SPACING * density
        val intervalSecs = maxOf(1.0, ceil(minimumSpacing * secondsPerPixel))
        var seconds = offset * secondsPerPixel
        var interval = (seconds / intervalSecs).toInt()
        for (i in 1..width) {
            seconds += secondsPerPixel
            if ((seconds / intervalSecs).toInt() != interval) {
                interval = (seconds / intervalSecs).toInt()
                val wholeSeconds = seconds.toInt()
                // e.g. 67 seconds -> "1:07"
                val minutes = wholeSeconds / SECONDS_PER_MINUTE
                val timecode = "$minutes:${(wholeSeconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
                val halfWidth = timecodePaint.measureText(timecode) / 2
                canvas.drawText(timecode, i - halfWidth, timecodePaint.textSize, timecodePaint)
            }
        }
    }

    private fun paint(
        colorRes: Int,
        antiAlias: Boolean,
    ) = Paint().apply {
        isAntiAlias = antiAlias
        color = ContextCompat.getColor(context, colorRes)
    }

    private companion object {
        const val TIMECODE_TEXT_SIZE = 12f
        const val TIMECODE_SPACING = 16
        const val PLAYBACK_LINE_WIDTH = 2.5f
        const val BORDER_INSET = 30f
        const val BORDER_WIDTH = 1.5f
        const val BORDER_DASH = 3f
        const val BORDER_GAP = 2f
        const val HALF_PIXEL = 0.5f
        const val MIN_PIXELS_PER_SECOND = 50
        const val ZOOMED_OUT_GRID_SECONDS = 5
        const val SECONDS_PER_MINUTE = 60
    }
}
