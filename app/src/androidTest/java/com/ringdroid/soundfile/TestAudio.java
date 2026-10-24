package com.ringdroid.soundfile;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/** Helpers for generating and reading 16-bit PCM WAV files in tests. */
public final class TestAudio {
    public static final int SAMPLE_RATE = 44100;

    private TestAudio() {
    }

    public static File writeWav(File file, int channels, short[] interleaved) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(interleaved.length * 2)
                .order(ByteOrder.LITTLE_ENDIAN);
        data.asShortBuffer().put(interleaved);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(WAVHeader.getWAVHeader(SAMPLE_RATE, channels, interleaved.length / channels));
            out.write(data.array());
        }
        return file;
    }

    /** Returns the PCM data of a WAV file written by this app (44-byte header). */
    public static short[] readWavData(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        short[] samples = new short[(bytes.length - 44) / 2];
        ByteBuffer.wrap(bytes, 44, bytes.length - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                .get(samples);
        return samples;
    }

    /** Samples 0, 1, 2, ... so that every position is identifiable. */
    public static short[] ramp(int count) {
        short[] samples = new short[count];
        for (int i = 0; i < count; i++) {
            samples[i] = (short) i;
        }
        return samples;
    }

    public static SoundFile load(File file) throws Exception {
        SoundFile soundFile = SoundFile.create(file.getParentFile(), file.getPath(),
                fraction -> false);
        assertNotNull(soundFile);
        return soundFile;
    }
}
