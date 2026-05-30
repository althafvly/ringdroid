package com.ringdroid.soundfile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MP4HeaderTest {
    @Test
    public void testMP4Header() {
        int sampleRate = 44100;
        int numChannels = 2;
        int[] frameSizes = new int[]{2, 100, 200}; // first frame size must be 2
        int bitrate = 128000;

        byte[] header = MP4Header.getMP4Header(sampleRate, numChannels, frameSizes, bitrate);

        assertNotNull(header);

        // The first atom should be "ftyp"
        assertEquals('f', header[4]);
        assertEquals('t', header[5]);
        assertEquals('y', header[6]);
        assertEquals('p', header[7]);
    }

    @Test
    public void testInvalidFrameSizes() {
        int sampleRate = 44100;
        int numChannels = 2;
        int bitrate = 128000;

        // Invalid because first frame size is not 2
        int[] invalidFrameSizes = new int[]{100, 200};
        byte[] header = MP4Header.getMP4Header(sampleRate, numChannels, invalidFrameSizes, bitrate);

        assertNull(header);
    }
}
