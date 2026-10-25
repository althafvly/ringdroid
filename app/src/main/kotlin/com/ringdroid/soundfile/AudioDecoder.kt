package com.ringdroid.soundfile

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer

/** Feeds an extractor's selected track through a started codec into a [PcmFile]. */
internal class AudioDecoder(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    private val isAac: Boolean,
    private val fileSize: Long,
    private val listener: SoundFile.ProgressListener?,
    private val pcm: PcmFile,
) {
    /** The decoder's output format, if it reported one. */
    var outputFormat: MediaFormat? = null
        private set

    private val info = MediaCodec.BufferInfo()
    private var chunk = ByteArray(0)
    private var totalSizeRead = 0L
    private var doneReading = false
    private var firstSampleData = true
    private var inputDoneAt = 0L
    private var canceled = false

    /** Decodes to the end of the stream. Returns false if the listener canceled. */
    fun run(): Boolean {
        var finished = false
        while (!finished && !canceled) {
            if (!doneReading) {
                feedInput()
            }
            finished = !canceled && drainOutput()
        }
        return !canceled
    }

    private fun feedInput() {
        val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        val inputBuffer = (if (inputIndex >= 0) codec.getInputBuffer(inputIndex) else null) ?: return
        inputBuffer.clear()
        val sampleSize = extractor.readSampleData(inputBuffer, 0)
        when {
            firstSampleData && isAac && sampleSize == AAC_CONFIG_SIZE -> {
                // Some devices (e.g. Samsung S3) crash when given the 2-byte AAC config
                // that the codec has already been configured with, so skip it.
                extractor.advance()
                totalSizeRead += sampleSize
                codec.queueInputBuffer(inputIndex, 0, 0, 0, 0)
            }

            sampleSize < 0 -> {
                codec.queueInputBuffer(inputIndex, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                doneReading = true
                inputDoneAt = SystemClock.uptimeMillis()
            }

            else -> {
                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
                totalSizeRead += sampleSize
                canceled = listener?.reportProgress(totalSizeRead.toDouble() / fileSize) == true
            }
        }
        firstSampleData = false
    }

    // Returns true at the end of the stream.
    private fun drainOutput(): Boolean {
        val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        return when {
            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val format = codec.outputFormat
                outputFormat = format
                Log.d(TAG, "Decoder output format: $format")
                false
            }

            outputIndex >= 0 -> {
                readOutput(outputIndex)
            }

            // Some devices never flag the end of stream; stop once output dries up.
            else -> {
                doneReading && SystemClock.uptimeMillis() - inputDoneAt > EOS_TIMEOUT_MS
            }
        }
    }

    private fun readOutput(outputIndex: Int): Boolean {
        // Release every buffer, including empty ones, or the codec runs out of them.
        val outputBuffer = codec.getOutputBuffer(outputIndex)
        if (outputBuffer != null && info.size > 0) {
            write(outputBuffer)
        }
        codec.releaseOutputBuffer(outputIndex, false)
        if (doneReading) {
            inputDoneAt = SystemClock.uptimeMillis()
        }
        return info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
    }

    private fun write(outputBuffer: ByteBuffer) {
        if (chunk.size < info.size) {
            chunk = ByteArray(info.size)
        }
        outputBuffer.get(chunk, 0, info.size)
        var output = pcm.buffer
        if (output.remaining() < info.size) {
            // Estimate the final size from the share of input read so far.
            val position = output.position()
            val estimate = (position * (fileSize.toDouble() / maxOf(totalSizeRead, 1L)) * GROWTH_MARGIN).toLong()
            output = pcm.grow(maxOf(estimate, position + info.size + MIN_GROWTH_BYTES))
        }
        output.put(chunk, 0, info.size)
    }

    private companion object {
        const val TAG = "AudioDecoder"
        const val TIMEOUT_US = 100L
        const val AAC_CONFIG_SIZE = 2
        const val EOS_TIMEOUT_MS = 2000L
        const val GROWTH_MARGIN = 1.2
        const val MIN_GROWTH_BYTES = 5L shl 20
    }
}
