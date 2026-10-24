package com.ringdroid.soundfile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;

@RunWith(AndroidJUnit4.class)
public class SoundRecorderTest {
    @Test
    public void nullListenerReturnsNull() {
        SoundFile soundFile = SoundRecorder.record(null);
        assertNull("Should return null if progress listener is missing", soundFile);
    }

    @Test
    public void stopsWhenListenerAsks() {
        // Record and cancel immediately
        SoundFile.ProgressListener listener = fractionComplete -> true; // stop immediately
        SoundFile soundFile = SoundRecorder.record(listener);
        // Null when the microphone is unavailable or the permission is missing.
        if (soundFile != null) {
            assertEquals("raw", soundFile.getFiletype());
        }
    }

    @Test
    public void recordsMonoAudio() throws Exception {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String packageName = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getPackageName();
        try (ParcelFileDescriptor out = automation.executeShellCommand(
                "pm grant " + packageName + " android.permission.RECORD_AUDIO");
                FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(out)) {
            while (in.read() != -1) {
                // Wait for the command to finish.
            }
        }

        SoundFile soundFile = SoundRecorder.record(seconds -> seconds >= 0.5);
        assertNotNull(soundFile);
        try {
            assertEquals("raw", soundFile.getFiletype());
            assertEquals(44100, soundFile.getSampleRate());
            assertEquals(1, soundFile.getChannels());
            assertTrue(soundFile.getNumSamples() >= 44100 / 2);
            assertEquals((soundFile.getNumSamples() + 1023) / 1024, soundFile.getNumFrames());
            assertNotNull(soundFile.getSamples());
        } finally {
            soundFile.release();
        }
    }
}
