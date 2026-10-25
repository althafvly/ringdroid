package com.ringdroid.soundfile

import android.media.MediaCodec
import android.media.MediaMuxer
import java.io.IOException

/** Feeds PCM through a started AAC encoder into a muxer, which it starts once the format is known. */
internal class AacEncoder(
    private val codec: MediaCodec,
    private val muxer: MediaMuxer,
    private val bytesPerFrame: Int,
    private val sampleRate: Int,
    private var samplesLeft: Int,
    private val read: (buffer: ByteArray, length: Int) -> Unit,
) {
    private val buffer = ByteArray(FRAME_SAMPLES * bytesPerFrame)
    private val info = MediaCodec.BufferInfo()
    private var samplesQueued = 0L
    private var doneReading = false
    private var track = -1

    fun run() {
        var finished = false
        while (!finished) {
            if (!doneReading) {
                feedInput()
            }
            finished = drainOutput()
        }
        if (track < 0) {
            throw IOException("Encoder produced no output")
        }
    }

    private fun feedInput() {
        val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex < 0) {
            return
        }
        val inputBuffer = codec.getInputBuffer(inputIndex)
        // Queue whole sample frames only, however large the input buffer is.
        val capacity = inputBuffer?.capacity() ?: 0
        val length = minOf(buffer.size, capacity, samplesLeft * bytesPerFrame) / bytesPerFrame * bytesPerFrame
        val presentationTimeUs = samplesQueued * MICROS_PER_SECOND / sampleRate
        if (inputBuffer == null || length == 0) {
            // Some encoders stamp their last frame with this time; the muxer rejects going back.
            codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            doneReading = true
        } else {
            inputBuffer.clear()
            read(buffer, length)
            inputBuffer.put(buffer, 0, length)
            codec.queueInputBuffer(inputIndex, 0, length, presentationTimeUs, 0)
            samplesQueued += length / bytesPerFrame
            samplesLeft -= length / bytesPerFrame
        }
    }

    // Returns true at the end of the stream.
    private fun drainOutput(): Boolean {
        val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            track = muxer.addTrack(codec.outputFormat)
            muxer.start()
        }
        if (outputIndex < 0) {
            return false
        }
        val outputBuffer = codec.getOutputBuffer(outputIndex)
        if (outputBuffer != null && isMediaData()) {
            muxer.writeSampleData(track, outputBuffer, info)
        }
        codec.releaseOutputBuffer(outputIndex, false)
        return info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
    }

    private fun isMediaData() = track >= 0 && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0

    companion object {
        /** Samples per channel in one AAC frame. */
        const val FRAME_SAMPLES = 1024
        private const val TIMEOUT_US = 100L
        private const val MICROS_PER_SECOND = 1_000_000L
    }
}
