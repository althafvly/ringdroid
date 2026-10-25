package com.ringdroid.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformScaleTest {
    private val scale = WaveformScale(44100, 1024, 1.0)

    @Test
    fun secondsAndPixelsRoundTrip() {
        assertEquals(431, scale.secondsToPixels(10.0))
        assertEquals(10.0, scale.pixelsToSeconds(431), 0.01)
        assertEquals(431, scale.secondsToFrames(10.0))
    }

    @Test
    fun millisecondsAndPixelsRoundTrip() {
        assertEquals(43, scale.millisecsToPixels(1000))
        assertEquals(998, scale.pixelsToMillisecs(43))
    }

    @Test
    fun zoomScalesPixelsButNotFrames() {
        val zoomedIn = WaveformScale(44100, 1024, 2.0)
        assertEquals(861, zoomedIn.secondsToPixels(10.0))
        assertEquals(431, zoomedIn.secondsToFrames(10.0))
    }

    @Test
    fun noSoundConvertsToZero() {
        val empty = WaveformScale(0, 0, 0.0)
        assertEquals(0, empty.secondsToPixels(10.0))
        assertEquals(0.0, empty.pixelsToSeconds(100), 0.0)
        assertEquals(0, empty.millisecsToPixels(1000))
        assertEquals(0, empty.pixelsToMillisecs(100))
        assertEquals(0, empty.secondsToFrames(10.0))
    }
}
