package com.ringdroid.soundfile

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Helpers for generating and reading 16-bit PCM WAV files in tests. */
object TestAudio {
    const val SAMPLE_RATE = 44100

    fun writeWav(file: File, channels: Int, interleaved: ShortArray): File {
        val data = ByteBuffer.allocate(interleaved.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.asShortBuffer().put(interleaved)
        FileOutputStream(file).use { out ->
            out.write(WAVHeader.getWAVHeader(SAMPLE_RATE, channels, interleaved.size / channels))
            out.write(data.array())
        }
        return file
    }

    /** Returns the PCM data of a WAV file written by this app (44-byte header). */
    fun readWavData(file: File): ShortArray {
        val bytes = file.readBytes()
        val samples = ShortArray((bytes.size - 44) / 2)
        ByteBuffer
            .wrap(bytes, 44, bytes.size - 44)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(samples)
        return samples
    }

    /** Samples 0, 1, 2, ... so that every position is identifiable. */
    fun ramp(count: Int) = ShortArray(count) { it.toShort() }

    fun load(file: File): SoundFile {
        val soundFile = SoundFile.create(file.parentFile!!, file.path) { false }
        assertNotNull(soundFile)
        return soundFile!!
    }

    /** Copies a bundled asset, by default test_audio.wav (1 s, mono, 44.1 kHz), to [file]. */
    fun copyAsset(file: File, asset: String = "test_audio.wav"): File {
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input ->
            FileOutputStream(file).use { input.copyTo(it) }
        }
        return file
    }
}
