package com.ringdroid;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ShortBuffer;

@RunWith(AndroidJUnit4.class)
public class SamplePlayerTest {
    private static final int SAMPLE_RATE = 44100;

    @Test
    public void playsCommonChannelCounts() {
        for (int channels : new int[]{1, 2, 3, 4, 6, 8}) {
            int numSamples = SAMPLE_RATE / 10;
            ShortBuffer samples = ShortBuffer.allocate(numSamples * channels);
            SamplePlayer player = new SamplePlayer(samples, SAMPLE_RATE, channels, numSamples);
            try {
                player.start();
                assertTrue(channels + " channels", player.isPlaying());
                player.stop();
            } finally {
                player.release();
            }
        }
    }

    @Test
    public void seekBeyondEndIsClamped() {
        int numSamples = SAMPLE_RATE / 10;
        SamplePlayer player = new SamplePlayer(ShortBuffer.allocate(numSamples), SAMPLE_RATE, 1,
                numSamples);
        try {
            player.seekTo(60000);
            assertTrue(player.getCurrentPosition() <= 100 + 1);
        } finally {
            player.release();
        }
    }
}
