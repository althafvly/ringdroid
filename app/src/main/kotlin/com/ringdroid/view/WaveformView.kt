package com.ringdroid.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.ringdroid.data.WaveformData
import com.ringdroid.data.WaveformScale
import com.ringdroid.soundfile.SoundFile
import kotlin.math.abs

/**
 * Draws the waveform of a sound file with the selection highlighted, a time grid, timecodes and
 * the playback position. Touches, flings and pinch zooms are passed to the listener; selection is
 * handled by the embedding activity.
 */
internal class WaveformView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    interface WaveformListener {
        fun waveformTouchStart(x: Float)

        fun waveformTouchMove(x: Float)

        fun waveformTouchEnd()

        fun waveformFling(x: Float)

        fun waveformDraw()

        fun waveformZoomIn()

        fun waveformZoomOut()
    }

    private val renderer = WaveformRenderer(context)

    private var soundFile: SoundFile? = null
    private var data: WaveformData? = null
    private var heightsAtThisZoomLevel: IntArray? = null
    private var level = 0
    private var sampleRate = 0
    private var samplesPerFrame = 0
    private var initialScaleSpan = 0f

    var listener: WaveformListener? = null
    var playbackPos = -1

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
                    if (scale - initialScaleSpan > PINCH_ZOOM_SPAN) {
                        listener?.waveformZoomIn()
                        initialScaleSpan = scale
                    }
                    if (scale - initialScaleSpan < -PINCH_ZOOM_SPAN) {
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
            while (level > value && canZoomIn) {
                zoomIn()
            }
            while (level < value && canZoomOut) {
                zoomOut()
            }
        }

    val canZoomIn: Boolean
        get() = level > 0

    val canZoomOut: Boolean
        get() = data != null && level < WaveformData.NUM_ZOOM_LEVELS - 1

    val maxPos: Int
        get() = data?.getLength(level) ?: 0

    val scale: WaveformScale
        get() = WaveformScale(sampleRate, samplesPerFrame, data?.getZoomFactor(level) ?: 0.0)

    /** Screen density, which scales the timecodes and the playback line. */
    var density = 1f
        set(value) {
            field = value
            renderer.density = value
            heightsAtThisZoomLevel = null
            invalidate()
        }

    fun zoomIn() {
        if (canZoomIn) {
            level--
            selectionStart *= 2
            selectionEnd *= 2
            offset = maxOf(0, (offset + measuredWidth / 2) * 2 - measuredWidth / 2)
            heightsAtThisZoomLevel = null
            invalidate()
        }
    }

    fun zoomOut() {
        if (canZoomOut) {
            level++
            selectionStart /= 2
            selectionEnd /= 2
            offset = maxOf(0, (offset + measuredWidth / 2) / 2 - measuredWidth / 2)
            heightsAtThisZoomLevel = null
            invalidate()
        }
    }

    fun setSelection(start: Int, end: Int, offset: Int) {
        selectionStart = start
        selectionEnd = end
        this.offset = offset
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (soundFile != null) {
            renderer.draw(canvas, this, heightsAtThisZoomLevel ?: computeHeightsForThisZoomLevel())
            listener?.waveformDraw()
        }
    }

    private fun computeHeightsForThisZoomLevel(): IntArray {
        val halfHeight = measuredHeight / 2 - 1
        val values = data?.getValues(level) ?: DoubleArray(0)
        return IntArray(values.size) { (values[it] * halfHeight).toInt() }.also {
            heightsAtThisZoomLevel =
                it
        }
    }

    private companion object {
        const val PINCH_ZOOM_SPAN = 40
    }
}
