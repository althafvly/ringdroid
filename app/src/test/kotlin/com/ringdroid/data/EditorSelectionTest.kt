package com.ringdroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSelectionTest {
    private fun selection() =
        EditorSelection().apply {
            scroll.maxPos = 1000
            scroll.width = 200
            reset(100, 300)
        }

    @Test
    fun resetClampsTheEndToTheLength() {
        val s = EditorSelection().apply { scroll.maxPos = 150 }
        s.reset(10, 400)
        assertEquals(10, s.startPos)
        assertEquals(150, s.endPos)
        assertEquals(0, s.scroll.offset)
    }

    @Test
    fun draggingTheStartMarkerMovesTheWholeSelection() {
        val s = selection()
        s.markerTouchStart(50f)
        s.markerTouchMove(isStart = true, x = 80f)
        assertEquals(130, s.startPos)
        assertEquals(330, s.endPos)
        s.markerTouchMove(isStart = true, x = 5000f)
        assertEquals(1000, s.endPos)
    }

    @Test
    fun theEndMarkerCannotPassTheStart() {
        val s = selection()
        s.markerTouchStart(0f)
        s.markerTouchMove(isStart = false, x = -500f)
        assertEquals(100, s.endPos)
        s.markerTouchMove(isStart = false, x = 50f)
        assertEquals(350, s.endPos)
    }

    @Test
    fun nudgingMovesMarkersAndClamps() {
        val s = selection()
        s.nudgeLeft(isStart = true, velocity = 30)
        assertEquals(70, s.startPos)
        assertEquals(270, s.endPos)
        s.nudgeRight(isStart = false, velocity = 2000)
        assertEquals(1000, s.endPos)
        s.nudgeLeft(isStart = true, velocity = 500)
        assertEquals(0, s.startPos)
        assertTrue(s.scroll.keyDown)
        s.focusMarker(isStart = true)
        assertFalse(s.scroll.keyDown)
    }
}
