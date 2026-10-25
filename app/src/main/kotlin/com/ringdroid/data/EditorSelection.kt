package com.ringdroid.data

/**
 * The editor's start and end markers in waveform pixels at the current zoom level, and marker
 * dragging. Moving a marker scrolls it into view through [scroll].
 */
internal class EditorSelection {
    val scroll = EditorScroll()
    var startPos = 0
    var endPos = 0

    private var touchStart = 0f
    private var touchInitialStartPos = 0
    private var touchInitialEndPos = 0

    /** Starts over at the beginning with the markers at [start] and [end]. */
    fun reset(start: Int, end: Int) {
        scroll.fling(0f)
        scroll.jumpTo(0)
        startPos = start
        endPos = minOf(end, scroll.maxPos)
    }

    /** Takes over the positions after the waveform view rescaled them for a new zoom level. */
    fun syncZoom(start: Int, end: Int, newMaxPos: Int, newOffset: Int) {
        startPos = start
        endPos = end
        scroll.maxPos = newMaxPos
        scroll.jumpTo(newOffset)
    }

    // Dragging a marker: the start marker moves the whole selection, the end marker resizes it.

    fun markerTouchStart(x: Float) {
        scroll.touchDragging = true
        touchStart = x
        touchInitialStartPos = startPos
        touchInitialEndPos = endPos
    }

    fun markerTouchMove(isStart: Boolean, x: Float) {
        val delta = x - touchStart
        if (isStart) {
            startPos = scroll.trap((touchInitialStartPos + delta).toInt())
            endPos = scroll.trap((touchInitialEndPos + delta).toInt())
        } else {
            endPos = maxOf(scroll.trap((touchInitialEndPos + delta).toInt()), startPos)
        }
    }

    fun markerTouchEnd(isStart: Boolean) {
        scroll.touchDragging = false
        scroll.scrollToCenter(if (isStart) startPos else endPos)
    }

    // Arrow keys nudge a focused marker by [velocity] pixels.

    fun nudgeLeft(isStart: Boolean, velocity: Int) {
        scroll.keyDown = true
        if (isStart) {
            val saveStart = startPos
            startPos = scroll.trap(startPos - velocity)
            endPos = scroll.trap(endPos - (saveStart - startPos))
            scroll.scrollToCenter(startPos)
        } else {
            if (endPos == startPos) {
                startPos = scroll.trap(startPos - velocity)
                endPos = startPos
            } else {
                endPos = scroll.trap(endPos - velocity)
            }
            scroll.scrollToCenter(endPos)
        }
    }

    fun nudgeRight(isStart: Boolean, velocity: Int) {
        scroll.keyDown = true
        if (isStart) {
            val saveStart = startPos
            startPos = minOf(startPos + velocity, scroll.maxPos)
            endPos = minOf(endPos + (startPos - saveStart), scroll.maxPos)
            scroll.scrollToCenter(startPos)
        } else {
            endPos = minOf(endPos + velocity, scroll.maxPos)
            scroll.scrollToCenter(endPos)
        }
    }

    fun focusMarker(isStart: Boolean) {
        scroll.keyDown = false
        scroll.scrollToCenter(if (isStart) startPos else endPos)
    }
}
