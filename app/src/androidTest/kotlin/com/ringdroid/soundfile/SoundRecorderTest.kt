package com.ringdroid.soundfile

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ringdroid.testing.TestPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundRecorderTest {
    @Test
    fun nullListenerReturnsNull() {
        assertNull(SoundRecorder.record(null))
    }

    @Test
    fun stopsWhenListenerAsks() {
        // Null when the microphone is unavailable or the permission is missing.
        SoundRecorder.record { true }?.let {
            assertEquals("raw", it.filetype)
            it.release()
        }
    }

    @Test
    fun recordsMonoAudio() {
        TestPermissions.grant(Manifest.permission.RECORD_AUDIO)

        val soundFile = SoundRecorder.record { seconds -> seconds >= 0.5 }
        assertNotNull(soundFile)
        try {
            assertEquals("raw", soundFile!!.filetype)
            assertEquals(44100, soundFile.sampleRate)
            assertEquals(1, soundFile.channels)
            assertTrue(soundFile.numSamples >= 44100 / 2)
            assertEquals((soundFile.numSamples + 1023) / 1024, soundFile.numFrames)
            assertNotNull(soundFile.samples)
        } finally {
            soundFile?.release()
        }
    }
}
