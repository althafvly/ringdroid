package com.ringdroid.soundfile

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WAVHeaderTest {
    @Test
    fun headerDescribesPcmData() {
        val sampleRate = 44100
        val numChannels = 2
        val numSamples = 1000
        val header = WAVHeader.getWAVHeader(sampleRate, numChannels, numSamples)

        assertEquals(44, header.size)
        assertEquals("RIFF", String(header, 0, 4))
        assertEquals("WAVE", String(header, 8, 4))
        assertEquals("fmt ", String(header, 12, 4))
        assertEquals("data", String(header, 36, 4))

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val dataSize = numSamples * numChannels * 2
        assertEquals(36 + dataSize, buf.getInt(4))
        assertEquals(1, buf.getShort(20).toInt()) // PCM
        assertEquals(numChannels, buf.getShort(22).toInt())
        assertEquals(sampleRate, buf.getInt(24))
        assertEquals(sampleRate * numChannels * 2, buf.getInt(28))
        assertEquals(16, buf.getShort(34).toInt())
        assertEquals(dataSize, buf.getInt(40))
    }
}
