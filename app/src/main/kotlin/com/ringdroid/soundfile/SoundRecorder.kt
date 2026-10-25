package com.ringdroid.soundfile

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

/** Records mono 16-bit audio from the microphone into a SoundFile. */
class SoundRecorder private constructor() {
    private var bytes: ByteBuffer = allocate(INITIAL_SECONDS * SAMPLE_RATE * Short.SIZE_BYTES)
    private var samples: ShortBuffer = bytes.asShortBuffer()

    @SuppressLint("MissingPermission")
    private fun recordInternal(listener: SoundFile.ProgressListener): SoundFile? {
        // The buffer holds at least one second of audio.
        val bufferSize =
            maxOf(
                SAMPLE_RATE * 2,
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ),
            )
        val audioRecord =
            AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return null
        }
        try {
            audioRecord.startRecording()
            recordLoop(audioRecord, listener)
        } finally {
            try {
                audioRecord.stop()
            } catch (ignored: IllegalStateException) {
            }
            audioRecord.release()
        }
        val numSamples = samples.position()
        bytes.rewind()
        return SoundFile.fromRecording(bytes, SAMPLE_RATE, numSamples)
    }

    private fun recordLoop(audioRecord: AudioRecord, listener: SoundFile.ProgressListener) {
        val buffer = ShortArray(CHUNK_SAMPLES)
        var done = false
        while (!done) {
            // Grow by 10 seconds when the next chunk would not fit.
            val hasRoom =
                samples.remaining() >= CHUNK_SAMPLES ||
                    grow(GROW_SECONDS * SAMPLE_RATE * Short.SIZE_BYTES)
            val numRead = if (hasRoom) audioRecord.read(buffer, 0, buffer.size) else -1
            if (numRead >= 0) {
                samples.put(buffer, 0, numRead)
            }
            done =
                numRead < 0 || listener.reportProgress(samples.position().toDouble() / SAMPLE_RATE)
        }
    }

    private fun grow(extraBytes: Int): Boolean {
        val oldBytes = bytes
        val position = samples.position()
        val newBytes =
            try {
                allocate(oldBytes.capacity() + extraBytes)
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Recording buffer full", e)
                return false
            }
        oldBytes.rewind()
        newBytes.put(oldBytes).rewind()
        bytes = newBytes
        samples = newBytes.asShortBuffer()
        samples.position(position)
        return true
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHUNK_SAMPLES = 1024
        private const val INITIAL_SECONDS = 20
        private const val GROW_SECONDS = 10
        private const val TAG = "SoundRecorder"

        private fun allocate(capacity: Int): ByteBuffer =
            ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN)

        /**
         * Records until the listener asks to stop. The listener receives the elapsed time in
         * seconds.
         *
         * @return the recording, or null if there is no listener or the microphone is unavailable
         */
        fun record(listener: SoundFile.ProgressListener?): SoundFile? =
            listener?.let { SoundRecorder().recordInternal(it) }
    }
}
