package com.ringdroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformDataTest {
    private fun gains(
        numFrames: Int,
        value: Int,
    ) = IntArray(numFrames) { value }

    @Test
    fun silenceIsFlat() {
        val data = WaveformData(gains(400, 0))
        for (level in 0 until WaveformData.NUM_ZOOM_LEVELS) {
            assertEquals(0.0, data.getValues(level).maxOrNull() ?: 0.0, 0.0)
        }
    }

    @Test
    fun constantLevelIsFullHeight() {
        val values = WaveformData(gains(400, 120)).getValues(1)
        for (i in 1 until values.size - 1) {
            assertEquals(1.0, values[i], 0.0)
        }
    }

    @Test
    fun varyingLevelsStayInRange() {
        val data = WaveformData(IntArray(1000) { (it * 37) % 180 })
        for (level in 0 until WaveformData.NUM_ZOOM_LEVELS) {
            assertTrue(data.getValues(level).all { it in 0.0..1.0 })
        }
        assertTrue(data.getValues(1).max() > 0.5)
    }

    @Test
    fun zoomLevelsHalveTheLength() {
        val data = WaveformData(gains(1000, 50))
        assertEquals(
            listOf(2000, 1000, 500, 250, 125),
            (0 until WaveformData.NUM_ZOOM_LEVELS).map { data.getLength(it) },
        )
        assertEquals(2.0, data.getZoomFactor(0), 0.0)
        assertEquals(1.0, data.getZoomFactor(1), 0.0)
        assertEquals(0.125, data.getZoomFactor(4), 0.0)
        for (level in 0 until WaveformData.NUM_ZOOM_LEVELS) {
            assertEquals(data.getLength(level), data.getValues(level).size)
        }
    }

    @Test
    fun longerFilesStartMoreZoomedOut() {
        assertEquals(0, WaveformData(gains(300, 50)).initialZoomLevel)
        assertEquals(1, WaveformData(gains(301, 50)).initialZoomLevel)
        assertEquals(2, WaveformData(gains(1001, 50)).initialZoomLevel)
        assertEquals(3, WaveformData(gains(5001, 50)).initialZoomLevel)
    }

    @Test
    fun emptyAudioHasNoFrames() {
        val data = WaveformData(IntArray(0))
        for (level in 0 until WaveformData.NUM_ZOOM_LEVELS) {
            assertEquals(0, data.getLength(level))
        }
    }
}
