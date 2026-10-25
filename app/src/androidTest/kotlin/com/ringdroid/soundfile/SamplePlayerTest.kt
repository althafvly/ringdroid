package com.ringdroid.soundfile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ShortBuffer

@RunWith(AndroidJUnit4::class)
class SamplePlayerTest {
    @Test
    fun playsCommonChannelCounts() {
        for (channels in intArrayOf(1, 2, 3, 4, 6, 8)) {
            val numSamples = SAMPLE_RATE / 10
            val player =
                SamplePlayer(
                    ShortBuffer.allocate(numSamples * channels),
                    SAMPLE_RATE,
                    channels,
                    numSamples,
                )
            try {
                player.start()
                assertTrue("$channels channels", player.isPlaying)
                player.stop()
            } finally {
                player.release()
            }
        }
    }

    @Test
    fun seekBeyondEndIsClamped() {
        val numSamples = SAMPLE_RATE / 10
        val player = SamplePlayer(ShortBuffer.allocate(numSamples), SAMPLE_RATE, 1, numSamples)
        try {
            player.seekTo(60_000)
            assertTrue(player.currentPosition <= 101)
        } finally {
            player.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44100
    }
}
