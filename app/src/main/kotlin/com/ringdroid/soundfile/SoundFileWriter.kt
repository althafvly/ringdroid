package com.ringdroid.soundfile

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** Writes a range of a decoded SoundFile as M4A (AAC) or 16-bit PCM WAV. */
class SoundFileWriter(
    soundFile: SoundFile,
    private val gain: Float,
) {
    private val decodedBytes: ByteBuffer =
        soundFile.getDecodedBytes() ?: throw IllegalStateException("Sound file has no decoded samples")
    private val sampleRate = soundFile.sampleRate
    private val channels = soundFile.channels
    private val samplesPerFrame = soundFile.samplesPerFrame
    private val bytesPerFrame = channels * 2

    fun writeM4A(
        outputFile: File,
        startFrame: Int,
        numFrames: Int,
    ) {
        writeAAC(MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4), startFrame, numFrames)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun writeM4A(
        context: Context,
        outputUri: Uri,
        startFrame: Int,
        numFrames: Int,
    ) {
        val pfd = context.contentResolver.openFileDescriptor(outputUri, "rw") ?: throw IOException("Cannot open MediaStore output")
        pfd.use {
            writeAAC(MediaMuxer(it.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4), startFrame, numFrames)
        }
    }

    fun writeWAV(
        outputFile: File,
        startFrame: Int,
        numFrames: Int,
    ) {
        writeWAVData(FileOutputStream(outputFile), startFrame, numFrames)
    }

    fun writeWAV(
        context: Context,
        outputUri: Uri,
        startFrame: Int,
        numFrames: Int,
    ) {
        val out = context.contentResolver.openOutputStream(outputUri) ?: throw IOException("Cannot open MediaStore output stream")
        writeWAVData(out, startFrame, numFrames)
    }

    private fun framesToSeconds(frames: Int): Float = frames.toFloat() * samplesPerFrame / sampleRate

    /** Positions [decodedBytes] at the start of the range and returns its samples per channel. */
    private fun seekToRange(
        startFrame: Int,
        numFrames: Int,
    ): Int {
        val startTime = framesToSeconds(startFrame)
        val endTime = framesToSeconds(startFrame + numFrames)
        val startOffset = (startTime * sampleRate).toInt() * bytesPerFrame
        val numSamples = ((endTime - startTime) * sampleRate).toInt()
        if (numSamples <= 0) {
            throw IOException("Invalid time bounds: $startTime to $endTime")
        }
        if (startOffset < 0 || startOffset > decodedBytes.limit()) {
            throw IOException("Start offset out of bounds: $startOffset")
        }
        decodedBytes.position(startOffset)
        return numSamples
    }

    /** Reads [length] bytes, padding with silence past the end, and applies the gain. */
    private fun readPadded(
        buffer: ByteArray,
        length: Int,
    ) {
        val n = minOf(length, decodedBytes.remaining())
        decodedBytes.get(buffer, 0, n)
        buffer.fill(0, n, length)
        if (gain != 1.0f) {
            applyGain(buffer, length)
        }
    }

    private fun applyGain(
        buffer: ByteArray,
        length: Int,
    ) {
        for (i in 0 until length step 2) {
            val sample = ((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)).toShort()
            val scaled = (sample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = scaled.toByte()
            buffer[i + 1] = (scaled shr 8).toByte()
        }
    }

    private fun writeAAC(
        muxer: MediaMuxer,
        startFrame: Int,
        numFrames: Int,
    ) {
        val mimeType = MediaFormat.MIMETYPE_AUDIO_AAC
        var codec: MediaCodec? = null
        try {
            // Two extra AAC frames make up for the encoder priming delay.
            var samplesLeft = seekToRange(startFrame, numFrames) + 2 * AAC_FRAME_SAMPLES
            codec = MediaCodec.createEncoderByType(mimeType)
            val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channels)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000 * channels)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val buffer = ByteArray(AAC_FRAME_SAMPLES * bytesPerFrame)
            var samplesQueued = 0L
            var doneReading = false
            var track = -1
            val info = MediaCodec.BufferInfo()
            while (true) {
                if (!doneReading) {
                    val inputIndex = codec.dequeueInputBuffer(100)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        // Queue whole sample frames only, however large the input buffer is.
                        val length =
                            if (inputBuffer == null) {
                                0
                            } else {
                                minOf(buffer.size, inputBuffer.capacity(), samplesLeft * bytesPerFrame) / bytesPerFrame * bytesPerFrame
                            }
                        if (length == 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            doneReading = true
                        } else {
                            inputBuffer!!.clear()
                            readPadded(buffer, length)
                            inputBuffer.put(buffer, 0, length)
                            val presentationTimeUs = samplesQueued * 1_000_000L / sampleRate
                            codec.queueInputBuffer(inputIndex, 0, length, presentationTimeUs, 0)
                            samplesQueued += length / bytesPerFrame
                            samplesLeft -= length / bytesPerFrame
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 100)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                } else if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (outputBuffer != null && track >= 0 && info.size > 0 && !isConfig) {
                        muxer.writeSampleData(track, outputBuffer, info)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }
            if (track < 0) {
                throw IOException("Encoder produced no output")
            }
            codec.stop()
            muxer.stop()
        } finally {
            codec?.release()
            muxer.release()
        }
    }

    private fun writeWAVData(
        outputStream: OutputStream,
        startFrame: Int,
        numFrames: Int,
    ) {
        outputStream.use { out ->
            val numSamples = seekToRange(startFrame, numFrames)
            out.write(WAVHeader.getWAVHeader(sampleRate, channels, numSamples))
            val buffer = ByteArray(1024 * bytesPerFrame)
            var bytesLeft = numSamples * bytesPerFrame
            while (bytesLeft > 0) {
                val length = minOf(buffer.size, bytesLeft)
                readPadded(buffer, length)
                out.write(buffer, 0, length)
                bytesLeft -= length
            }
        }
    }

    private companion object {
        const val AAC_FRAME_SAMPLES = 1024
    }
}
