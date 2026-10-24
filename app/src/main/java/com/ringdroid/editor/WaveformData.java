package com.ringdroid.editor;

/** Normalized waveform heights (0..1) for each zoom level, computed from per-frame gains. */
final class WaveformData {
    static final int NUM_ZOOM_LEVELS = 5;
    private final int[] mLenByZoomLevel;
    private final double[][] mValuesByZoomLevel;
    private final double[] mZoomFactorByZoomLevel;
    private final int mInitialZoomLevel;

    WaveformData(int[] frameGains) {

        int numFrames = frameGains.length;
        double[] smoothedGains = new double[numFrames];
        if (numFrames == 1) {
            smoothedGains[0] = frameGains[0];
        } else if (numFrames == 2) {
            smoothedGains[0] = frameGains[0];
            smoothedGains[1] = frameGains[1];
        } else if (numFrames > 2) {
            smoothedGains[0] = (frameGains[0] / 2.0) + (frameGains[1] / 2.0);
            for (int i = 1; i < numFrames - 1; i++) {
                smoothedGains[i] = (frameGains[i - 1] / 3.0) + (frameGains[i] / 3.0)
                        + (frameGains[i + 1] / 3.0);
            }
            smoothedGains[numFrames - 1] = (frameGains[numFrames - 2] / 2.0)
                    + (frameGains[numFrames - 1] / 2.0);
        }

        // Make sure the range is no more than 0 - 255
        double maxGain = 1.0;
        for (int i = 0; i < numFrames; i++) {
            if (smoothedGains[i] > maxGain) {
                maxGain = smoothedGains[i];
            }
        }
        double scaleFactor = 1.0;
        if (maxGain > 255.0) {
            scaleFactor = 255 / maxGain;
        }

        // Build histogram of 256 bins and figure out the new scaled max
        maxGain = 0;
        int[] gainHist = new int[256];
        for (int i = 0; i < numFrames; i++) {
            int smoothedGain = (int) (smoothedGains[i] * scaleFactor);
            if (smoothedGain < 0)
                smoothedGain = 0;
            if (smoothedGain > 255)
                smoothedGain = 255;

            if (smoothedGain > maxGain)
                maxGain = smoothedGain;

            gainHist[smoothedGain]++;
        }

        // Re-calibrate the min to be 5%
        double minGain = 0;
        int sum = 0;
        while (minGain < 255 && sum < numFrames / 20) {
            sum += gainHist[(int) minGain];
            minGain++;
        }

        // Re-calibrate the max to be 99%
        sum = 0;
        while (maxGain > 2 && sum < numFrames / 100) {
            sum += gainHist[(int) maxGain];
            maxGain--;
        }

        // Compute the heights
        double[] heights = new double[numFrames];
        double range = maxGain - minGain;
        for (int i = 0; i < numFrames; i++) {
            double scaledGain = smoothedGains[i] * scaleFactor;
            // With no range (constant level) draw sound at full height and silence flat.
            double value = range > 0 ? (scaledGain - minGain) / range : (scaledGain > 0 ? 1 : 0);
            if (value < 0.0)
                value = 0.0;
            if (value > 1.0)
                value = 1.0;
            heights[i] = value * value;
        }

        mLenByZoomLevel = new int[NUM_ZOOM_LEVELS];
        mZoomFactorByZoomLevel = new double[NUM_ZOOM_LEVELS];
        mValuesByZoomLevel = new double[NUM_ZOOM_LEVELS][];

        // Level 0 is doubled, with interpolated values
        mLenByZoomLevel[0] = numFrames * 2;
        mZoomFactorByZoomLevel[0] = 2.0;
        mValuesByZoomLevel[0] = new double[mLenByZoomLevel[0]];
        if (numFrames > 0) {
            mValuesByZoomLevel[0][0] = 0.5 * heights[0];
            mValuesByZoomLevel[0][1] = heights[0];
        }
        for (int i = 1; i < numFrames; i++) {
            mValuesByZoomLevel[0][2 * i] = 0.5 * (heights[i - 1] + heights[i]);
            mValuesByZoomLevel[0][2 * i + 1] = heights[i];
        }

        // Level 1 is normal
        mLenByZoomLevel[1] = numFrames;
        mValuesByZoomLevel[1] = new double[mLenByZoomLevel[1]];
        mZoomFactorByZoomLevel[1] = 1.0;
        System.arraycopy(heights, 0, mValuesByZoomLevel[1], 0, mLenByZoomLevel[1]);

        // 3 more levels are each halved
        for (int j = 2; j < NUM_ZOOM_LEVELS; j++) {
            mLenByZoomLevel[j] = mLenByZoomLevel[j - 1] / 2;
            mValuesByZoomLevel[j] = new double[mLenByZoomLevel[j]];
            mZoomFactorByZoomLevel[j] = mZoomFactorByZoomLevel[j - 1] / 2.0;
            for (int i = 0; i < mLenByZoomLevel[j]; i++) {
                mValuesByZoomLevel[j][i] = 0.5
                        * (mValuesByZoomLevel[j - 1][2 * i] + mValuesByZoomLevel[j - 1][2 * i + 1]);
            }
        }

        if (numFrames > 5000) {
            mInitialZoomLevel = 3;
        } else if (numFrames > 1000) {
            mInitialZoomLevel = 2;
        } else if (numFrames > 300) {
            mInitialZoomLevel = 1;
        } else {
            mInitialZoomLevel = 0;
        }
    }

    int getLength(int zoomLevel) {
        return mLenByZoomLevel[zoomLevel];
    }

    double[] getValues(int zoomLevel) {
        return mValuesByZoomLevel[zoomLevel];
    }

    double getZoomFactor(int zoomLevel) {
        return mZoomFactorByZoomLevel[zoomLevel];
    }

    int getInitialZoomLevel() {
        return mInitialZoomLevel;
    }
}
