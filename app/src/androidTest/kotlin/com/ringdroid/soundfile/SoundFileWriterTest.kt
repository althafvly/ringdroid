package com.ringdroid.soundfile

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ringdroid.soundfile.TestAudio.SAMPLE_RATE
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SoundFileWriterTest {
    private val dir: File = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    private val files = mutableListOf<File>()
    private val soundFiles = mutableListOf<SoundFile>()

    @After
    fun tearDown() {
        soundFiles.forEach { it.release() }
        files.forEach { it.delete() }
    }

    private fun file(name: String) = File(dir, name).also { files.add(it) }

    private fun load(channels: Int, samples: ShortArray): SoundFile {
        val wav = TestAudio.writeWav(file("writer_input_$channels.wav"), channels, samples)
        return TestAudio.load(wav).also { soundFiles.add(it) }
    }

    @Test
    fun wavStereoKeepsHeaderAndChannelOrder() {
        val soundFile = load(2, TestAudio.ramp(SAMPLE_RATE * 2))
        val out = file("writer_stereo.wav")
        SoundFileWriter(soundFile, 1.0f).writeWAV(out, 0, 10)

        val bytes = out.readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val dataSize = 10 * FRAME * 2 * 2
        assertEquals(44 + dataSize, bytes.size)
        assertEquals(2, header.getShort(22).toInt())
        assertEquals(SAMPLE_RATE, header.getInt(24))
        assertEquals(dataSize, header.getInt(40))
        assertArrayEquals(TestAudio.ramp(10 * FRAME * 2), TestAudio.readWavData(out))
    }

    @Test
    fun wavRangeStartsAtStartFrame() {
        val soundFile = load(1, TestAudio.ramp(SAMPLE_RATE))
        val out = file("writer_range.wav")
        SoundFileWriter(soundFile, 1.0f).writeWAV(out, 3, 2)

        val data = TestAudio.readWavData(out)
        assertEquals(2 * FRAME, data.size)
        assertEquals(3 * FRAME, data[0].toInt())
        assertEquals(5 * FRAME - 1, data.last().toInt())
    }

    @Test
    fun wavGainScalesAndClips() {
        val pattern = shortArrayOf(1000, -1000, 20000, -20000)
        val soundFile = load(1, ShortArray(FRAME) { pattern[it % pattern.size] })
        val out = file("writer_gain.wav")
        SoundFileWriter(soundFile, 2.0f).writeWAV(out, 0, 1)

        assertArrayEquals(
            shortArrayOf(2000, -2000, Short.MAX_VALUE, Short.MIN_VALUE),
            TestAudio.readWavData(out).copyOf(4),
        )
    }

    @Test
    fun wavPadsPastEndWithSilence() {
        val soundFile = load(1, TestAudio.ramp(1000))
        val out = file("writer_pad.wav")
        SoundFileWriter(soundFile, 1.0f).writeWAV(out, 0, 2)

        val data = TestAudio.readWavData(out)
        assertEquals(2 * FRAME, data.size)
        assertArrayEquals(TestAudio.ramp(1000), data.copyOf(1000))
        assertTrue(data.drop(1000).all { it.toInt() == 0 })
    }

    @Test
    fun m4aRoundTripKeepsFormat() {
        for (channels in 1..2) {
            val soundFile = load(channels, ShortArray(SAMPLE_RATE * channels))
            val out = file("writer_roundtrip_$channels.m4a")
            SoundFileWriter(soundFile, 1.0f).writeM4A(out, 0, 40)

            val decoded = TestAudio.load(out).also { soundFiles.add(it) }
            assertEquals(channels, decoded.channels)
            assertEquals(SAMPLE_RATE, decoded.sampleRate)
            assertTrue(
                "samples ${decoded.numSamples}",
                decoded.numSamples in 40 * FRAME..44 * FRAME,
            )
        }
    }

    @Test
    fun m4aLongerThanAMinuteHasCorrectDuration() {
        val soundFile = load(2, ShortArray(SAMPLE_RATE * 2))
        val out = file("writer_long.m4a")
        val numFrames = 2600 // ~60 s, past the old header's 48.7 s overflow
        SoundFileWriter(soundFile, 1.0f).writeM4A(out, 0, numFrames)

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(out.path)
            assertEquals(1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, format.getString(MediaFormat.KEY_MIME))
            val expectedUs = numFrames * FRAME.toLong() * 1_000_000L / SAMPLE_RATE
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            assertTrue("duration $durationUs", abs(durationUs - expectedUs) < 200_000)
        } finally {
            extractor.release()
        }
    }

    @Test(expected = IOException::class)
    fun emptyRangeThrows() {
        SoundFileWriter(
            load(1, TestAudio.ramp(SAMPLE_RATE)),
            1.0f,
        ).writeM4A(file("writer_empty.m4a"), 0, 0)
    }

    @Test(expected = IOException::class)
    fun startPastEndThrows() {
        SoundFileWriter(
            load(1, TestAudio.ramp(SAMPLE_RATE)),
            1.0f,
        ).writeWAV(file("writer_past_end.wav"), 1000, 100)
    }

    @Test(expected = IllegalStateException::class)
    fun releasedSoundFileThrows() {
        val soundFile = load(1, TestAudio.ramp(SAMPLE_RATE))
        soundFile.release()
        SoundFileWriter(soundFile, 1.0f)
    }

    private companion object {
        const val FRAME = 1024
    }
}
