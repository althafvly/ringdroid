package com.ringdroid.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSelectionTest {
    private fun selection() =
        EditorSelection().apply {
            maxPos = 1000
            width = 200
            reset(100, 300)
        }

    @Test
    fun resetClampsTheEndToTheLength() {
        val s = EditorSelection().apply { maxPos = 150 }
        s.reset(10, 400)
        assertEquals(10, s.startPos)
        assertEquals(150, s.endPos)
        assertEquals(0, s.offset)
    }

    @Test
    fun trapKeepsPositionsInRange() {
        val s = selection()
        assertEquals(0, s.trap(-5))
        assertEquals(1000, s.trap(5000))
        assertEquals(42, s.trap(42))
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
        assertTrue(s.keyDown)
        s.keyUp()
        assertFalse(s.keyDown)
    }

    @Test
    fun scrollingEasesTowardsTheCenteredGoal() {
        val s = selection()
        s.scrollToCenter(600)
        assertEquals(500, s.offsetGoal) // 600 - width / 2
        assertTrue(s.isAnimating)
        s.step()
        assertEquals(50, s.offset) // A tenth of the distance.
        repeat(200) { s.step() }
        assertEquals(500, s.offset)
        assertFalse(s.isAnimating)
    }

    @Test
    fun scrollGoalStaysWithinTheWaveform() {
        val s = selection()
        s.scrollToCenter(10)
        assertEquals(0, s.offsetGoal)
        s.scrollToCenter(5000)
        assertEquals(900, s.offsetGoal) // maxPos - width / 2
    }

    @Test
    fun flingDecaysAndStopsAtTheEdge() {
        val s = selection()
        s.fling(-3000f)
        s.step()
        assertEquals(100, s.offset)
        assertEquals(2920, s.flingVelocity)
        repeat(100) { s.step() }
        assertEquals(900, s.offset)
        assertEquals(0, s.flingVelocity)
    }

    @Test
    fun draggingTheWaveformScrollsAndReportsTheTap() {
        val s = selection()
        s.waveformTouchStart(400f)
        s.waveformTouchMove(300f)
        assertEquals(100, s.offset)
        s.scrollToCenter(800) // Ignored while dragging.
        assertEquals(0, s.offsetGoal)
        assertEquals(500, s.waveformTouchEnd())
        assertEquals(100, s.offsetGoal)
    }
}
