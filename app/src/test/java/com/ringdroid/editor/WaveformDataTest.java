package com.ringdroid.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.util.Arrays;

public class WaveformDataTest {
    private static int[] gains(int numFrames, int value) {
        int[] gains = new int[numFrames];
        Arrays.fill(gains, value);
        return gains;
    }

    private static double max(double[] values) {
        double max = 0;
        for (double value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    @Test
    public void silenceIsFlat() {
        WaveformData data = new WaveformData(gains(400, 0));
        for (int level = 0; level < WaveformData.NUM_ZOOM_LEVELS; level++) {
            assertEquals(0.0, max(data.getValues(level)), 0.0);
        }
    }

    @Test
    public void constantLevelIsFullHeight() {
        WaveformData data = new WaveformData(gains(400, 120));
        double[] values = data.getValues(1);
        for (int i = 1; i < values.length - 1; i++) {
            assertEquals(1.0, values[i], 0.0);
        }
    }

    @Test
    public void varyingLevelsStayInRange() {
        int[] gains = new int[1000];
        for (int i = 0; i < gains.length; i++) {
            gains[i] = (i * 37) % 180;
        }
        WaveformData data = new WaveformData(gains);
        for (int level = 0; level < WaveformData.NUM_ZOOM_LEVELS; level++) {
            for (double value : data.getValues(level)) {
                assertTrue(value >= 0.0 && value <= 1.0);
            }
        }
        assertTrue(max(data.getValues(1)) > 0.5);
    }

    @Test
    public void zoomLevelsHalveTheLength() {
        WaveformData data = new WaveformData(gains(1000, 50));
        assertEquals(2000, data.getLength(0));
        assertEquals(1000, data.getLength(1));
        assertEquals(500, data.getLength(2));
        assertEquals(250, data.getLength(3));
        assertEquals(125, data.getLength(4));
        assertEquals(2.0, data.getZoomFactor(0), 0.0);
        assertEquals(1.0, data.getZoomFactor(1), 0.0);
        assertEquals(0.125, data.getZoomFactor(4), 0.0);
        for (int level = 0; level < WaveformData.NUM_ZOOM_LEVELS; level++) {
            assertEquals(data.getLength(level), data.getValues(level).length);
        }
    }

    @Test
    public void longerFilesStartMoreZoomedOut() {
        assertEquals(0, new WaveformData(gains(300, 50)).getInitialZoomLevel());
        assertEquals(1, new WaveformData(gains(301, 50)).getInitialZoomLevel());
        assertEquals(2, new WaveformData(gains(1001, 50)).getInitialZoomLevel());
        assertEquals(3, new WaveformData(gains(5001, 50)).getInitialZoomLevel());
    }

    @Test
    public void emptyAudioHasNoFrames() {
        WaveformData data = new WaveformData(new int[0]);
        for (int level = 0; level < WaveformData.NUM_ZOOM_LEVELS; level++) {
            assertEquals(0, data.getLength(level));
        }
    }
}
