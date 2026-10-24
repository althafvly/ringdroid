package com.ringdroid;

import static org.junit.Assert.assertEquals;
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
import java.lang.reflect.Field;

@RunWith(AndroidJUnit4.class)
public class WaveformViewTest {
    @Test
    public void silentAudioDrawsFlatWaveform() throws Exception {
        double max = maxHeight(new short[TestAudio.SAMPLE_RATE * 2]);
        assertEquals(0.0, max, 0.0);
    }

    @Test
    public void loudAudioDrawsWaveform() throws Exception {
        short[] samples = new short[TestAudio.SAMPLE_RATE * 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((i % 100) * 300 - 15000);
        }
        assertTrue(maxHeight(samples) > 0.5);
    }

    private static double maxHeight(short[] samples) throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        File wav = TestAudio.writeWav(new File(context.getCacheDir(), "waveform_input.wav"), 1,
                samples);
        SoundFile soundFile = TestAudio.load(wav);
        try {
            double[][] values = new double[1][];
            instrumentation.runOnMainSync(() -> {
                WaveformView view = new WaveformView(context, null);
                view.setSoundFile(soundFile);
                values[0] = getNormalZoomValues(view);
            });
            double max = 0;
            for (double value : values[0]) {
                assertTrue(value >= 0.0 && value <= 1.0);
                max = Math.max(max, value);
            }
            return max;
        } finally {
            soundFile.release();
            wav.delete();
        }
    }

    private static double[] getNormalZoomValues(WaveformView view) {
        try {
            Field field = WaveformView.class.getDeclaredField("mValuesByZoomLevel");
            field.setAccessible(true);
            return ((double[][]) field.get(view))[1];
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
