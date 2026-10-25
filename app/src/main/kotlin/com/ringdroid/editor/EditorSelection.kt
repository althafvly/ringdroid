package com.ringdroid.editor

/**
 * The editor's selection and scroll state, in waveform pixels at the current zoom level: the
 * start and end markers, the scroll offset with its animation goal and fling velocity, and
 * marker / waveform dragging.
 */
internal class EditorSelection {
    /** Length of the waveform; positions are kept within 0..maxPos. */
    var maxPos = 0

    /** Width of the visible waveform. */
    var width = 0
    var startPos = 0
    var endPos = 0
    var offset = 0
        private set
    var offsetGoal = 0
        private set
    var flingVelocity = 0
        private set
    var touchDragging = false
        private set
    var keyDown = false
        private set

    private var touchStart = 0f
    private var touchInitialOffset = 0
    private var touchInitialStartPos = 0
    private var touchInitialEndPos = 0

    /** Whether the scroll position is still moving and needs another frame. */
    val isAnimating: Boolean
        get() = (offsetGoal != offset && !keyDown) || flingVelocity != 0

    fun trap(pos: Int): Int = pos.coerceIn(0, maxOf(maxPos, 0))

    /** Starts over at the beginning with the markers at [start] and [end]. */
    fun reset(
        start: Int,
        end: Int,
    ) {
        touchDragging = false
        offset = 0
        offsetGoal = 0
        flingVelocity = 0
        startPos = start
        endPos = minOf(end, maxPos)
    }

    /** Takes over the positions after the waveform view rescaled them for a new zoom level. */
    fun syncZoom(
        start: Int,
        end: Int,
        newMaxPos: Int,
        newOffset: Int,
    ) {
        startPos = start
        endPos = end
        maxPos = newMaxPos
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
        if (flingVelocity != 0) {
            offset += flingVelocity / 30
            flingVelocity =
                when {
                    flingVelocity > 80 -> flingVelocity - 80
                    flingVelocity < -80 -> flingVelocity + 80
                    else -> 0
                }
            if (offset + width / 2 > maxPos) {
                offset = maxPos - width / 2
                flingVelocity = 0
            }
            if (offset < 0) {
                offset = 0
                flingVelocity = 0
            }
            offsetGoal = offset
        } else {
            // Ease towards the goal: a tenth of the distance, at least one pixel.
            val delta = offsetGoal - offset
            offset +=
                when {
                    delta > 10 || delta < -10 -> delta / 10
                    delta > 0 -> 1
                    delta < 0 -> -1
                    else -> 0
                }
        }
    }

    // Dragging the waveform scrolls it.

    fun waveformTouchStart(x: Float) {
        touchDragging = true
        touchStart = x
        touchInitialOffset = offset
        flingVelocity = 0
    }

    fun waveformTouchMove(x: Float) {
        offset = trap((touchInitialOffset + (touchStart - x)).toInt())
    }

    /** Ends a drag and returns the waveform position that was first touched. */
    fun waveformTouchEnd(): Int {
        touchDragging = false
        offsetGoal = offset
        return (touchStart + offset).toInt()
    }

    fun fling(velocityX: Float) {
        touchDragging = false
        offsetGoal = offset
        flingVelocity = (-velocityX).toInt()
    }

    // Dragging a marker: the start marker moves the whole selection, the end marker resizes it.

    fun markerTouchStart(x: Float) {
        touchDragging = true
        touchStart = x
        touchInitialStartPos = startPos
        touchInitialEndPos = endPos
    }

    fun markerTouchMove(
        isStart: Boolean,
        x: Float,
    ) {
        val delta = x - touchStart
        if (isStart) {
            startPos = trap((touchInitialStartPos + delta).toInt())
            endPos = trap((touchInitialEndPos + delta).toInt())
        } else {
            endPos = maxOf(trap((touchInitialEndPos + delta).toInt()), startPos)
        }
    }

    fun markerTouchEnd(isStart: Boolean) {
        touchDragging = false
        scrollToCenter(if (isStart) startPos else endPos)
    }

    // Arrow keys nudge a focused marker by [velocity] pixels.

    fun nudgeLeft(
        isStart: Boolean,
        velocity: Int,
    ) {
        keyDown = true
        if (isStart) {
            val saveStart = startPos
            startPos = trap(startPos - velocity)
            endPos = trap(endPos - (saveStart - startPos))
            scrollToCenter(startPos)
        } else {
            if (endPos == startPos) {
                startPos = trap(startPos - velocity)
                endPos = startPos
            } else {
                endPos = trap(endPos - velocity)
            }
            scrollToCenter(endPos)
        }
    }

    fun nudgeRight(
        isStart: Boolean,
        velocity: Int,
    ) {
        keyDown = true
        if (isStart) {
            val saveStart = startPos
            startPos = minOf(startPos + velocity, maxPos)
            endPos = minOf(endPos + (startPos - saveStart), maxPos)
            scrollToCenter(startPos)
        } else {
            endPos = minOf(endPos + velocity, maxPos)
            scrollToCenter(endPos)
        }
    }

    fun keyUp() {
        keyDown = false
    }

    fun focusMarker(isStart: Boolean) {
        keyDown = false
        scrollToCenter(if (isStart) startPos else endPos)
    }
}
