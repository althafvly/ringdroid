package com.ringdroid.data

import kotlin.math.roundToInt

/** Converts between time, audio frames and waveform pixels at one zoom level. */
internal class WaveformScale(
    sampleRate: Int,
    samplesPerFrame: Int,
    private val zoomFactor: Double,
) {
    private val framesPerSecond = sampleRate.toDouble() / samplesPerFrame

    fun secondsToFrames(seconds: Double): Int = (seconds * framesPerSecond).roundToIntOrZero()

    fun secondsToPixels(seconds: Double): Int =
        (zoomFactor * seconds * framesPerSecond).roundToIntOrZero()

    fun pixelsToSeconds(pixels: Int): Double = if (zoomFactor ==
        0.0
    ) {
        0.0
    } else {
        pixels / (framesPerSecond * zoomFactor)
    }

    fun millisecsToPixels(msecs: Int): Int = secondsToPixels(msecs / MILLIS_PER_SECOND)

    fun pixelsToMillisecs(pixels: Int): Int =
        (pixelsToSeconds(pixels) * MILLIS_PER_SECOND).roundToIntOrZero()

    private fun Double.roundToIntOrZero(): Int = if (isNaN()) 0 else roundToInt()

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
    }
}
