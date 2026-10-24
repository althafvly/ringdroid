package com.ringdroid.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ringdroid.soundfile.SoundFile;
import com.ringdroid.soundfile.TestAudio;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public class WaveformViewTest {
    @Test
    public void soundFileSetsZoomAndLength() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        // 2 s of mono audio = 87 frames of 1024 samples, shown at the most zoomed-in level.
        File wav = TestAudio.writeWav(new File(context.getCacheDir(), "waveform_input.wav"), 1,
                TestAudio.ramp(TestAudio.SAMPLE_RATE * 2));
        SoundFile soundFile = TestAudio.load(wav);
        try {
            instrumentation.runOnMainSync(() -> {
                WaveformView view = new WaveformView(context, null);
                assertFalse(view.isInitialized());
                assertEquals(0, view.maxPos());

                view.setSoundFile(soundFile);
                assertTrue(view.isInitialized());
                assertEquals(0, view.getZoomLevel());
                assertEquals(174, view.maxPos());
                assertFalse(view.canZoomIn());

                view.zoomOut();
                assertEquals(87, view.maxPos());
                assertEquals(1000, view.pixelsToMillisecs(view.millisecsToPixels(1000)), 30);
            });
        } finally {
            soundFile.release();
            wav.delete();
        }
    }
}
