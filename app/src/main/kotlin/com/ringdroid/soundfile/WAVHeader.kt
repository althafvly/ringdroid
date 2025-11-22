/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ringdroid.soundfile

// sampling frequency in Hz (e.g. 44100).
class WAVHeader(
    private val mSampleRate: Int, // number of channels.
    private val mChannels: Int, // total number of samples per channel.
    private val mNumSamples: Int,
) {
    // number of bytes per sample, all channels included.
    // assuming 2 bytes per sample (for 1 channel)
    private val mNumBytesPerSample: Int = 2 * mChannels
    private var mHeader: ByteArray? = null // the complete header.

    init {
        setHeader()
    }

    private fun setHeader() {
        val header = ByteArray(46)
        var offset = 0

        // set the RIFF chunk
        System.arraycopy(
            byteArrayOf(
                'R'.code.toByte(),
                'I'.code.toByte(),
                'F'.code.toByte(),
                'F'.code.toByte(),
            ),
            0,
            header,
            offset,
            4,
        )
        offset += 4
        var size: Int = 36 + mNumSamples * mNumBytesPerSample
        header[offset++] = (size and 0xFF).toByte()
        header[offset++] = ((size shr 8) and 0xFF).toByte()
        header[offset++] = ((size shr 16) and 0xFF).toByte()
        header[offset++] = ((size shr 24) and 0xFF).toByte()
        System.arraycopy(
            byteArrayOf(
                'W'.code.toByte(),
                'A'.code.toByte(),
                'V'.code.toByte(),
                'E'.code.toByte(),
            ),
            0,
            header,
            offset,
            4,
        )
        offset += 4

        // set the fmt chunk
        System.arraycopy(
            byteArrayOf(
                'f'.code.toByte(),
                'm'.code.toByte(),
                't'.code.toByte(),
                ' '.code.toByte(),
            ),
            0,
            header,
            offset,
            4,
        )
        offset += 4
        System.arraycopy(byteArrayOf(0x10, 0, 0, 0), 0, header, offset, 4) // chunk size = 16
        offset += 4
        System.arraycopy(byteArrayOf(1, 0), 0, header, offset, 2) // format = 1 for PCM
        offset += 2
        header[offset++] = (mChannels and 0xFF).toByte()
        header[offset++] = ((mChannels shr 8) and 0xFF).toByte()
        header[offset++] = (mSampleRate and 0xFF).toByte()
        header[offset++] = ((mSampleRate shr 8) and 0xFF).toByte()
        header[offset++] = ((mSampleRate shr 16) and 0xFF).toByte()
        header[offset++] = ((mSampleRate shr 24) and 0xFF).toByte()
        val byteRate = mSampleRate * mNumBytesPerSample
        header[offset++] = (byteRate and 0xFF).toByte()
        header[offset++] = ((byteRate shr 8) and 0xFF).toByte()
        header[offset++] = ((byteRate shr 16) and 0xFF).toByte()
        header[offset++] = ((byteRate shr 24) and 0xFF).toByte()
        header[offset++] = (mNumBytesPerSample and 0xFF).toByte()
        header[offset++] = ((mNumBytesPerSample shr 8) and 0xFF).toByte()
        System.arraycopy(byteArrayOf(0x10, 0), 0, header, offset, 2)
        offset += 2

        // set the beginning of the data chunk
        System.arraycopy(
            byteArrayOf(
                'd'.code.toByte(),
                'a'.code.toByte(),
                't'.code.toByte(),
                'a'.code.toByte(),
            ),
            0,
            header,
            offset,
            4,
        )
        offset += 4
        size = mNumSamples * mNumBytesPerSample
        header[offset++] = (size and 0xFF).toByte()
        header[offset++] = ((size shr 8) and 0xFF).toByte()
        header[offset++] = ((size shr 16) and 0xFF).toByte()
        header[offset++] = ((size shr 24) and 0xFF).toByte()

        mHeader = header
    }

    companion object {
        @JvmStatic
        fun getWAVHeader(
            sampleRate: Int,
            numChannels: Int,
            numSamples: Int,
        ): ByteArray? = WAVHeader(sampleRate, numChannels, numSamples).mHeader
    }
}
