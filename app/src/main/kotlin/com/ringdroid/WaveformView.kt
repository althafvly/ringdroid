/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ringdroid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import com.ringdroid.soundfile.SoundFile
import kotlin.math.abs

/**
 * WaveformView is an Android view that displays a visual representation of an
 * audio waveform. It retrieves the frame gains from a CheapSoundFile object and
 * recomputes the shape contour at several zoom levels.
 *
 *
 *
 * This class doesn't handle selection or any of the touch interactions
 * directly, so it exposes a listener interface. The class that embeds this view
 * should add itself as a listener and make the view scroll and respond to other
 * events appropriately.
 *
 *
 *
 * WaveformView doesn't actually handle selection, but it will just display the
 * selected part of the waveform in a different color.
 */
class WaveformView(
    context: Context,
    attrs: AttributeSet?,
) : View(context, attrs) {
    // Colors
    private val mGridPaint: Paint

    private val mSelectedLinePaint: Paint
    private val mUnselectedLinePaint: Paint
    private val mUnselectedBkgndLinePaint: Paint
    private val mBorderLinePaint: Paint
    private val mPlaybackLinePaint: Paint
    private val mTimecodePaint: Paint
    private val mGestureDetector: GestureDetector
    private val mScaleGestureDetector: ScaleGestureDetector
    private var mSoundFile: SoundFile?
    private var mLenByZoomLevel: IntArray?
    private var mValuesByZoomLevel: Array<DoubleArray?>?
    private var mZoomFactorByZoomLevel: DoubleArray = doubleArrayOf()
    private var mHeightsAtThisZoomLevel: IntArray?
    private var mZoomLevel = 0
    private var mNumZoomLevels = 0
    private var mSampleRate = 0
    private var mSamplesPerFrame = 0
    var offset: Int
        private set
    var start: Int
        private set
    var end: Int
        private set
    private var mPlaybackPos: Int
    private var mDensity: Float
    private var mInitialScaleSpan = 0f
    private var mListener: WaveformListener? = null
    var isInitialized: Boolean
        private set

    init {
        // We don't want keys, the markers get these
        setFocusable(false)

        val res = resources
        mGridPaint = Paint()
        mGridPaint.isAntiAlias = false
        mGridPaint.color = res.getColor(R.color.grid_line)
        mSelectedLinePaint = Paint()
        mSelectedLinePaint.isAntiAlias = false
        mSelectedLinePaint.color = res.getColor(R.color.waveform_selected)
        mUnselectedLinePaint = Paint()
        mUnselectedLinePaint.isAntiAlias = false
        mUnselectedLinePaint.color = res.getColor(R.color.waveform_unselected)
        mUnselectedBkgndLinePaint = Paint()
        mUnselectedBkgndLinePaint.isAntiAlias = false
        mUnselectedBkgndLinePaint.color = res.getColor(R.color.waveform_unselected_bkgnd_overlay)
        mBorderLinePaint = Paint()
        mBorderLinePaint.isAntiAlias = true
        mBorderLinePaint.strokeWidth = 1.5f
        mBorderLinePaint.pathEffect = DashPathEffect(floatArrayOf(3.0f, 2.0f), 0.0f)
        mBorderLinePaint.color = res.getColor(R.color.selection_border)
        mPlaybackLinePaint = Paint()
        mPlaybackLinePaint.isAntiAlias = false
        mPlaybackLinePaint.color = res.getColor(R.color.playback_indicator)
        mTimecodePaint = Paint()
        mTimecodePaint.textSize = 12f
        mTimecodePaint.isAntiAlias = true
        mTimecodePaint.color = res.getColor(R.color.timecode)
        mTimecodePaint.setShadowLayer(2f, 1f, 1f, res.getColor(R.color.timecode_shadow))

        mGestureDetector =
            GestureDetector(
                context,
                object : SimpleOnGestureListener() {
                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        mListener!!.waveformFling(velocityX)
                        return true
                    }
                },
            )

        mScaleGestureDetector =
            ScaleGestureDetector(
                context,
                object : SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                        Log.v("Ringdroid", "ScaleBegin " + d.currentSpanX)
                        mInitialScaleSpan = abs(d.currentSpanX)
                        return true
                    }

                    override fun onScale(d: ScaleGestureDetector): Boolean {
                        val scale = abs(d.currentSpanX)
                        Log.v("Ringdroid", "Scale " + (scale - mInitialScaleSpan))
                        if (scale - mInitialScaleSpan > 40) {
                            mListener!!.waveformZoomIn()
                            mInitialScaleSpan = scale
                        }
                        if (scale - mInitialScaleSpan < -40) {
                            mListener!!.waveformZoomOut()
                            mInitialScaleSpan = scale
                        }
                        return true
                    }

                    override fun onScaleEnd(d: ScaleGestureDetector) {
                        Log.v("Ringdroid", "ScaleEnd " + d.currentSpanX)
                    }
                },
            )

        mSoundFile = null
        mLenByZoomLevel = null
        mValuesByZoomLevel = null
        mHeightsAtThisZoomLevel = null
        this.offset = 0
        mPlaybackPos = -1
        this.start = 0
        this.end = 0
        mDensity = 1.0f
        this.isInitialized = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        mScaleGestureDetector.onTouchEvent(event)
        if (mGestureDetector.onTouchEvent(event)) {
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> mListener!!.waveformTouchStart(event.x)
            MotionEvent.ACTION_MOVE -> mListener!!.waveformTouchMove(event.x)
            MotionEvent.ACTION_UP -> mListener!!.waveformTouchEnd()
        }
        return true
    }

    fun hasSoundFile(): Boolean = mSoundFile != null

    fun setSoundFile(soundFile: SoundFile?) {
        mSoundFile = soundFile
        mSampleRate = mSoundFile!!.sampleRate
        mSamplesPerFrame = mSoundFile!!.samplesPerFrame
        computeDoublesForAllZoomLevels()
        mHeightsAtThisZoomLevel = null
    }

    var zoomLevel: Int
        get() = mZoomLevel
        set(zoomLevel) {
            while (mZoomLevel > zoomLevel) {
                zoomIn()
            }
            while (mZoomLevel < zoomLevel) {
                zoomOut()
            }
        }

    fun canZoomIn(): Boolean = (mZoomLevel > 0)

    fun zoomIn() {
        if (canZoomIn()) {
            mZoomLevel--
            this.start *= 2
            this.end *= 2
            mHeightsAtThisZoomLevel = null
            var offsetCenter = this.offset + measuredWidth / 2
            offsetCenter *= 2
            this.offset = offsetCenter - measuredWidth / 2
            if (this.offset < 0) this.offset = 0
            invalidate()
        }
    }

    fun canZoomOut(): Boolean = (mZoomLevel < mNumZoomLevels - 1)

    fun zoomOut() {
        if (canZoomOut()) {
            mZoomLevel++
            this.start /= 2
            this.end /= 2
            var offsetCenter = this.offset + measuredWidth / 2
            offsetCenter /= 2
            this.offset = offsetCenter - measuredWidth / 2
            if (this.offset < 0) this.offset = 0
            mHeightsAtThisZoomLevel = null
            invalidate()
        }
    }

    fun maxPos(): Int = mLenByZoomLevel!![mZoomLevel]

    fun secondsToFrames(seconds: Double): Int = (1.0 * seconds * mSampleRate / mSamplesPerFrame + 0.5).toInt()

    fun secondsToPixels(seconds: Double): Int {
        val z = mZoomFactorByZoomLevel[mZoomLevel]
        return (z * seconds * mSampleRate / mSamplesPerFrame + 0.5).toInt()
    }

    fun pixelsToSeconds(pixels: Int): Double {
        val z = mZoomFactorByZoomLevel[mZoomLevel]
        return (pixels * mSamplesPerFrame.toDouble() / (mSampleRate * z))
    }

    fun millisecsToPixels(msecs: Int): Int {
        val z = mZoomFactorByZoomLevel[mZoomLevel]
        return ((msecs * 1.0 * mSampleRate * z) / (1000.0 * mSamplesPerFrame) + 0.5).toInt()
    }

    fun pixelsToMillisecs(pixels: Int): Int {
        val z = mZoomFactorByZoomLevel[mZoomLevel]
        return (pixels * (1000.0 * mSamplesPerFrame) / (mSampleRate * z) + 0.5).toInt()
    }

    fun setParameters(
        start: Int,
        end: Int,
        offset: Int,
    ) {
        this.start = start
        this.end = end
        this.offset = offset
    }

    fun setPlayback(pos: Int) {
        mPlaybackPos = pos
    }

    fun setListener(listener: WaveformListener?) {
        mListener = listener
    }

    fun recomputeHeights(density: Float) {
        mHeightsAtThisZoomLevel = null
        mDensity = density
        mTimecodePaint.textSize = (12 * density).toInt().toFloat()

        invalidate()
    }

    private fun drawWaveformLine(
        canvas: Canvas,
        x: Int,
        y0: Int,
        y1: Int,
        paint: Paint,
    ) {
        canvas.drawLine(x.toFloat(), y0.toFloat(), x.toFloat(), y1.toFloat(), paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mSoundFile == null) return

        if (mHeightsAtThisZoomLevel == null) computeIntsForThisZoomLevel()

        // Draw waveform
        val measuredWidth = getMeasuredWidth()
        val measuredHeight = getMeasuredHeight()
        val start = this.offset
        var width = mHeightsAtThisZoomLevel!!.size - start
        val ctr = measuredHeight / 2

        if (width > measuredWidth) width = measuredWidth

        // Draw grid
        val onePixelInSecs = pixelsToSeconds(1)
        val onlyEveryFiveSecs = (onePixelInSecs > 1.0 / 50.0)
        var fractionalSecs = this.offset * onePixelInSecs
        var integerSecs = fractionalSecs.toInt()
        var i = 0
        while (i < width) {
            i++
            fractionalSecs += onePixelInSecs
            val integerSecsNew = fractionalSecs.toInt()
            if (integerSecsNew != integerSecs) {
                integerSecs = integerSecsNew
                if (!onlyEveryFiveSecs || 0 == (integerSecs % 5)) {
                    canvas.drawLine(
                        i.toFloat(),
                        0f,
                        i.toFloat(),
                        measuredHeight.toFloat(),
                        mGridPaint,
                    )
                }
            }
        }

        // Draw waveform
        i = 0
        while (i < width) {
            val paint: Paint
            if (i + start >= this.start && i + start < this.end) {
                paint = mSelectedLinePaint
            } else {
                drawWaveformLine(canvas, i, 0, measuredHeight, mUnselectedBkgndLinePaint)
                paint = mUnselectedLinePaint
            }
            drawWaveformLine(
                canvas,
                i,
                ctr - mHeightsAtThisZoomLevel!![start + i],
                ctr + 1 + mHeightsAtThisZoomLevel!![start + i],
                paint,
            )

            if (i + start == mPlaybackPos) {
                canvas.drawLine(
                    i.toFloat(),
                    0f,
                    i.toFloat(),
                    measuredHeight.toFloat(),
                    mPlaybackLinePaint,
                )
            }
            i++
        }

        // If we can see the right edge of the waveform, draw the
        // non-waveform area to the right as unselected
        i = width
        while (i < measuredWidth) {
            drawWaveformLine(canvas, i, 0, measuredHeight, mUnselectedBkgndLinePaint)
            i++
        }

        // Draw borders
        canvas.drawLine(
            this.start - this.offset + 0.5f,
            30f,
            this.start - this.offset + 0.5f,
            measuredHeight.toFloat(),
            mBorderLinePaint,
        )
        canvas.drawLine(
            this.end - this.offset + 0.5f,
            0f,
            this.end - this.offset + 0.5f,
            (measuredHeight - 30).toFloat(),
            mBorderLinePaint,
        )

        // Draw timecode
        var timecodeIntervalSecs = 1.0
        if (timecodeIntervalSecs / onePixelInSecs < 50) {
            timecodeIntervalSecs = 5.0
        }
        if (timecodeIntervalSecs / onePixelInSecs < 50) {
            timecodeIntervalSecs = 15.0
        }

        // Draw grid
        fractionalSecs = this.offset * onePixelInSecs
        var integerTimecode = (fractionalSecs / timecodeIntervalSecs).toInt()
        i = 0
        while (i < width) {
            i++
            fractionalSecs += onePixelInSecs
            integerSecs = fractionalSecs.toInt()
            val integerTimecodeNew = (fractionalSecs / timecodeIntervalSecs).toInt()
            if (integerTimecodeNew != integerTimecode) {
                integerTimecode = integerTimecodeNew

                // Turn, e.g. 67 seconds into "1:07"
                val timecodeMinutes = "" + (integerSecs / 60)
                var timecodeSeconds = "" + (integerSecs % 60)
                if ((integerSecs % 60) < 10) {
                    timecodeSeconds = "0$timecodeSeconds"
                }
                val timecodeStr = "$timecodeMinutes:$timecodeSeconds"
                val offset = (0.5 * mTimecodePaint.measureText(timecodeStr)).toFloat()
                canvas.drawText(
                    timecodeStr,
                    i - offset,
                    (12 * mDensity).toInt().toFloat(),
                    mTimecodePaint,
                )
            }
        }

        if (mListener != null) {
            mListener!!.waveformDraw()
        }
    }

    /** Called once when a new sound file is added  */
    private fun computeDoublesForAllZoomLevels() {
        val numFrames = mSoundFile!!.numFrames
        val frameGains = mSoundFile!!.frameGains
        val smoothedGains = DoubleArray(numFrames)
        if (numFrames == 1) {
            smoothedGains[0] = frameGains[0].toDouble()
        } else if (numFrames == 2) {
            smoothedGains[0] = frameGains[0].toDouble()
            smoothedGains[1] = frameGains[1].toDouble()
        } else if (numFrames > 2) {
            smoothedGains[0] = (frameGains[0] / 2.0) + (frameGains[1] / 2.0)
            for (i in 1..<numFrames - 1) {
                smoothedGains[i] =
                    (frameGains[i - 1] / 3.0) + (frameGains[i] / 3.0) + (frameGains[i + 1] / 3.0)
            }
            smoothedGains[numFrames - 1] =
                (frameGains[numFrames - 2] / 2.0) + (frameGains[numFrames - 1] / 2.0)
        }

        // Make sure the range is no more than 0 - 255
        var maxGain = 1.0
        for (i in 0..<numFrames) {
            if (smoothedGains[i] > maxGain) {
                maxGain = smoothedGains[i]
            }
        }
        var scaleFactor = 1.0
        if (maxGain > 255.0) {
            scaleFactor = 255 / maxGain
        }

        // Build histogram of 256 bins and figure out the new scaled max
        maxGain = 0.0
        val gainHist = IntArray(256)
        for (i in 0..<numFrames) {
            var smoothedGain = (smoothedGains[i] * scaleFactor).toInt()
            if (smoothedGain < 0) smoothedGain = 0
            if (smoothedGain > 255) smoothedGain = 255

            if (smoothedGain > maxGain) maxGain = smoothedGain.toDouble()

            gainHist[smoothedGain]++
        }

        // Re-calibrate the min to be 5%
        var minGain = 0.0
        var sum = 0
        while (minGain < 255 && sum < numFrames / 20) {
            sum += gainHist[minGain.toInt()]
            minGain++
        }

        // Re-calibrate the max to be 99%
        sum = 0
        while (maxGain > 2 && sum < numFrames / 100) {
            sum += gainHist[maxGain.toInt()]
            maxGain--
        }

        // Compute the heights
        val heights = DoubleArray(numFrames)
        val range = maxGain - minGain
        for (i in 0..<numFrames) {
            var value = (smoothedGains[i] * scaleFactor - minGain) / range
            if (value < 0.0) value = 0.0
            if (value > 1.0) value = 1.0
            heights[i] = value * value
        }

        mNumZoomLevels = 5
        mLenByZoomLevel = IntArray(5)
        mZoomFactorByZoomLevel = DoubleArray(5)
        mValuesByZoomLevel = arrayOfNulls(5)

        // Level 0 is doubled, with interpolated values
        mLenByZoomLevel!![0] = numFrames * 2
        mZoomFactorByZoomLevel[0] = 2.0
        mValuesByZoomLevel!![0] = DoubleArray(mLenByZoomLevel!![0])
        if (numFrames > 0) {
            mValuesByZoomLevel!![0]!![0] = 0.5 * heights[0]
            mValuesByZoomLevel!![0]!![1] = heights[0]
        }
        for (i in 1..<numFrames) {
            mValuesByZoomLevel!![0]!![2 * i] = 0.5 * (heights[i - 1] + heights[i])
            mValuesByZoomLevel!![0]!![2 * i + 1] = heights[i]
        }

        // Level 1 is normal
        mLenByZoomLevel!![1] = numFrames
        mValuesByZoomLevel!![1] = DoubleArray(mLenByZoomLevel!![1])
        mZoomFactorByZoomLevel[1] = 1.0
        System.arraycopy(heights, 0, mValuesByZoomLevel!![1]!!, 0, mLenByZoomLevel!![1])

        // 3 more levels are each halved
        for (j in 2..4) {
            mLenByZoomLevel!![j] = mLenByZoomLevel!![j - 1] / 2
            mValuesByZoomLevel!![j] = DoubleArray(mLenByZoomLevel!![j])
            mZoomFactorByZoomLevel[j] = mZoomFactorByZoomLevel[j - 1] / 2.0
            for (i in 0..<mLenByZoomLevel!![j]) {
                mValuesByZoomLevel!![j]!![i] = (
                    0.5 *
                        (mValuesByZoomLevel!![j - 1]!![2 * i] + mValuesByZoomLevel!![j - 1]!![2 * i + 1])
                )
            }
        }

        mZoomLevel =
            if (numFrames > 5000) {
                3
            } else if (numFrames > 1000) {
                2
            } else if (numFrames > 300) {
                1
            } else {
                0
            }

        this.isInitialized = true
    }

    /**
     * Called the first time we need to draw when the zoom level has changed or the
     * screen is resized
     */
    private fun computeIntsForThisZoomLevel() {
        val halfHeight = (measuredHeight / 2) - 1
        mHeightsAtThisZoomLevel = IntArray(mLenByZoomLevel!![mZoomLevel])
        for (i in 0..<mLenByZoomLevel!![mZoomLevel]) {
            mHeightsAtThisZoomLevel!![i] =
                (mValuesByZoomLevel!![mZoomLevel]!![i] * halfHeight).toInt()
        }
    }

    interface WaveformListener {
        fun waveformTouchStart(x: Float)

        fun waveformTouchMove(x: Float)

        fun waveformTouchEnd()

        fun waveformFling(x: Float)

        fun waveformDraw()

        fun waveformZoomIn()

        fun waveformZoomOut()
    }
}
