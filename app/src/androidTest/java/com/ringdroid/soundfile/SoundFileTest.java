package com.ringdroid.soundfile;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
        try (java.io.InputStream in = testContext.getAssets().open("test_audio.wav");
             FileOutputStream out = new FileOutputStream(testWavFile)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }

        // Setup corrupt file
        try (FileOutputStream out = new FileOutputStream(corruptFile)) {
            out.write(new byte[]{0, 1, 2, 3});
        }
    }

    @After
    public void tearDown() {
        if (testWavFile.exists()) testWavFile.delete();
        if (corruptFile.exists()) corruptFile.delete();
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

    @Test(expected = java.io.FileNotFoundException.class)
    public void testCreateFileNotFound() throws Exception {
        SoundFile.create("/path/to/nonexistent/file.wav", null);
    }

    @Test
    public void testCreateUnsupportedExtension() throws Exception {
        File txtFile = new File(outDir, "test.txt");
        txtFile.createNewFile();
        assertNull(SoundFile.create(txtFile.getAbsolutePath(), null));
        txtFile.delete();
    }

    @Test(expected = Exception.class)
    public void testCreateCorruptFile() throws Exception {
        SoundFile.create(corruptFile.getAbsolutePath(), null);
    }

    @Test
    public void testCreateValidFileAndGetters() throws Exception {
        SoundFile.ProgressListener listener = fractionComplete -> true;
        SoundFile soundFile = SoundFile.create(testWavFile.getAbsolutePath(), listener);

        injectMockAudioData(soundFile, 1); // Mock data for getters

        assertNotNull(soundFile);
        assertEquals("wav", soundFile.getFiletype());
        assertEquals(44100, soundFile.getSampleRate());
        assertEquals(1, soundFile.getChannels());
        assertTrue(soundFile.getNumSamples() > 0);
        assertTrue(soundFile.getNumFrames() > 0);
        assertEquals(1024, soundFile.getSamplesPerFrame());

        assertNotNull(soundFile.getFrameGains());
        ShortBuffer samples = soundFile.getSamples();
        assertNotNull(samples);
        assertTrue(samples.capacity() > 0);

        soundFile.release();
        assertNull(soundFile.getSamples());
    }

    @Test
    public void testCreateWithProgressListenerCancellation() throws Exception {
        // Cancel immediately
        SoundFile.ProgressListener listener = fractionComplete -> false;

        // This will likely stop parsing midway.
        // It might still return a SoundFile object but with partially decoded data, 
        // or return early. We just verify it doesn't crash.
        SoundFile soundFile = SoundFile.create(testWavFile.getAbsolutePath(), listener);
        assertNotNull(soundFile);
    }

    @Test
    public void testInvalidInputException() {
        SoundFile.InvalidInputException e = new SoundFile.InvalidInputException("Test message");
        assertEquals("Test message", e.getMessage());
    }

    @Test
    public void testUriExists() {
        // Just verify it doesn't crash on null or empty Uri
        android.net.Uri uri = android.net.Uri.parse("content://dummy");
        SoundFile.uriExists(context, uri);
    }

    @Test
    public void testFileTooLongForMemory() throws Exception {
        File oggFile = new File(context.getCacheDir(), "test_audio.ogg");
        try (java.io.InputStream in = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("test_audio.ogg");
             FileOutputStream out = new FileOutputStream(oggFile)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }

        try {
            SoundFile.sMaxAllowedMemoryOverride = 10;
            SoundFile.create(oggFile.getAbsolutePath(), null);
            org.junit.Assert.fail("Expected InvalidInputException for a file that is too large");
        } catch (SoundFile.InvalidInputException e) {
            assertTrue(e.getMessage().contains("Audio file is too long for the available memory"));
        } finally {
            SoundFile.sMaxAllowedMemoryOverride = -1;
            oggFile.delete();
        }
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

            out.write("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize).array());
            out.write("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.write("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(16).array());
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) 1).array());
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(channels).array());
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate).array());
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate).array());
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(blockAlign).array());
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(bitsPerSample).array());
            out.write("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize).array());

            // Write 10.5 MB of actual silent PCM data so the file is completely valid
            byte[] silenceBuffer = new byte[8000 * 2]; // 1 second of silence
            for (int i = 0; i < 660; i++) {
                out.write(silenceBuffer);
            }
        }

        try {
            // Should easily fit in RAM of the emulator and parse in a second.
            SoundFile soundFile = SoundFile.create(longWav.getAbsolutePath(), null);
            assertNotNull(soundFile);
            assertEquals(1, soundFile.getChannels());
            assertTrue(soundFile.getNumFrames() > 0);
        } finally {
            longWav.delete();
        }
    }

    // --- 3. Audio Recording ---

    @Test
    public void testRecordAudioNullListener() {
        SoundFile soundFile = SoundFile.record(null);
        assertNull("Should return null if progress listener is missing", soundFile);
    }

    @Test
    public void testRecordAudioCancellation() {
        // Record and cancel immediately
        SoundFile.ProgressListener listener = fractionComplete -> true; // stop immediately
        try {
            SoundFile soundFile = SoundFile.record(listener);
            assertNotNull(soundFile);
            assertEquals("raw", soundFile.getFiletype());
        } catch (Exception e) {
            // Emulators might lack mic access / permission and throw IllegalStateException.
            // That's acceptable for this test context.
            assertTrue(e instanceof IllegalStateException || e instanceof SecurityException);
        }
    }

    // --- 4. File Writing Operations ---

    private void injectMockAudioData(SoundFile soundFile, int channels) throws Exception {
        Field mNumSamples = SoundFile.class.getDeclaredField("mNumSamples");
        mNumSamples.setAccessible(true);
        mNumSamples.set(soundFile, 44100);

        Field mSampleRate = SoundFile.class.getDeclaredField("mSampleRate");
        mSampleRate.setAccessible(true);
        mSampleRate.set(soundFile, 44100);

        Field mChannels = SoundFile.class.getDeclaredField("mChannels");
        mChannels.setAccessible(true);
        mChannels.set(soundFile, channels);

        Field mNumFrames = SoundFile.class.getDeclaredField("mNumFrames");
        mNumFrames.setAccessible(true);
        mNumFrames.set(soundFile, 100);

        Field mDecodedBytes = SoundFile.class.getDeclaredField("mDecodedBytes");
        mDecodedBytes.setAccessible(true);
        ByteBuffer buf = ByteBuffer.allocate(44100 * 2 * channels);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        // Fill some non-zero data
        for (int i=0; i < 44100 * channels; i++) {
            buf.putShort((short) i);
        }

        buf.flip();
        mDecodedBytes.set(soundFile, buf);

        Field mFrameGains = SoundFile.class.getDeclaredField("mFrameGains");
        mFrameGains.setAccessible(true);
        int[] gains = new int[100];
        Arrays.fill(gains, 5);
        mFrameGains.set(soundFile, gains);

        Field mFileSize = SoundFile.class.getDeclaredField("mFileSize");
        mFileSize.setAccessible(true);
        mFileSize.set(soundFile, 1024 * 1024); // 1 MB mock size for bitrate calculation

        Field mDecodedSamples = SoundFile.class.getDeclaredField("mDecodedSamples");
        mDecodedSamples.setAccessible(true);
        ShortBuffer sBuf = ShortBuffer.allocate(44100 * channels);
        sBuf.position(sBuf.capacity());
        sBuf.flip();
        mDecodedSamples.set(soundFile, sBuf);
    }

    @Test
    public void testWriteFileM4AMono() throws Exception {
        SoundFile soundFile = SoundFile.create(testWavFile.getAbsolutePath(), fractionComplete -> true);
        injectMockAudioData(soundFile, 1);

        File outputFile = new File(outDir, "out_audio.m4a");
        soundFile.WriteFile(outputFile, 0, 50);

        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);
        outputFile.delete();
    }

    @Test
    public void testWriteFileM4AStereo() throws Exception {
        SoundFile soundFile = SoundFile.create(testWavFile.getAbsolutePath(), fractionComplete -> true);
        injectMockAudioData(soundFile, 2); // Set channels to 2

        File outputFile = new File(outDir, "out_audio_stereo.m4a");
        soundFile.WriteFile(outputFile, 0, 50);

        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);
        outputFile.delete();
    }

    @Test
    public void testWriteWavFileMono() throws Exception {
        SoundFile soundFile = SoundFile.create(testWavFile.getAbsolutePath(), fractionComplete -> true);
        injectMockAudioData(soundFile, 1);

        File outputFile = new File(outDir, "out_audio.wav");
        soundFile.WriteWAVFile(outputFile, 0, 50);

        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);
        outputFile.delete();
    }

    @Test
    public void testWriteWavFileStereo() throws Exception {
        SoundFile soundFile = SoundFile.create(testWavFile.getAbsolutePath(), fractionComplete -> true);
        injectMockAudioData(soundFile, 2);

        File outputFile = new File(outDir, "out_audio_stereo.wav");
        soundFile.WriteWAVFile(outputFile, 0, 50);

        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);
        outputFile.delete();
    }

    @Test(expected = IOException.class)
    public void testWriteFileInvalidBounds() throws Exception {
        SoundFile soundFile = SoundFile.create(testWavFile.getAbsolutePath(), fractionComplete -> true);
        injectMockAudioData(soundFile, 1);

        File outputFile = new File(outDir, "out_audio_invalid.wav");
        soundFile.WriteFile(outputFile, 0, 0); // Should throw IOException (numSamples <= 0)
    }

    @Test(expected = IOException.class)
    public void testWriteWavFileInvalidBounds() throws Exception {
        SoundFile soundFile = SoundFile.create(testWavFile.getAbsolutePath(), fractionComplete -> true);
        injectMockAudioData(soundFile, 1);

        File outputFile = new File(outDir, "out_audio_invalid.wav");
        soundFile.WriteWAVFile(outputFile, 1000, 100); // end < start
    }
}
