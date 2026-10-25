package com.ringdroid.soundfile

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.ShortBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Decoded 16-bit PCM audio, either decoded from a file into a memory-mapped temp file or recorded
 * from the microphone. Samples are interleaved: {s1c1, s1c2, ..., s1cM, s2c1, ...}.
 */
class SoundFile private constructor() {
    /** Return true from [reportProgress] to cancel loading or stop recording. */
    fun interface ProgressListener {
        // Loading reports a fraction between 0 and 1; recording reports elapsed seconds.
        fun reportProgress(fractionComplete: Double): Boolean
    }

    class InvalidInputException(
        message: String,
    ) : Exception(message)

    var filetype: String? = null
        private set
    var avgBitrateKbps = 0
        private set
    var sampleRate = 0
        private set
    var channels = 0
        private set

    /** Number of samples per channel. */
    var numSamples = 0
        private set

    // Waveform data, one gain per frame of SAMPLES_PER_FRAME samples.
    var numFrames = 0
        private set
    var frameGains: IntArray? = null
        private set
    val samplesPerFrame: Int
        get() = SAMPLES_PER_FRAME

    private var progressListener: ProgressListener? = null
    private var decodedBytes: ByteBuffer? = null
    private var decodedSamples: ShortBuffer? = null
    private var pcmTempFile: File? = null
    private var pcmRaf: RandomAccessFile? = null
    private var pcmChannel: FileChannel? = null

    /** Independent view of the raw PCM bytes, or null after [release]. */
    internal fun getDecodedBytes(): ByteBuffer? = decodedBytes?.duplicate()

    /** Read-only view of the samples, or null after [release]. */
    val samples: ShortBuffer?
        get() = decodedSamples?.asReadOnlyBuffer()

    /** Frees the samples and deletes the temporary PCM file. */
    fun release() {
        decodedBytes = null
        decodedSamples = null
        frameGains = null
        try {
            pcmChannel?.close()
        } catch (ignored: IOException) {
        }
        pcmChannel = null
        try {
            pcmRaf?.close()
        } catch (ignored: IOException) {
        }
        pcmRaf = null
        pcmTempFile?.let { file ->
            synchronized(activePcmFiles) { activePcmFiles.remove(file) }
            if (!file.delete() && file.exists()) {
                Log.w(TAG, "Failed to delete temporary PCM file: ${file.absolutePath}")
            }
        }
        pcmTempFile = null
    }

    private fun readFile(
        cacheDir: File,
        inputFile: File,
    ) {
        filetype = inputFile.path.substringAfterLast('.')
        val fileSize = inputFile.length()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(inputFile.path)
            val trackIndex =
                (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: throw InvalidInputException("No audio track found in $inputFile")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime =
                format.getString(MediaFormat.KEY_MIME)
                    ?: throw InvalidInputException("Audio track has no MIME type in $inputFile")
            if (!format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) || !format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                throw InvalidInputException("Audio track missing channel count or sample rate in $inputFile")
            }
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

            // The container duration only sizes the initial buffer; decoding runs to end of stream.
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val expectedBytes = durationUs * sampleRate / 1_000_000L * channels * 2

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            synchronized(activePcmFiles) {
                deleteStalePcmFiles(cacheDir)
                val tempFile = File.createTempFile(PCM_PREFIX, ".raw", cacheDir)
                pcmTempFile = tempFile
                activePcmFiles.add(tempFile)
            }
            val initialSize = maxOf(expectedBytes, 1L shl 20)
            val raf = RandomAccessFile(pcmTempFile, "rw")
            pcmRaf = raf
            raf.setLength(initialSize)
            val channel = raf.channel
            pcmChannel = channel
            var output: MappedByteBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, initialSize)
            decodedBytes = output

