package com.ringdroid.soundfile

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WAVHeader {
    fun getWAVHeader(
        sampleRate: Int,
        numChannels: Int,
        numSamples: Int,
    ): ByteArray {
        val bytesPerFrame = 2 * numChannels
        val dataSize = numSamples * bytesPerFrame
        return ByteBuffer
            .allocate(44)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray())
            .putInt(36 + dataSize)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
            .putShort(1)
            .putShort(numChannels.toShort())
            .putInt(sampleRate)
            .putInt(sampleRate * bytesPerFrame)
            .putShort(bytesPerFrame.toShort())
            .putShort(16)
            .put("data".toByteArray())
            .putInt(dataSize)
            .array()
    }
}
