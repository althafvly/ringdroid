package com.ringdroid.soundfile;

import static com.ringdroid.soundfile.TestAudio.SAMPLE_RATE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SoundFileWriterTest {
    private static final int FRAME = 1024;
    private final List<File> mFiles = new ArrayList<>();
    private final List<SoundFile> mSoundFiles = new ArrayList<>();
    private File mDir;

    @Before
    public void setUp() {
        mDir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
    }

    @After
    public void tearDown() {
        for (SoundFile soundFile : mSoundFiles) {
            soundFile.release();
        }
        for (File file : mFiles) {
            file.delete();
        }
    }

    private File file(String name) {
        File file = new File(mDir, name);
        mFiles.add(file);
        return file;
    }

    private SoundFile load(int channels, short[] samples) throws Exception {
        File wav = TestAudio.writeWav(file("writer_input_" + channels + ".wav"), channels, samples);
        SoundFile soundFile = TestAudio.load(wav);
        mSoundFiles.add(soundFile);
        return soundFile;
    }

    @Test
    public void wavStereoKeepsHeaderAndChannelOrder() throws Exception {
        SoundFile soundFile = load(2, TestAudio.ramp(SAMPLE_RATE * 2));
        File out = file("writer_stereo.wav");
        new SoundFileWriter(soundFile, 1.0f).writeWAV(out, 0, 10);

        byte[] bytes = Files.readAllBytes(out.toPath());
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int dataSize = 10 * FRAME * 2 * 2;
        assertEquals(44 + dataSize, bytes.length);
        assertEquals(2, header.getShort(22));
        assertEquals(SAMPLE_RATE, header.getInt(24));
        assertEquals(dataSize, header.getInt(40));
        assertArrayEquals(TestAudio.ramp(10 * FRAME * 2), TestAudio.readWavData(out));
    }

    @Test
    public void wavRangeStartsAtStartFrame() throws Exception {
        SoundFile soundFile = load(1, TestAudio.ramp(SAMPLE_RATE));
        File out = file("writer_range.wav");
        new SoundFileWriter(soundFile, 1.0f).writeWAV(out, 3, 2);

        short[] data = TestAudio.readWavData(out);
        assertEquals(2 * FRAME, data.length);
        assertEquals(3 * FRAME, data[0]);
        assertEquals(5 * FRAME - 1, data[data.length - 1]);
    }

    @Test
    public void wavGainScalesAndClips() throws Exception {
        short[] pattern = {1000, -1000, 20000, -20000};
        short[] samples = new short[FRAME];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = pattern[i % pattern.length];
        }
        SoundFile soundFile = load(1, samples);
        File out = file("writer_gain.wav");
        new SoundFileWriter(soundFile, 2.0f).writeWAV(out, 0, 1);

        short[] data = TestAudio.readWavData(out);
        assertArrayEquals(new short[]{2000, -2000, Short.MAX_VALUE, Short.MIN_VALUE},
                Arrays.copyOf(data, 4));
    }

    @Test
    public void wavPadsPastEndWithSilence() throws Exception {
        SoundFile soundFile = load(1, TestAudio.ramp(1000));
        File out = file("writer_pad.wav");
        new SoundFileWriter(soundFile, 1.0f).writeWAV(out, 0, 2);

        short[] data = TestAudio.readWavData(out);
        assertEquals(2 * FRAME, data.length);
        assertArrayEquals(TestAudio.ramp(1000), Arrays.copyOf(data, 1000));
        for (int i = 1000; i < data.length; i++) {
            assertEquals(0, data[i]);
        }
    }

    @Test
    public void m4aRoundTripKeepsFormat() throws Exception {
        for (int channels = 1; channels <= 2; channels++) {
            SoundFile soundFile = load(channels, new short[SAMPLE_RATE * channels]);
            File out = file("writer_roundtrip_" + channels + ".m4a");
            new SoundFileWriter(soundFile, 1.0f).writeM4A(out, 0, 40);

            SoundFile decoded = TestAudio.load(out);
            mSoundFiles.add(decoded);
            assertEquals(channels, decoded.getChannels());
            assertEquals(SAMPLE_RATE, decoded.getSampleRate());
            assertTrue("samples " + decoded.getNumSamples(),
                    decoded.getNumSamples() >= 40 * FRAME && decoded.getNumSamples() <= 44 * FRAME);
        }
    }

    @Test
    public void m4aLongerThanAMinuteHasCorrectDuration() throws Exception {
        SoundFile soundFile = load(2, new short[SAMPLE_RATE * 2]);
        File out = file("writer_long.m4a");
        int numFrames = 2600; // ~60 s, past the old header's 48.7 s overflow
        new SoundFileWriter(soundFile, 1.0f).writeM4A(out, 0, numFrames);

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(out.getPath());
            assertEquals(1, extractor.getTrackCount());
            MediaFormat format = extractor.getTrackFormat(0);
            assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, format.getString(MediaFormat.KEY_MIME));
            long expectedUs = numFrames * (long) FRAME * 1000000L / SAMPLE_RATE;
            long durationUs = format.getLong(MediaFormat.KEY_DURATION);
            assertTrue("duration " + durationUs, Math.abs(durationUs - expectedUs) < 200000);
        } finally {
            extractor.release();
        }
    }

    @Test(expected = IOException.class)
    public void emptyRangeThrows() throws Exception {
        SoundFile soundFile = load(1, TestAudio.ramp(SAMPLE_RATE));
        new SoundFileWriter(soundFile, 1.0f).writeM4A(file("writer_empty.m4a"), 0, 0);
    }

    @Test(expected = IOException.class)
    public void startPastEndThrows() throws Exception {
        SoundFile soundFile = load(1, TestAudio.ramp(SAMPLE_RATE));
        new SoundFileWriter(soundFile, 1.0f).writeWAV(file("writer_past_end.wav"), 1000, 100);
    }

    @Test(expected = IllegalStateException.class)
    public void releasedSoundFileThrows() throws Exception {
        SoundFile soundFile = load(1, TestAudio.ramp(SAMPLE_RATE));
        soundFile.release();
        new SoundFileWriter(soundFile, 1.0f);
    }
}
