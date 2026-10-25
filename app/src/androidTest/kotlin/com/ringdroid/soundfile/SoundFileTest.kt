package com.ringdroid.soundfile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SoundFileTest {
    private val outDir: File = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    private lateinit var testWavFile: File
    private lateinit var corruptFile: File

    @Before
    fun setUp() {
        testWavFile = TestAudio.copyAsset(File(outDir, "test_audio.wav"))
        corruptFile =
            File(outDir, "corrupt_audio.wav").apply { writeBytes(byteArrayOf(0, 1, 2, 3)) }
    }

    @After
    fun tearDown() {
        testWavFile.delete()
        corruptFile.delete()
    }

    @Test
    fun supportedExtensions() {
        val extensions = SoundFile.getSupportedExtensions().toList()
        assertTrue(extensions.containsAll(listOf("wav", "mp3", "flac", "opus", "wma", "mkv")))
    }

    @Test
    fun filenameSupport() {
        for (name in listOf(
            "audio.wav",
            "music.mp3",
            "track.m4a",
            "track.flac",
            "track.opus",
            "track.wma",
            "track.mkv",
        )) {
            assertTrue(name, SoundFile.isFilenameSupported(name))
        }
        assertFalse(SoundFile.isFilenameSupported("image.jpg"))
        assertFalse(SoundFile.isFilenameSupported("document.txt"))
    }

    @Test(expected = FileNotFoundException::class)
    fun missingFileThrows() {
        SoundFile.create(outDir, "/path/to/nonexistent/file.wav", null)
    }

    @Test
    fun unsupportedExtensionReturnsNull() {
        val txtFile = File(outDir, "test.txt")
        txtFile.createNewFile()
        try {
            assertNull(SoundFile.create(outDir, txtFile.absolutePath, null))
        } finally {
            txtFile.delete()
        }
    }

    @Test(expected = IOException::class)
    fun corruptFileThrows() {
        SoundFile.create(outDir, corruptFile.absolutePath, null)
    }

    @Test
    fun decodesOggVorbis() {
        val ogg = TestAudio.copyAsset(File(outDir, "test_audio.ogg"), "test_audio.ogg")
        val soundFile = SoundFile.create(outDir, ogg.absolutePath) { false }!!
        try {
            assertEquals("ogg", soundFile.filetype)
            assertTrue(soundFile.numSamples > 0)
            assertNotNull(soundFile.frameGains)
        } finally {
            soundFile.release()
            ogg.delete()
        }
    }

    @Test
    fun decodesValidFile() {
        val soundFile = SoundFile.create(outDir, testWavFile.absolutePath) { false }!!
        assertEquals("wav", soundFile.filetype)
        assertEquals(44100, soundFile.sampleRate)
        assertEquals(1, soundFile.channels)
        assertEquals(44100, soundFile.numSamples)
        assertEquals(44, soundFile.numFrames) // ceil(44100 / 1024)
        assertEquals(1024, soundFile.samplesPerFrame)
        assertNotNull(soundFile.frameGains)
        assertTrue(soundFile.samples!!.remaining() >= 44100)
        soundFile.release()
        assertNull(soundFile.samples)
    }

    @Test
    fun cancelingReturnsNull() {
        assertNull(SoundFile.create(outDir, testWavFile.absolutePath) { true })
    }

    @Test
    fun invalidInputExceptionKeepsMessage() {
        assertEquals("Test message", SoundFile.InvalidInputException("Test message").message)
    }

    @Test
    fun decodesAnElevenMinuteFile() {
        val sampleRate = 8000
        val numSamples = 660 * sampleRate
        val longWav = File(outDir, "long_audio.wav")
        FileOutputStream(longWav).use { out ->
            out.write(WAVHeader.getWAVHeader(sampleRate, 1, numSamples))
            val oneSecond = ByteArray(sampleRate * 2)
            repeat(660) { out.write(oneSecond) }
        }
        try {
            val soundFile = SoundFile.create(outDir, longWav.absolutePath, null)!!
            assertEquals(1, soundFile.channels)
            assertEquals(numSamples, soundFile.numSamples)
            soundFile.release()
        } finally {
            longWav.delete()
        }
    }

    @Test
    fun staleTempFilesAreDeleted() {
        val stale = File(outDir, "ringdroid_pcm_stale.raw")
        assertTrue(stale.createNewFile())
        // Files of SoundFiles still open in this process must survive.
        val before = pcmFileNames() - stale.name

        val soundFile = SoundFile.create(outDir, testWavFile.absolutePath) { false }!!
        assertFalse(stale.exists())
        val created = pcmFileNames()
        assertTrue(created.containsAll(before))
        val own = created - before
        assertEquals(1, own.size)

        soundFile.release()
        assertFalse(File(outDir, own.first()).exists())
    }

    private fun pcmFileNames(): Set<String> =
        outDir.list { _, name -> name.startsWith("ringdroid_pcm_") }.orEmpty().toSet()
}
