package com.ringdroid.soundfile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class WAVHeaderTest {
    @Test
    public void testWAVHeader() {
        int sampleRate = 44100;
        int numChannels = 2;
        int numSamples = 1000;
        byte[] header = WAVHeader.getWAVHeader(sampleRate, numChannels, numSamples);

        assertNotNull(header);
        assertEquals(46, header.length);

        // Check "RIFF"
        assertEquals('R', header[0]);
        assertEquals('I', header[1]);
        assertEquals('F', header[2]);
        assertEquals('F', header[3]);

        // Check "WAVE"
        assertEquals('W', header[8]);
        assertEquals('A', header[9]);
        assertEquals('V', header[10]);
        assertEquals('E', header[11]);

        // Check "fmt "
        assertEquals('f', header[12]);
        assertEquals('m', header[13]);
        assertEquals('t', header[14]);
        assertEquals(' ', header[15]);

        // Check "data"
        assertEquals('d', header[36]);
        assertEquals('a', header[37]);
        assertEquals('t', header[38]);
        assertEquals('a', header[39]);
    }
}
