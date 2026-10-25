package com.ringdroid.soundfile

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
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

    class InvalidInputException(message: String) : Exception(message)

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
    private var pcmFile: PcmFile? = null

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
        pcmFile?.delete()
        pcmFile = null
    }

    private fun readFile(cacheDir: File, inputFile: File) {
        filetype = inputFile.path.substringAfterLast('.')
        val fileSize = inputFile.length()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val format = selectAudioTrack(extractor, inputFile)
            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // The container duration only sizes the initial buffer; decoding runs to end of stream.
            val durationUs =
                if (format.containsKey(
                        MediaFormat.KEY_DURATION,
                    )
                ) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else {
                    0L
                }
            val expectedBytes =
                durationUs * sampleRate / MICROS_PER_SECOND * channels * Short.SIZE_BYTES
            val pcm = PcmFile.create(cacheDir, maxOf(expectedBytes, MIN_PCM_BYTES))
            pcmFile = pcm
            val decoder =
                AudioDecoder(extractor, codec, mime == AAC_MIME, fileSize, progressListener, pcm)
            if (decoder.run()) {
                decoder.outputFormat?.let {
                    sampleRate = it.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = it.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                useDecoded(pcm.buffer, fileSize)
            }
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

    private fun useDecoded(output: ByteBuffer, fileSize: Long) {
        numSamples = output.position() / (channels * Short.SIZE_BYTES)
        output.rewind()
        output.order(ByteOrder.LITTLE_ENDIAN)
        decodedBytes = output
        val shorts = output.asShortBuffer()
        decodedSamples = shorts
        avgBitrateKbps =
            if (numSamples >
                0
            ) {
                (fileSize * Byte.SIZE_BITS * sampleRate / numSamples / BITS_PER_KILOBIT).toInt()
            } else {
                0
            }
        computeFrameGains(shorts)
    }

    private fun computeFrameGains(samples: ShortBuffer) {
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
        private const val SAMPLES_PER_FRAME = 1024
        private const val AAC_MIME = "audio/mp4a-latm"
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val MIN_PCM_BYTES = 1L shl 20
        private const val BITS_PER_KILOBIT = 1000
        private val SUPPORTED_EXTENSIONS =
            arrayOf(
                "mp3",
                "wav",
                "3gpp",
                "3gp",
                "amr",
                "aac",
                "m4a",
                "ogg",
                "flac",
                "opus",
                "wma",
                "mkv",
            )

        fun getSupportedExtensions(): Array<String> = SUPPORTED_EXTENSIONS.copyOf()

        fun isFilenameSupported(filename: String): Boolean = SUPPORTED_EXTENSIONS.any {
            filename.endsWith(".$it")
        }

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
            if (!isFilenameSupported(file.name.lowercase(Locale.ROOT))) {
                return null
            }
            val soundFile = SoundFile()
            soundFile.progressListener = progressListener
            var loaded = false
            try {
                soundFile.readFile(cacheDir, file)
                // No samples means the progress listener canceled.
                loaded = soundFile.decodedSamples != null
            } finally {
                if (!loaded) {
                    soundFile.release()
                }
            }
            return soundFile.takeIf { loaded }
        }

        /** Wraps mono 16-bit little-endian PCM recorded from the microphone. */
        internal fun fromRecording(pcm: ByteBuffer, sampleRate: Int, numSamples: Int): SoundFile {
            val soundFile = SoundFile()
            soundFile.filetype = "raw"
            soundFile.sampleRate = sampleRate
            soundFile.channels = 1
            soundFile.numSamples = numSamples
            soundFile.avgBitrateKbps = sampleRate * Short.SIZE_BITS / BITS_PER_KILOBIT
            soundFile.decodedBytes = pcm.order(ByteOrder.LITTLE_ENDIAN)
            val shorts = pcm.asShortBuffer()
            soundFile.decodedSamples = shorts
            soundFile.computeFrameGains(shorts)
            return soundFile
        }

        // Selects the first audio track and returns its format, which must be decodable.
        private fun selectAudioTrack(extractor: MediaExtractor, inputFile: File): MediaFormat {
            extractor.setDataSource(inputFile.path)
            val trackIndex =
                (0 until extractor.trackCount).firstOrNull {
                    extractor
                        .getTrackFormat(
                            it,
                        ).getString(MediaFormat.KEY_MIME)
                        ?.startsWith("audio/") ==
                        true
                } ?: throw InvalidInputException("No audio track found in $inputFile")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            if (format.getString(MediaFormat.KEY_MIME) == null ||
                !format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ||
                !format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
            ) {
                throw InvalidInputException(
                    "Audio track missing its MIME type, channel count or sample rate in $inputFile",
                )
            }
            return format
        }
    }
}
