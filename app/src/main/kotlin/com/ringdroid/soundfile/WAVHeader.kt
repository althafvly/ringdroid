package com.ringdroid.soundfile

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WAVHeader {
    private const val HEADER_SIZE = 44
    private const val CHUNK_HEADER_SIZE = 8
    private const val FMT_CHUNK_SIZE = 16
    private const val BITS_PER_SAMPLE = 16
    private const val PCM_FORMAT: Short = 1

    fun getWAVHeader(sampleRate: Int, numChannels: Int, numSamples: Int): ByteArray {
        val bytesPerFrame = Short.SIZE_BYTES * numChannels
        val dataSize = numSamples * bytesPerFrame
        return ByteBuffer
            .allocate(HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray())
            .putInt(HEADER_SIZE - CHUNK_HEADER_SIZE + dataSize)
            .put("WAVEfmt ".toByteArray())
            .putInt(FMT_CHUNK_SIZE)
            .putShort(PCM_FORMAT)
            .putShort(numChannels.toShort())
            .putInt(sampleRate)
            .putInt(sampleRate * bytesPerFrame)
            .putShort(bytesPerFrame.toShort())
            .putShort(BITS_PER_SAMPLE.toShort())
            .put("data".toByteArray())
            .putInt(dataSize)
            .array()
    }
}
