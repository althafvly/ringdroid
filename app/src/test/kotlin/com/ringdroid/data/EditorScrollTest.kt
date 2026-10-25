package com.ringdroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorScrollTest {
    private fun scroll() =
        EditorScroll().apply {
            maxPos = 1000
            width = 200
        }

    @Test
    fun trapKeepsPositionsInRange() {
        val s = scroll()
        assertEquals(0, s.trap(-5))
        assertEquals(1000, s.trap(5000))
        assertEquals(42, s.trap(42))
    }

    @Test
    fun scrollingEasesTowardsTheCenteredGoal() {
        val s = scroll()
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
        val s = scroll()
        s.scrollToCenter(10)
        assertEquals(0, s.offsetGoal)
        s.scrollToCenter(5000)
        assertEquals(900, s.offsetGoal) // maxPos - width / 2
    }

    @Test
    fun flingDecaysAndStopsAtTheEdge() {
        val s = scroll()
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
        val s = scroll()
        s.touchStart(400f)
        s.touchMove(300f)
        assertEquals(100, s.offset)
        s.scrollToCenter(800) // Ignored while dragging.
        assertEquals(0, s.offsetGoal)
        assertEquals(500, s.touchEnd())
        assertEquals(100, s.offsetGoal)
    }
}
