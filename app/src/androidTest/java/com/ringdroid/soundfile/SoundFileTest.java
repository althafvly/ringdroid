package com.ringdroid.soundfile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class SoundFileTest {

    private Context context;
    private File testWavFile;
    private File corruptFile;
    private File outDir;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();

        outDir = context.getCacheDir();
        testWavFile = new File(outDir, "test_audio.wav");
        corruptFile = new File(outDir, "corrupt_audio.wav");

        // Setup valid dummy WAV
        try (InputStream in = testContext.getAssets().open("test_audio.wav");
                FileOutputStream out = new FileOutputStream(testWavFile)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0)
                out.write(buf, 0, len);
        }

        // Setup corrupt file
        try (FileOutputStream out = new FileOutputStream(corruptFile)) {
            out.write(new byte[]{0, 1, 2, 3});
        }
    }

    @After
    public void tearDown() {
        if (testWavFile.exists())
            testWavFile.delete();
        if (corruptFile.exists())
            corruptFile.delete();
    }

    // --- 1. Static and Utility Methods ---

    @Test
    public void testGetSupportedExtensions() {
        String[] extensions = SoundFile.getSupportedExtensions();
        assertNotNull(extensions);
        assertTrue(extensions.length > 0);
        assertTrue(Arrays.asList(extensions).contains("wav"));
        assertTrue(Arrays.asList(extensions).contains("mp3"));
        assertTrue(Arrays.asList(extensions).contains("flac"));
        assertTrue(Arrays.asList(extensions).contains("opus"));
        assertTrue(Arrays.asList(extensions).contains("wma"));
        assertTrue(Arrays.asList(extensions).contains("mkv"));
    }

    @Test
    public void testIsFilenameSupported() {
        assertTrue(SoundFile.isFilenameSupported("audio.wav"));
        assertTrue(SoundFile.isFilenameSupported("music.mp3"));
        assertTrue(SoundFile.isFilenameSupported("track.m4a"));
        assertTrue(SoundFile.isFilenameSupported("track.flac"));
        assertTrue(SoundFile.isFilenameSupported("track.opus"));
        assertTrue(SoundFile.isFilenameSupported("track.wma"));
        assertTrue(SoundFile.isFilenameSupported("track.mkv"));
        assertFalse(SoundFile.isFilenameSupported("image.jpg"));
        assertFalse(SoundFile.isFilenameSupported("document.txt"));
    }

    // --- 2. File Creation / Reading ---

    @Test(expected = FileNotFoundException.class)
    public void testCreateFileNotFound() throws Exception {
        SoundFile.create(outDir, "/path/to/nonexistent/file.wav", null);
    }

    @Test
    public void testCreateUnsupportedExtension() throws Exception {
        File txtFile = new File(outDir, "test.txt");
        txtFile.createNewFile();
        assertNull(SoundFile.create(outDir, txtFile.getAbsolutePath(), null));
        txtFile.delete();
    }

    @Test(expected = Exception.class)
    public void testCreateCorruptFile() throws Exception {
        SoundFile.create(outDir, corruptFile.getAbsolutePath(), null);
    }

    @Test
    public void testCreateValidFileAndGetters() throws Exception {
        SoundFile.ProgressListener listener = fractionComplete -> false;
        SoundFile soundFile = SoundFile.create(outDir, testWavFile.getAbsolutePath(), listener);

        assertNotNull(soundFile);
        assertEquals("wav", soundFile.getFiletype());
        assertEquals(44100, soundFile.getSampleRate());
        assertEquals(1, soundFile.getChannels());
        assertEquals(44100, soundFile.getNumSamples());
        assertEquals(44, soundFile.getNumFrames()); // ceil(44100 / 1024)
        assertEquals(1024, soundFile.getSamplesPerFrame());

        assertNotNull(soundFile.getFrameGains());
        ShortBuffer samples = soundFile.getSamples();
        assertNotNull(samples);
        assertTrue(samples.remaining() >= 44100);

        soundFile.release();
        assertNull(soundFile.getSamples());
    }

    @Test
    public void testCreateWithProgressListenerCancellation() throws Exception {
        // Cancel immediately
        SoundFile.ProgressListener listener = fractionComplete -> true;
        SoundFile soundFile = SoundFile.create(outDir, testWavFile.getAbsolutePath(), listener);
        assertNull(soundFile);
    }

    @Test
    public void testInvalidInputException() {
        SoundFile.InvalidInputException e = new SoundFile.InvalidInputException("Test message");
        assertEquals("Test message", e.getMessage());
    }

    @Test
    public void testLongAudioFileParsing() throws Exception {
        // This test generates a real, valid 11-minute WAV file
        // and successfully parses it, proving the old 10-minute limit
        // has been successfully eliminated.
        File longWav = new File(context.getCacheDir(), "long_audio.wav");
        try (FileOutputStream out = new FileOutputStream(longWav)) {
            int sampleRate = 8000;
            short channels = 1;
            short bitsPerSample = 16;
            // 11 minutes
            int numSamples = 660 * sampleRate;
            int byteRate = sampleRate * channels * (bitsPerSample / 8);
            short blockAlign = (short) (channels * (bitsPerSample / 8));
            int dataSize = numSamples * blockAlign;
            int chunkSize = 36 + dataSize;

            out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize)
                    .array());
            out.write("WAVE".getBytes(StandardCharsets.US_ASCII));
            out.write("fmt ".getBytes(StandardCharsets.US_ASCII));
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(16).array());
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) 1)
                    .array());
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(channels)
                    .array());
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate)
                    .array());
            out.write(
                    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate).array());
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(blockAlign)
                    .array());
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(bitsPerSample)
                    .array());
            out.write("data".getBytes(StandardCharsets.US_ASCII));
            out.write(
                    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize).array());

            // Write 10.5 MB of actual silent PCM data so the file is completely valid
            byte[] silenceBuffer = new byte[8000 * 2]; // 1 second of silence
            for (int i = 0; i < 660; i++) {
                out.write(silenceBuffer);
            }
        }

        try {
            // Should easily fit in RAM of the emulator and parse in a second.
            SoundFile soundFile = SoundFile.create(outDir, longWav.getAbsolutePath(), null);
            assertNotNull(soundFile);
            assertEquals(1, soundFile.getChannels());
            assertTrue(soundFile.getNumFrames() > 0);
        } finally {
            longWav.delete();
        }
    }

    // --- 3. Audio Recording ---

    // --- 4. File Writing Operations ---

    @Test
    public void testStaleTempFilesAreDeleted() throws Exception {
        File stale = new File(outDir, "ringdroid_pcm_stale.raw");
        assertTrue(stale.createNewFile());
        // Files of SoundFiles still open in this process must survive.
        Set<String> before = pcmFileNames();
        before.remove(stale.getName());

        SoundFile soundFile = SoundFile.create(outDir, testWavFile.getAbsolutePath(),
                fractionComplete -> false);
        assertNotNull(soundFile);
        assertFalse(stale.exists());
        Set<String> created = pcmFileNames();
        assertTrue(created.containsAll(before));
        created.removeAll(before);
        assertEquals(1, created.size());

        soundFile.release();
        assertFalse(new File(outDir, created.iterator().next()).exists());
    }

    private Set<String> pcmFileNames() {
        Set<String> names = new HashSet<>();
        String[] files = outDir.list((dir, name) -> name.startsWith("ringdroid_pcm_"));
        if (files != null) {
            names.addAll(Arrays.asList(files));
        }
        return names;
    }
}
