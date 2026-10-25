package com.ringdroid.data

/** Normalized waveform heights (0..1) for each zoom level, computed from per-frame gains. */
internal class WaveformData(frameGains: IntArray) {
    private val values = ArrayList<DoubleArray>(NUM_ZOOM_LEVELS)

    val initialZoomLevel = INITIAL_ZOOM_THRESHOLDS.count { frameGains.size > it }

    init {
        val heights = heights(smooth(frameGains))
        // Level 0 is doubled, with interpolated values; level 1 is one value per frame.
        values.add(
            DoubleArray(heights.size * 2) { i ->
                val frame = i / 2
                when {
                    i % 2 == 1 -> heights[frame]
                    frame == 0 -> heights[0] / 2
                    else -> (heights[frame - 1] + heights[frame]) / 2
                }
            },
        )
        values.add(heights)
        // The remaining levels are each halved.
        while (values.size < NUM_ZOOM_LEVELS) {
            val previous = values.last()
            values.add(
                DoubleArray(previous.size / 2) { i ->
                    (previous[2 * i] + previous[2 * i + 1]) /
                        2
                },
            )
        }
    }

    fun getLength(zoomLevel: Int): Int = values[zoomLevel].size

    fun getValues(zoomLevel: Int): DoubleArray = values[zoomLevel]

    /** Pixels per frame: 2 at the most zoomed-in level, halving with each level. */
    fun getZoomFactor(zoomLevel: Int): Double = 2.0 / (1 shl zoomLevel)

    companion object {
        const val NUM_ZOOM_LEVELS = 5
        private const val MAX_GAIN = 255
        private const val QUIET_CLIP_FRACTION = 20
        private const val LOUD_CLIP_FRACTION = 100
        private const val NEIGHBORS = 3.0
        private val INITIAL_ZOOM_THRESHOLDS = intArrayOf(300, 1000, 5000)

        // Averages each gain with its neighbors.
        private fun smooth(gains: IntArray): DoubleArray {
            val n = gains.size
            if (n <= 2) {
                return DoubleArray(n) { gains[it].toDouble() }
            }
            return DoubleArray(n) { i ->
                when (i) {
                    0 -> {
                        gains[0] / 2.0 + gains[1] / 2.0
                    }

                    n - 1 -> {
                        gains[n - 2] / 2.0 + gains[n - 1] / 2.0
                    }

                    else -> {
                        gains[i - 1] / NEIGHBORS + gains[i] / NEIGHBORS +
                            gains[i + 1] / NEIGHBORS
                    }
                }
            }
        }

        // Scales the gains to 0..1, clipping the quietest 5% and loudest 1%, and squares them.
        private fun heights(smoothedGains: DoubleArray): DoubleArray {
            val numFrames = smoothedGains.size
            val peak = maxOf(1.0, smoothedGains.maxOrNull() ?: 0.0)
            val scaleFactor = if (peak > MAX_GAIN) MAX_GAIN / peak else 1.0

            var maxGain = 0
            val gainHist = IntArray(MAX_GAIN + 1)
            for (gain in smoothedGains) {
                val bin = (gain * scaleFactor).toInt().coerceIn(0, MAX_GAIN)
                maxGain = maxOf(maxGain, bin)
                gainHist[bin]++
            }
            var minGain = 0
            var sum = 0
            while (minGain < MAX_GAIN && sum < numFrames / QUIET_CLIP_FRACTION) {
                sum += gainHist[minGain]
                minGain++
            }
            sum = 0
            while (maxGain > 2 && sum < numFrames / LOUD_CLIP_FRACTION) {
                sum += gainHist[maxGain]
                maxGain--
            }

            val range = (maxGain - minGain).toDouble()
            return DoubleArray(numFrames) { i ->
                val scaledGain = smoothedGains[i] * scaleFactor
                // With no range (constant level) draw sound at full height and silence flat.
                val value =
                    when {
                        range > 0 -> (scaledGain - minGain) / range
                        scaledGain > 0 -> 1.0
                        else -> 0.0
                    }
                val clamped = value.coerceIn(0.0, 1.0)
                clamped * clamped
            }
        }
    }
}
