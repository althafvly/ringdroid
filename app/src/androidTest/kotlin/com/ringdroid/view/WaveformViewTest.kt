package com.ringdroid.view

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ringdroid.soundfile.TestAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WaveformViewTest {
    @Test
    fun soundFileSetsZoomAndLength() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // 2 s of mono audio = 87 frames of 1024 samples, shown at the most zoomed-in level.
        val wav =
            TestAudio.writeWav(
                File(context.cacheDir, "waveform_input.wav"),
                1,
                TestAudio.ramp(TestAudio.SAMPLE_RATE * 2),
            )
        val soundFile = TestAudio.load(wav)
        try {
            instrumentation.runOnMainSync {
                val view = WaveformView(context, null)
                assertFalse(view.isInitialized)
                assertEquals(0, view.maxPos)

                view.setSoundFile(soundFile)
                assertTrue(view.isInitialized)
                assertEquals(0, view.zoomLevel)
                assertEquals(174, view.maxPos)
                assertFalse(view.canZoomIn)

                view.zoomOut()
                assertEquals(87, view.maxPos)
                assertEquals(1000.0, view.scale.pixelsToMillisecs(view.scale.millisecsToPixels(1000)).toDouble(), 30.0)

                // Restoring a zoom level after rotation.
                view.zoomLevel = 0
                assertEquals(174, view.maxPos)
            }
        } finally {
            soundFile.release()
            wav.delete()
        }
    }
}
