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

package com.ringdroid.editor;

import com.ringdroid.R;
import com.ringdroid.soundfile.SoundFile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * WaveformView is an Android view that displays a visual representation of an audio waveform. It
 * retrieves the frame gains from a CheapSoundFile object and recomputes the shape contour at
 * several zoom levels.
 *
 * <p>
 * This class doesn't handle selection or any of the touch interactions directly, so it exposes a
 * listener interface. The class that embeds this view should add itself as a listener and make the
 * view scroll and respond to other events appropriately.
 *
 * <p>
 * WaveformView doesn't actually handle selection, but it will just display the selected part of the
 * waveform in a different color.
 */
public class WaveformView extends View {
    private static final String TAG = "WaveformView";

    // Colors
    private final Paint mGridPaint;

    private final Paint mSelectedLinePaint;
    private final Paint mUnselectedLinePaint;
    private final Paint mUnselectedBkgndLinePaint;
    private final Paint mBorderLinePaint;
    private final Paint mPlaybackLinePaint;
    private final Paint mTimecodePaint;
    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleGestureDetector;
    private SoundFile mSoundFile;
    private WaveformData mData;
    private int[] mHeightsAtThisZoomLevel;
    private int mZoomLevel;
    private int mSampleRate;
    private int mSamplesPerFrame;
    private int mOffset;
    private int mSelectionStart;
    private int mSelectionEnd;
    private int mPlaybackPos;
    private float mDensity;
    private float mInitialScaleSpan;
    private WaveformListener mListener;
    private boolean mInitialized;

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // We don't want keys, the markers get these
        setFocusable(false);

        mGridPaint = new Paint();
        mGridPaint.setAntiAlias(false);
        mGridPaint.setColor(ContextCompat.getColor(context, R.color.grid_line));
        mSelectedLinePaint = new Paint();
        mSelectedLinePaint.setAntiAlias(false);
        mSelectedLinePaint.setColor(ContextCompat.getColor(context, R.color.waveform_selected));
        mUnselectedLinePaint = new Paint();
        mUnselectedLinePaint.setAntiAlias(false);
        mUnselectedLinePaint.setColor(ContextCompat.getColor(context, R.color.waveform_unselected));
        mUnselectedBkgndLinePaint = new Paint();
        mUnselectedBkgndLinePaint.setAntiAlias(false);
        mUnselectedBkgndLinePaint.setColor(
                ContextCompat.getColor(context, R.color.waveform_unselected_bkgnd_overlay));
        mBorderLinePaint = new Paint();
        mBorderLinePaint.setAntiAlias(true);
        mBorderLinePaint.setStrokeWidth(1.5f);
        mBorderLinePaint.setPathEffect(new DashPathEffect(new float[]{3.0f, 2.0f}, 0.0f));
        mBorderLinePaint.setColor(ContextCompat.getColor(context, R.color.selection_border));
        mPlaybackLinePaint = new Paint();
        mPlaybackLinePaint.setAntiAlias(true);
        mPlaybackLinePaint.setColor(ContextCompat.getColor(context, R.color.playback_indicator));
        mTimecodePaint = new Paint();
        mTimecodePaint.setTextSize(12);
        mTimecodePaint.setAntiAlias(true);
        mTimecodePaint.setColor(ContextCompat.getColor(context, R.color.timecode));
        mTimecodePaint.setShadowLayer(2, 1, 1,
                ContextCompat.getColor(context, R.color.timecode_shadow));

        mGestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                        mListener.waveformFling(vx);
                        return true;
                    }
                });

        mScaleGestureDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    public boolean onScaleBegin(ScaleGestureDetector d) {
                        Log.v(TAG, "ScaleBegin " + d.getCurrentSpanX());
                        mInitialScaleSpan = Math.abs(d.getCurrentSpanX());
                        return true;
                    }

                    public boolean onScale(ScaleGestureDetector d) {
                        float scale = Math.abs(d.getCurrentSpanX());
                        Log.v(TAG, "Scale " + (scale - mInitialScaleSpan));
                        if (scale - mInitialScaleSpan > 40) {
                            mListener.waveformZoomIn();
                            mInitialScaleSpan = scale;
                        }
                        if (scale - mInitialScaleSpan < -40) {
                            mListener.waveformZoomOut();
                            mInitialScaleSpan = scale;
                        }
                        return true;
                    }

                    public void onScaleEnd(ScaleGestureDetector d) {
                        Log.v(TAG, "ScaleEnd " + d.getCurrentSpanX());
                    }
                });

        mSoundFile = null;
        mHeightsAtThisZoomLevel = null;
        mOffset = 0;
        mPlaybackPos = -1;
        mSelectionStart = 0;
        mSelectionEnd = 0;
        mDensity = 1.0f;
        mInitialized = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleGestureDetector.onTouchEvent(event);
        if (mGestureDetector.onTouchEvent(event)) {
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN :
                mListener.waveformTouchStart(event.getX());
                break;
            case MotionEvent.ACTION_MOVE :
                mListener.waveformTouchMove(event.getX());
                break;
            case MotionEvent.ACTION_UP :
                mListener.waveformTouchEnd();
                performClick();
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public boolean hasSoundFile() {
        return mSoundFile != null;
    }

    public void setSoundFile(SoundFile soundFile) {
        mSoundFile = soundFile;
        if (soundFile != null) {
            mSampleRate = mSoundFile.getSampleRate();
            mSamplesPerFrame = mSoundFile.getSamplesPerFrame();
            mData = new WaveformData(soundFile.getFrameGains());
            mZoomLevel = mData.getInitialZoomLevel();
            mInitialized = true;
        }
        mHeightsAtThisZoomLevel = null;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public int getZoomLevel() {
        return mZoomLevel;
    }

    public void setZoomLevel(int zoomLevel) {
        while (mZoomLevel > zoomLevel) {
            zoomIn();
        }
        while (mZoomLevel < zoomLevel) {
            zoomOut();
        }
    }

    public boolean canZoomIn() {
        return (mZoomLevel > 0);
    }

    public void zoomIn() {
        if (canZoomIn()) {
            mZoomLevel--;
            mSelectionStart *= 2;
            mSelectionEnd *= 2;
            mHeightsAtThisZoomLevel = null;
            int offsetCenter = mOffset + getMeasuredWidth() / 2;
            offsetCenter *= 2;
            mOffset = offsetCenter - getMeasuredWidth() / 2;
            if (mOffset < 0)
                mOffset = 0;
            invalidate();
        }
    }

    public boolean canZoomOut() {
        return mData != null && mZoomLevel < WaveformData.NUM_ZOOM_LEVELS - 1;
    }

    public void zoomOut() {
        if (canZoomOut()) {
            mZoomLevel++;
            mSelectionStart /= 2;
            mSelectionEnd /= 2;
            int offsetCenter = mOffset + getMeasuredWidth() / 2;
            offsetCenter /= 2;
            mOffset = offsetCenter - getMeasuredWidth() / 2;
            if (mOffset < 0)
                mOffset = 0;
            mHeightsAtThisZoomLevel = null;
            invalidate();
        }
    }

    public int maxPos() {
        return mData != null ? mData.getLength(mZoomLevel) : 0;
    }

    public int secondsToFrames(double seconds) {
        return (int) (1.0 * seconds * mSampleRate / mSamplesPerFrame + 0.5);
    }

    public int secondsToPixels(double seconds) {
        if (mData == null) {
            return 0;
        }
        double z = mData.getZoomFactor(mZoomLevel);
        return (int) (z * seconds * mSampleRate / mSamplesPerFrame + 0.5);
    }

    public double pixelsToSeconds(int pixels) {
        if (mData == null) {
            return 0.0;
        }
        double z = mData.getZoomFactor(mZoomLevel);
        return (pixels * (double) mSamplesPerFrame / (mSampleRate * z));
    }

    public int millisecsToPixels(int msecs) {
        if (mData == null) {
            return 0;
        }
        double z = mData.getZoomFactor(mZoomLevel);
        return (int) ((msecs * 1.0 * mSampleRate * z) / (1000.0 * mSamplesPerFrame) + 0.5);
    }

    public int pixelsToMillisecs(int pixels) {
        if (mData == null) {
            return 0;
        }
        double z = mData.getZoomFactor(mZoomLevel);
        return (int) (pixels * (1000.0 * mSamplesPerFrame) / (mSampleRate * z) + 0.5);
    }

    public void setParameters(int start, int end, int offset) {
        mSelectionStart = start;
        mSelectionEnd = end;
        mOffset = offset;
    }

    public int getStart() {
        return mSelectionStart;
    }

    public int getEnd() {
        return mSelectionEnd;
    }

    public int getOffset() {
        return mOffset;
    }

    public void setPlayback(int pos) {
        mPlaybackPos = pos;
    }

    public void setListener(WaveformListener listener) {
        mListener = listener;
    }

    public void recomputeHeights(float density) {
        mHeightsAtThisZoomLevel = null;
        mDensity = density;
        mTimecodePaint.setTextSize((int) (12 * density));
        mPlaybackLinePaint.setStrokeWidth(2.5f * density);

        invalidate();
    }

    protected void drawWaveformLine(Canvas canvas, int x, int y0, int y1, Paint paint) {
        canvas.drawLine(x, y0, x, y1, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mSoundFile == null)
            return;

        if (mHeightsAtThisZoomLevel == null)
            computeIntsForThisZoomLevel();

        // Draw waveform
        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        int start = mOffset;
        int width = mHeightsAtThisZoomLevel.length - start;
        int ctr = measuredHeight / 2;

        if (width > measuredWidth)
            width = measuredWidth;

        // Draw grid
        double onePixelInSecs = pixelsToSeconds(1);
        boolean onlyEveryFiveSecs = (onePixelInSecs > 1.0 / 50.0);
        double fractionalSecs = mOffset * onePixelInSecs;
        int integerSecs = (int) fractionalSecs;
        int i = 0;
        while (i < width) {
            i++;
            fractionalSecs += onePixelInSecs;
            int integerSecsNew = (int) fractionalSecs;
            if (integerSecsNew != integerSecs) {
                integerSecs = integerSecsNew;
                if (!onlyEveryFiveSecs || 0 == (integerSecs % 5)) {
                    canvas.drawLine(i, 0, i, measuredHeight, mGridPaint);
                }
            }
        }

        // Draw waveform
        for (i = 0; i < width; i++) {
            Paint paint;
            if (i + start >= mSelectionStart && i + start < mSelectionEnd) {
                paint = mSelectedLinePaint;
            } else {
                drawWaveformLine(canvas, i, 0, measuredHeight, mUnselectedBkgndLinePaint);
                paint = mUnselectedLinePaint;
            }
            drawWaveformLine(canvas, i, ctr - mHeightsAtThisZoomLevel[start + i],
                    ctr + 1 + mHeightsAtThisZoomLevel[start + i], paint);

            if (i + start == mPlaybackPos) {
                canvas.drawLine(i, 0, i, measuredHeight, mPlaybackLinePaint);
            }
        }

        // If we can see the right edge of the waveform, draw the
        // non-waveform area to the right as unselected
        for (i = width; i < measuredWidth; i++) {
            drawWaveformLine(canvas, i, 0, measuredHeight, mUnselectedBkgndLinePaint);
        }

        // Draw borders
        canvas.drawLine(mSelectionStart - mOffset + 0.5f, 30, mSelectionStart - mOffset + 0.5f,
                measuredHeight, mBorderLinePaint);
        canvas.drawLine(mSelectionEnd - mOffset + 0.5f, 0, mSelectionEnd - mOffset + 0.5f,
                measuredHeight - 30, mBorderLinePaint);

        // Draw timecode
        float minimumTimecodeSpacing = mTimecodePaint.measureText("0:00") + 16 * mDensity;
        double timecodeIntervalSecs = Math.max(1.0,
                Math.ceil(minimumTimecodeSpacing * onePixelInSecs));

        // Draw grid
        fractionalSecs = mOffset * onePixelInSecs;
        int integerTimecode = (int) (fractionalSecs / timecodeIntervalSecs);
        i = 0;
        while (i < width) {
            i++;
            fractionalSecs += onePixelInSecs;
            integerSecs = (int) fractionalSecs;
            int integerTimecodeNew = (int) (fractionalSecs / timecodeIntervalSecs);
            if (integerTimecodeNew != integerTimecode) {
                integerTimecode = integerTimecodeNew;

                // Turn, e.g. 67 seconds into "1:07"
                String timecodeMinutes = "" + (integerSecs / 60);
                String timecodeSeconds = "" + (integerSecs % 60);
                if ((integerSecs % 60) < 10) {
                    timecodeSeconds = "0" + timecodeSeconds;
                }
                String timecodeStr = timecodeMinutes + ":" + timecodeSeconds;
                float offset = (float) (0.5 * mTimecodePaint.measureText(timecodeStr));
                canvas.drawText(timecodeStr, i - offset, (int) (12 * mDensity), mTimecodePaint);
            }
        }

        if (mListener != null) {
            mListener.waveformDraw();
        }
    }

    /**
     * Called the first time we need to draw when the zoom level has changed or the screen is
     * resized
     */
    private void computeIntsForThisZoomLevel() {
        int halfHeight = (getMeasuredHeight() / 2) - 1;
        double[] values = mData.getValues(mZoomLevel);
        mHeightsAtThisZoomLevel = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            mHeightsAtThisZoomLevel[i] = (int) (values[i] * halfHeight);
        }
    }

    public interface WaveformListener {
        void waveformTouchStart(float x);

        void waveformTouchMove(float x);

        void waveformTouchEnd();

        void waveformFling(float x);

        void waveformDraw();

        void waveformZoomIn();

        void waveformZoomOut();
    }
}
