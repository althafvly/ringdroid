package com.ringdroid.soundfile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
        assertEquals(44, header.length);

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

        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int dataSize = numSamples * numChannels * 2;
        assertEquals(36 + dataSize, buf.getInt(4));
        assertEquals(numChannels, buf.getShort(22));
        assertEquals(sampleRate, buf.getInt(24));
        assertEquals(dataSize, buf.getInt(40));
    }
}
