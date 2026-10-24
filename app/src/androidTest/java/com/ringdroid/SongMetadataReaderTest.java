package com.ringdroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ringdroid.soundfile.TestAudio;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public class SongMetadataReaderTest {
    @Test
    public void untaggedFileUsesFileName() throws Exception {
        File wav = new File(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
                "My Song.wav");
        TestAudio.writeWav(wav, 1, new short[TestAudio.SAMPLE_RATE]);
        try {
            SongMetadataReader reader = new SongMetadataReader(wav.getPath());
            assertEquals("My Song", reader.mTitle);
            assertNull(reader.mArtist);
        } finally {
            wav.delete();
        }
    }

    @Test
    public void unreadableFilesFallBackToFileName() {
        assertEquals("Track", new SongMetadataReader("/nonexistent/dir/Track.mp3").mTitle);
        assertEquals("README", new SongMetadataReader("/nonexistent/README").mTitle);
        assertEquals("a.b", new SongMetadataReader("/nonexistent/a.b.mp3").mTitle);
    }
}
