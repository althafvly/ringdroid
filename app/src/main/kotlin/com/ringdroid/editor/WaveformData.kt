package com.ringdroid.editor

/** Normalized waveform heights (0..1) for each zoom level, computed from per-frame gains. */
internal class WaveformData(
    frameGains: IntArray,
) {
    private val values = arrayOfNulls<DoubleArray>(NUM_ZOOM_LEVELS)
    private val zoomFactors = DoubleArray(NUM_ZOOM_LEVELS)

    val initialZoomLevel: Int

    init {
        val numFrames = frameGains.size
        val smoothedGains = DoubleArray(numFrames)
        if (numFrames <= 2) {
            for (i in 0 until numFrames) {
                smoothedGains[i] = frameGains[i].toDouble()
            }
        } else {
            smoothedGains[0] = frameGains[0] / 2.0 + frameGains[1] / 2.0
            for (i in 1 until numFrames - 1) {
                smoothedGains[i] = frameGains[i - 1] / 3.0 + frameGains[i] / 3.0 + frameGains[i + 1] / 3.0
            }
            smoothedGains[numFrames - 1] = frameGains[numFrames - 2] / 2.0 + frameGains[numFrames - 1] / 2.0
        }

        // Scale so the range is no more than 0 - 255.
        val peak = maxOf(1.0, smoothedGains.maxOrNull() ?: 0.0)
        val scaleFactor = if (peak > 255.0) 255 / peak else 1.0

        // Histogram of 256 bins, used to clip the quietest 5% and loudest 1%.
        var maxGain = 0
        val gainHist = IntArray(256)
        for (gain in smoothedGains) {
            val bin = (gain * scaleFactor).toInt().coerceIn(0, 255)
            maxGain = maxOf(maxGain, bin)
            gainHist[bin]++
        }
        var minGain = 0
        var sum = 0
        while (minGain < 255 && sum < numFrames / 20) {
            sum += gainHist[minGain]
            minGain++
        }
        sum = 0
        while (maxGain > 2 && sum < numFrames / 100) {
            sum += gainHist[maxGain]
            maxGain--
        }

        val range = (maxGain - minGain).toDouble()
        val heights =
            DoubleArray(numFrames) { i ->
                val scaledGain = smoothedGains[i] * scaleFactor
                // With no range (constant level) draw sound at full height and silence flat.
                val value =
                    if (range > 0) {
                        (scaledGain - minGain) / range
                    } else if (scaledGain > 0) {
                        1.0
                    } else {
                        0.0
                    }
                val clamped = value.coerceIn(0.0, 1.0)
                clamped * clamped
            }

        // Level 0 is doubled, with interpolated values.
        val doubled = DoubleArray(numFrames * 2)
        for (i in 0 until numFrames) {
            doubled[2 * i] = if (i == 0) 0.5 * heights[0] else 0.5 * (heights[i - 1] + heights[i])
            doubled[2 * i + 1] = heights[i]
        }
        values[0] = doubled
        zoomFactors[0] = 2.0
        // Level 1 is one value per frame.
        values[1] = heights
        zoomFactors[1] = 1.0
        // The remaining levels are each halved.
        for (level in 2 until NUM_ZOOM_LEVELS) {
            val previous = values[level - 1]!!
            values[level] = DoubleArray(previous.size / 2) { i -> 0.5 * (previous[2 * i] + previous[2 * i + 1]) }
            zoomFactors[level] = zoomFactors[level - 1] / 2.0
        }

        initialZoomLevel =
            when {
                numFrames > 5000 -> 3
                numFrames > 1000 -> 2
                numFrames > 300 -> 1
                else -> 0
            }
    }

    fun getLength(zoomLevel: Int): Int = values[zoomLevel]!!.size

    fun getValues(zoomLevel: Int): DoubleArray = values[zoomLevel]!!

    fun getZoomFactor(zoomLevel: Int): Double = zoomFactors[zoomLevel]

    companion object {
        const val NUM_ZOOM_LEVELS = 5
    }
}
