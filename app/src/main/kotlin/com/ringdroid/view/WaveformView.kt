package com.ringdroid.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.ringdroid.R
import com.ringdroid.data.WaveformData
import com.ringdroid.soundfile.SoundFile
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Draws the waveform of a sound file with the selection highlighted, a time grid, timecodes and
 * the playback position. Touches, flings and pinch zooms are passed to the listener; selection is
 * handled by the embedding activity.
 */
class WaveformView(
    context: Context,
    attrs: AttributeSet?,
) : View(context, attrs) {
    interface WaveformListener {
        fun waveformTouchStart(x: Float)

        fun waveformTouchMove(x: Float)

        fun waveformTouchEnd()

        fun waveformFling(x: Float)

        fun waveformDraw()

        fun waveformZoomIn()

        fun waveformZoomOut()
    }

    private fun paint(
        colorRes: Int,
        antiAlias: Boolean,
    ) = Paint().apply {
        isAntiAlias = antiAlias
        color = ContextCompat.getColor(context, colorRes)
    }

    private val gridPaint = paint(R.color.grid_line, false)
    private val selectedLinePaint = paint(R.color.waveform_selected, false)
    private val unselectedLinePaint = paint(R.color.waveform_unselected, false)
    private val unselectedBkgndLinePaint = paint(R.color.waveform_unselected_bkgnd_overlay, false)
    private val borderLinePaint =
        paint(R.color.selection_border, true).apply {
            strokeWidth = 1.5f
            pathEffect = DashPathEffect(floatArrayOf(3.0f, 2.0f), 0.0f)
        }
    private val playbackLinePaint = paint(R.color.playback_indicator, true)
    private val timecodePaint =
        paint(R.color.timecode, true).apply {
            textSize = 12f
            setShadowLayer(2f, 1f, 1f, ContextCompat.getColor(context, R.color.timecode_shadow))
        }

    private var soundFile: SoundFile? = null
    private var data: WaveformData? = null
    private var heightsAtThisZoomLevel: IntArray? = null
    private var level = 0
    private var sampleRate = 0
    private var samplesPerFrame = 0
    private var playbackPos = -1
    private var density = 1.0f
    private var initialScaleSpan = 0f

    var listener: WaveformListener? = null

    var selectionStart = 0
        private set
    var selectionEnd = 0
        private set
    var offset = 0
        private set
    var isInitialized = false
        private set

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    vx: Float,
                    vy: Float,
                ): Boolean {
                    listener?.waveformFling(vx)
                    return true
                }
            },
        )

    private val scaleGestureDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                    initialScaleSpan = abs(d.currentSpanX)
                    return true
                }

                override fun onScale(d: ScaleGestureDetector): Boolean {
                    val scale = abs(d.currentSpanX)
                    if (scale - initialScaleSpan > 40) {
                        listener?.waveformZoomIn()
                        initialScaleSpan = scale
                    }
                    if (scale - initialScaleSpan < -40) {
                        listener?.waveformZoomOut()
                        initialScaleSpan = scale
                    }
                    return true
                }
            },
        )

    init {
        // The markers get the key events.
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        if (gestureDetector.onTouchEvent(event)) {
            return true
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                listener?.waveformTouchStart(event.x)
            }

            MotionEvent.ACTION_MOVE -> {
                listener?.waveformTouchMove(event.x)
            }

            MotionEvent.ACTION_UP -> {
                listener?.waveformTouchEnd()
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun hasSoundFile(): Boolean = soundFile != null

    fun setSoundFile(soundFile: SoundFile?) {
        this.soundFile = soundFile
        val gains = soundFile?.frameGains
        if (soundFile != null && gains != null) {
            sampleRate = soundFile.sampleRate
            samplesPerFrame = soundFile.samplesPerFrame
            data = WaveformData(gains).also { level = it.initialZoomLevel }
            isInitialized = true
        }
        heightsAtThisZoomLevel = null
    }

    var zoomLevel: Int
        get() = level
        set(value) {
            while (level > value && canZoomIn()) {
                zoomIn()
            }
            while (level < value && canZoomOut()) {
                zoomOut()
            }
        }

    fun canZoomIn(): Boolean = level > 0

    fun canZoomOut(): Boolean = data != null && level < WaveformData.NUM_ZOOM_LEVELS - 1

    fun zoomIn() {
        if (canZoomIn()) {
            level--
            selectionStart *= 2
            selectionEnd *= 2
            offset = maxOf(0, (offset + measuredWidth / 2) * 2 - measuredWidth / 2)
            heightsAtThisZoomLevel = null
            invalidate()
        }
    }

    fun zoomOut() {
        if (canZoomOut()) {
            level++
            selectionStart /= 2
            selectionEnd /= 2
            offset = maxOf(0, (offset + measuredWidth / 2) / 2 - measuredWidth / 2)
            heightsAtThisZoomLevel = null
            invalidate()
        }
    }

    fun maxPos(): Int = data?.getLength(level) ?: 0

    fun secondsToFrames(seconds: Double): Int = (seconds * sampleRate / samplesPerFrame + 0.5).toInt()

    fun secondsToPixels(seconds: Double): Int {
        val z = data?.getZoomFactor(level) ?: return 0
        return (z * seconds * sampleRate / samplesPerFrame + 0.5).toInt()
    }

    fun pixelsToSeconds(pixels: Int): Double {
        val z = data?.getZoomFactor(level) ?: return 0.0
        return pixels * samplesPerFrame.toDouble() / (sampleRate * z)
    }

    fun millisecsToPixels(msecs: Int): Int {
        val z = data?.getZoomFactor(level) ?: return 0
        return (msecs * sampleRate * z / (1000.0 * samplesPerFrame) + 0.5).toInt()
    }

    fun pixelsToMillisecs(pixels: Int): Int {
        val z = data?.getZoomFactor(level) ?: return 0
        return (pixels * (1000.0 * samplesPerFrame) / (sampleRate * z) + 0.5).toInt()
    }

    fun setParameters(
        start: Int,
        end: Int,
        offset: Int,
    ) {
        selectionStart = start
        selectionEnd = end
        this.offset = offset
    }

    fun setPlayback(pos: Int) {
        playbackPos = pos
    }

    fun recomputeHeights(density: Float) {
        heightsAtThisZoomLevel = null
        this.density = density
        timecodePaint.textSize = (12 * density).toInt().toFloat()
        playbackLinePaint.strokeWidth = 2.5f * density
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (soundFile == null) {
            return
        }
        val heights = heightsAtThisZoomLevel ?: computeHeightsForThisZoomLevel()
        val measuredWidth = measuredWidth
        val measuredHeight = measuredHeight.toFloat()
        val start = offset
        val width = minOf(heights.size - start, measuredWidth)
        val ctr = (this.measuredHeight / 2).toFloat()

        // Grid lines every second, or every five seconds when zoomed out.
        val onePixelInSecs = pixelsToSeconds(1)
        val onlyEveryFiveSecs = onePixelInSecs > 1.0 / 50.0
        var fractionalSecs = offset * onePixelInSecs
        var integerSecs = fractionalSecs.toInt()
        for (i in 1..width) {
            fractionalSecs += onePixelInSecs
            val integerSecsNew = fractionalSecs.toInt()
            if (integerSecsNew != integerSecs) {
                integerSecs = integerSecsNew
                if (!onlyEveryFiveSecs || integerSecs % 5 == 0) {
                    canvas.drawLine(i.toFloat(), 0f, i.toFloat(), measuredHeight, gridPaint)
                }
            }
        }

        for (i in 0 until width) {
            val x = i.toFloat()
            val paint =
                if (i + start in selectionStart until selectionEnd) {
                    selectedLinePaint
                } else {
                    canvas.drawLine(x, 0f, x, measuredHeight, unselectedBkgndLinePaint)
                    unselectedLinePaint
                }
            val h = heights[start + i]
            canvas.drawLine(x, ctr - h, x, ctr + 1 + h, paint)
            if (i + start == playbackPos) {
                canvas.drawLine(x, 0f, x, measuredHeight, playbackLinePaint)
            }
        }

        // Past the right edge of the waveform, draw the area as unselected.
        for (i in maxOf(width, 0) until measuredWidth) {
            canvas.drawLine(i.toFloat(), 0f, i.toFloat(), measuredHeight, unselectedBkgndLinePaint)
        }

        canvas.drawLine(selectionStart - offset + 0.5f, 30f, selectionStart - offset + 0.5f, measuredHeight, borderLinePaint)
        canvas.drawLine(selectionEnd - offset + 0.5f, 0f, selectionEnd - offset + 0.5f, measuredHeight - 30, borderLinePaint)

        // Timecodes, spaced so that they don't overlap.
        val minimumTimecodeSpacing = timecodePaint.measureText("0:00") + 16 * density
        val timecodeIntervalSecs = maxOf(1.0, ceil(minimumTimecodeSpacing * onePixelInSecs))
        fractionalSecs = offset * onePixelInSecs
        var integerTimecode = (fractionalSecs / timecodeIntervalSecs).toInt()
        for (i in 1..width) {
            fractionalSecs += onePixelInSecs
            integerSecs = fractionalSecs.toInt()
            val integerTimecodeNew = (fractionalSecs / timecodeIntervalSecs).toInt()
            if (integerTimecodeNew != integerTimecode) {
                integerTimecode = integerTimecodeNew
                // e.g. 67 seconds -> "1:07"
                val timecode = "${integerSecs / 60}:${(integerSecs % 60).toString().padStart(2, '0')}"
                val halfWidth = 0.5f * timecodePaint.measureText(timecode)
                canvas.drawText(timecode, i - halfWidth, (12 * density).toInt().toFloat(), timecodePaint)
            }
        }
        listener?.waveformDraw()
    }

    private fun computeHeightsForThisZoomLevel(): IntArray {
        val halfHeight = measuredHeight / 2 - 1
        val values = data?.getValues(level) ?: DoubleArray(0)
        return IntArray(values.size) { (values[it] * halfHeight).toInt() }.also { heightsAtThisZoomLevel = it }
    }
}