            if (!decode(extractor, codec, format, fileSize) { newSize ->
                    val position = output.position()
                    raf.setLength(newSize)
                    output = channel.map(FileChannel.MapMode.READ_WRITE, 0, newSize)
                    output.position(position)
                    decodedBytes = output
                    output
                }
            ) {
                return
            }
            numSamples = output.position() / (channels * 2)
            output.rewind()
            output.order(ByteOrder.LITTLE_ENDIAN)
            decodedSamples = output.asShortBuffer()
            avgBitrateKbps = if (numSamples > 0) (fileSize * 8 * sampleRate / numSamples / 1000).toInt() else 0
            computeFrameGains()
        } finally {
            if (codec != null) {
                try {
                    codec.stop()
                } catch (ignored: IllegalStateException) {
                }
                codec.release()
            }
            extractor.release()
        }
    }

    /**
     * Feeds the extractor through the codec into [decodedBytes], calling [grow] with a new total
     * size when more room is needed. Returns false if the listener canceled.
     */
    private fun decode(
        extractor: MediaExtractor,
        codec: MediaCodec,
        format: MediaFormat,
        fileSize: Long,
        grow: (Long) -> ByteBuffer,
    ): Boolean {
        val info = MediaCodec.BufferInfo()
        var chunk = ByteArray(0)
        var totalSizeRead = 0L
        var doneReading = false
        var firstSampleData = true
        var inputDoneAt = 0L
        var output = decodedBytes!!
        while (true) {
            if (!doneReading) {
                val inputIndex = codec.dequeueInputBuffer(100)
                val inputBuffer = if (inputIndex >= 0) codec.getInputBuffer(inputIndex) else null
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (firstSampleData && format.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm" && sampleSize == 2) {
                        // Some devices (e.g. Samsung S3) crash when given the 2-byte AAC config
                        // that the codec has already been configured with, so skip it.
                        extractor.advance()
                        totalSizeRead += sampleSize
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, 0)
                    } else if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        doneReading = true
                        inputDoneAt = SystemClock.uptimeMillis()
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                        totalSizeRead += sampleSize
                        if (progressListener?.reportProgress(totalSizeRead.toDouble() / fileSize) == true) {
                            return false
                        }
                    }
                    firstSampleData = false
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(info, 100)
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val outputFormat = codec.outputFormat
                sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                Log.d(TAG, "Decoder output format: $sampleRate Hz, $channels channels")
            } else if (outputIndex >= 0) {
                // Release every buffer, including empty ones, or the codec runs out of them.
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && info.size > 0) {
                    if (chunk.size < info.size) {
                        chunk = ByteArray(info.size)
                    }
                    outputBuffer.get(chunk, 0, info.size)
                    if (output.remaining() < info.size) {
                        // Estimate the final size from the share of input read so far.
                        val position = output.position()
                        val estimate = (position * (fileSize.toDouble() / maxOf(totalSizeRead, 1L)) * 1.2).toLong()
                        output = grow(maxOf(estimate, position + info.size + 5L * (1 shl 20)))
                    }
                    output.put(chunk, 0, info.size)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    return true
                }
                inputDoneAt = if (doneReading) SystemClock.uptimeMillis() else inputDoneAt
            } else if (doneReading && SystemClock.uptimeMillis() - inputDoneAt > EOS_TIMEOUT_MS) {
                // Some devices never flag the end of stream; stop once output dries up.
                return true
            }
        }
    }

    private fun computeFrameGains() {
        val samples = decodedSamples!!
        val frames = (numSamples + SAMPLES_PER_FRAME - 1) / SAMPLES_PER_FRAME
        val gains = IntArray(frames)
        samples.rewind()
        for (i in 0 until frames) {
            var gain = 0
            repeat(SAMPLES_PER_FRAME) {
                var value = 0
                var k = 0
                while (k < channels && samples.hasRemaining()) {
                    value += abs(samples.get().toInt())
                    k++
                }
                gain = maxOf(gain, value / channels)
            }
            gains[i] = sqrt(gain.toDouble()).toInt()
        }
        samples.rewind()
        numFrames = frames
        frameGains = gains
    }

    companion object {
        private const val TAG = "SoundFile"
        private const val PCM_PREFIX = "ringdroid_pcm_"
        private const val SAMPLES_PER_FRAME = 1024
        private const val EOS_TIMEOUT_MS = 2000L
        private val SUPPORTED_EXTENSIONS =
            arrayOf("mp3", "wav", "3gpp", "3gp", "amr", "aac", "m4a", "ogg", "flac", "opus", "wma", "mkv")

        // Temp files in use by this process; any other ones were left behind by a killed process.
        private val activePcmFiles = mutableSetOf<File>()

        fun getSupportedExtensions(): Array<String> = SUPPORTED_EXTENSIONS.copyOf()

        fun isFilenameSupported(filename: String): Boolean = SUPPORTED_EXTENSIONS.any { filename.endsWith(".$it") }

        /**
         * Decodes [fileName]. Returns null if the extension is unsupported or the listener
         * canceled.
         */
        fun create(
            cacheDir: File,
            fileName: String,
            progressListener: ProgressListener?,
        ): SoundFile? {
            val file = File(fileName)
            if (!file.exists()) {
                throw FileNotFoundException(fileName)
            }
            val name = file.name.lowercase(Locale.ROOT)
            if (!name.contains('.') || name.substringAfterLast('.') !in SUPPORTED_EXTENSIONS) {
                return null
            }
            val soundFile = SoundFile()
            soundFile.progressListener = progressListener
            var readCompleted = false
            try {
                soundFile.readFile(cacheDir, file)
                readCompleted = true
                if (soundFile.decodedSamples == null) {
                    // Canceled by the progress listener.
                    soundFile.release()
                    return null
                }
                return soundFile
            } finally {
                if (!readCompleted) {
                    soundFile.release()
                }
            }
        }

        /** Wraps mono 16-bit little-endian PCM recorded from the microphone. */
        internal fun fromRecording(
            pcm: ByteBuffer,
            sampleRate: Int,
            numSamples: Int,
        ): SoundFile {
            val soundFile = SoundFile()
            soundFile.filetype = "raw"
            soundFile.sampleRate = sampleRate
            soundFile.channels = 1
            soundFile.numSamples = numSamples
            soundFile.avgBitrateKbps = sampleRate * 16 / 1000
            soundFile.decodedBytes = pcm.order(ByteOrder.LITTLE_ENDIAN)
            soundFile.decodedSamples = pcm.asShortBuffer()
            soundFile.computeFrameGains()
            return soundFile
        }

        private fun deleteStalePcmFiles(dir: File) {
            dir.listFiles { _, name -> name.startsWith(PCM_PREFIX) }?.forEach { file ->
                if (file !in activePcmFiles && !file.delete()) {
                    Log.w(TAG, "Failed to delete stale PCM file: $file")
                }
            }
        }
    }
}
