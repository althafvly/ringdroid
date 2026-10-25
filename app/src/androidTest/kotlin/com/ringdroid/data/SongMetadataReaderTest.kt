package com.ringdroid.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ringdroid.soundfile.TestAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SongMetadataReaderTest {
    @Test
    fun untaggedFileUsesFileName() {
        val wav = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "My Song.wav")
        TestAudio.writeWav(wav, 1, ShortArray(TestAudio.SAMPLE_RATE))
        try {
            val reader = SongMetadataReader(wav.path)
            assertEquals("My Song", reader.title)
            assertNull(reader.artist)
        } finally {
            wav.delete()
        }
    }

    @Test
    fun unreadableFilesFallBackToFileName() {
        assertEquals("Track", SongMetadataReader("/nonexistent/dir/Track.mp3").title)
        assertEquals("README", SongMetadataReader("/nonexistent/README").title)
        assertEquals("a.b", SongMetadataReader("/nonexistent/a.b.mp3").title)
        assertEquals(".hidden", SongMetadataReader("/nonexistent/.hidden").title)
    }
}
