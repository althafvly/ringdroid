package com.ringdroid.data

/**
 * The editor's scroll position in waveform pixels, with its animation goal, fling velocity and
 * waveform dragging.
 */
internal class EditorScroll {
    /** Length of the waveform; positions are kept within 0..maxPos. */
    var maxPos = 0

    /** Width of the visible waveform. */
    var width = 0
    var offset = 0
        private set
    var offsetGoal = 0
        private set
    var flingVelocity = 0
        private set
    var touchDragging = false
    var keyDown = false

    private var touchStart = 0f
    private var touchInitialOffset = 0

    /** Whether the scroll position is still moving and needs another frame. */
    val isAnimating: Boolean
        get() = (offsetGoal != offset && !keyDown) || flingVelocity != 0

    fun trap(pos: Int): Int = pos.coerceIn(0, maxOf(maxPos, 0))

    fun jumpTo(newOffset: Int) {
        offset = newOffset
        offsetGoal = newOffset
    }

    /** Scrolls smoothly so that [pos] ends up in the middle, unless the user is dragging. */
    fun scrollToCenter(pos: Int) {
        if (!touchDragging) {
            offsetGoal = (pos - width / 2).coerceAtMost(maxPos - width / 2).coerceAtLeast(0)
        }
    }

    fun scrollToStart() {
        offsetGoal = 0
    }

    /** Advances the fling or the eased scroll towards [offsetGoal] by one frame. */
    fun step() {
        if (touchDragging) {
            return
        }
        if (flingVelocity == 0) {
            // Ease towards the goal: a tenth of the distance, at least one pixel.
            val delta = offsetGoal - offset
            offset += if (delta > EASE_DIVISOR || delta < -EASE_DIVISOR) delta / EASE_DIVISOR else delta.coerceIn(-1, 1)
            return
        }
        offset += flingVelocity / FLING_DIVISOR
        flingVelocity =
            when {
                flingVelocity > FLING_DECAY -> flingVelocity - FLING_DECAY
                flingVelocity < -FLING_DECAY -> flingVelocity + FLING_DECAY
                else -> 0
            }
        if (offset + width / 2 > maxPos || offset < 0) {
            offset = offset.coerceAtMost(maxPos - width / 2).coerceAtLeast(0)
            flingVelocity = 0
        }
        offsetGoal = offset
    }

    fun touchStart(x: Float) {
        touchDragging = true
        touchStart = x
        touchInitialOffset = offset
        flingVelocity = 0
    }

    fun touchMove(x: Float) {
        offset = trap((touchInitialOffset + (touchStart - x)).toInt())
    }

    /** Ends a drag and returns the waveform position that was first touched. */
    fun touchEnd(): Int {
        touchDragging = false
        offsetGoal = offset
        return (touchStart + offset).toInt()
    }

    fun fling(velocityX: Float) {
        touchDragging = false
        offsetGoal = offset
        flingVelocity = (-velocityX).toInt()
    }

    private companion object {
        const val EASE_DIVISOR = 10
        const val FLING_DIVISOR = 30
        const val FLING_DECAY = 80
    }
}
