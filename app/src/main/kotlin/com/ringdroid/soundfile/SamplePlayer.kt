package com.ringdroid.soundfile

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ShortBuffer
import kotlin.concurrent.thread

/** Streams decoded 16-bit samples to an AudioTrack from a dedicated thread. */
internal class SamplePlayer(
    private val samples: ShortBuffer,
    private val sampleRate: Int,
    private val channels: Int,
    // Number of samples per channel.
    private val numSamples: Int,
) {
    constructor(soundFile: SoundFile) : this(
        requireNotNull(soundFile.samples) { "Sound file has no samples" },
        soundFile.sampleRate,
        soundFile.channels,
        soundFile.numSamples,
    )

    private val audioTrack: AudioTrack
    private val buffer: ShortArray
    private var playbackStart = 0 // Start offset, in samples.
    private var playThread: Thread? = null

    @Volatile private var keepPlaying = true
    var onCompletionListener: (() -> Unit)? = null

    init {
        val channelMask = getChannelMask(channels)
        var bufferSize = channels * sampleRate * 2
        if (channelMask != AudioFormat.CHANNEL_INVALID) {
            bufferSize =
                maxOf(
                    AudioTrack.getMinBufferSize(
                        sampleRate,
                        channelMask,
                        AudioFormat.ENCODING_PCM_16BIT,
                    ),
                    bufferSize,
                )
        }
        buffer = ShortArray(bufferSize / 2)
        val formatBuilder =
            AudioFormat
                .Builder()
                .setSampleRate(
                    sampleRate,
                ).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        if (channelMask != AudioFormat.CHANNEL_INVALID) {
            formatBuilder.setChannelMask(channelMask)
        } else {
            formatBuilder.setChannelIndexMask((1 shl channels) - 1)
        }
        audioTrack =
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setAudioFormat(formatBuilder.build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(buffer.size * 2)
                .build()
        audioTrack.setNotificationMarkerPosition(numSamples - 1)
        audioTrack.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onPeriodicNotification(track: AudioTrack) = Unit

                override fun onMarkerReached(track: AudioTrack) {
                    stop()
                    onCompletionListener?.invoke()
                }
            },
        )
    }

    val isPlaying: Boolean
        get() = audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING

    val isPaused: Boolean
        get() = audioTrack.playState == AudioTrack.PLAYSTATE_PAUSED

    fun start() {
        if (isPlaying) {
            return
        }
        keepPlaying = true
        audioTrack.flush()
        audioTrack.play()
        playThread =
            thread {
                samples.position(playbackStart * channels)
                val limit = numSamples * channels
                while (samples.position() < limit && keepPlaying) {
                    val toWrite = minOf(limit - samples.position(), buffer.size)
                    samples.get(buffer, 0, toWrite)
                    audioTrack.write(buffer, 0, toWrite)
                }
            }
    }

    fun pause() {
        // write() blocks while paused.
        if (isPlaying) {
            audioTrack.pause()
        }
    }

    fun stop() {
        if (isPlaying || isPaused) {
            keepPlaying = false
            audioTrack.pause() // Stops the playback immediately.
            audioTrack.stop() // Unblocks write() so the thread can exit.
            try {
                playThread?.join()
            } catch (ignored: InterruptedException) {
            }
            playThread = null
            audioTrack.flush()
        }
    }

    fun release() {
        stop()
        audioTrack.release()
    }

    fun seekTo(msec: Int) {
        val wasPlaying = isPlaying
        stop()
        playbackStart = minOf((msec * (sampleRate / MILLIS_PER_SECOND)).toInt(), numSamples)
        audioTrack.setNotificationMarkerPosition(numSamples - 1 - playbackStart)
        if (wasPlaying) {
            start()
        }
    }

    val currentPosition: Int
        get() =
            (
                (playbackStart + audioTrack.playbackHeadPosition) *
                    (MILLIS_PER_SECOND / sampleRate)
                ).toInt()

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
        const val QUAD_CHANNELS = 4
        const val SURROUND_5_1_CHANNELS = 6
        const val SURROUND_7_1_CHANNELS = 8

        // Positional masks let AudioTrack downmix to the device's outputs.
        fun getChannelMask(channels: Int): Int = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            QUAD_CHANNELS -> AudioFormat.CHANNEL_OUT_QUAD
            SURROUND_5_1_CHANNELS -> AudioFormat.CHANNEL_OUT_5POINT1
            SURROUND_7_1_CHANNELS -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            else -> AudioFormat.CHANNEL_INVALID
        }
    }
}
