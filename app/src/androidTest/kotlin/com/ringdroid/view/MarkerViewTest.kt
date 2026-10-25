package com.ringdroid.view

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkerViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun arrowKeysNudgeFasterWhileHeld() {
        onMain { marker, calls ->
            assertTrue(
                marker.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, key(KeyEvent.KEYCODE_DPAD_LEFT)),
            )
            repeat(HELD_REPEATS) {
                marker.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, key(KeyEvent.KEYCODE_DPAD_RIGHT))
            }
            marker.onKeyUp(
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT),
            )
            assertTrue(
                marker.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, key(KeyEvent.KEYCODE_DPAD_LEFT)),
            )
            assertEquals("left 1", calls.first())
            assertEquals("right 3", calls[HELD_REPEATS])
            assertEquals("keyUp", calls[HELD_REPEATS + 1])
            assertEquals("left 1", calls.last())
        }
    }

    @Test
    fun centerIsConsumedAndOtherKeysAreNot() {
        onMain { marker, calls ->
            assertTrue(
                marker.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, key(KeyEvent.KEYCODE_DPAD_CENTER)),
            )
            assertFalse(marker.onKeyDown(KeyEvent.KEYCODE_A, key(KeyEvent.KEYCODE_A)))
            assertTrue(calls.isEmpty())
        }
    }

    @Test
    fun touchesReportTheScreenPosition() {
        onMain { marker, calls ->
            for (action in intArrayOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
            )) {
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, action, TOUCH_X, 0f, 0)
                assertTrue(marker.onTouchEvent(event))
                event.recycle()
            }
            assertEquals(listOf("start", "move", "end"), calls.map { it.substringBefore(' ') })
        }
    }

    private fun key(code: Int) = KeyEvent(KeyEvent.ACTION_DOWN, code)

    private fun onMain(test: (MarkerView, MutableList<String>) -> Unit) {
        instrumentation.runOnMainSync {
            val calls = mutableListOf<String>()
            val marker = MarkerView(instrumentation.targetContext, null)
            marker.listener =
                object : MarkerView.MarkerListener {
                    override fun markerTouchStart(marker: MarkerView, pos: Float) {
                        calls.add("start $pos")
                    }

                    override fun markerTouchMove(marker: MarkerView, pos: Float) {
                        calls.add("move $pos")
                    }

                    override fun markerTouchEnd(marker: MarkerView) {
                        calls.add("end")
                    }

                    override fun markerFocus(marker: MarkerView) {
                        calls.add("focus")
                    }

                    override fun markerLeft(marker: MarkerView, velocity: Int) {
                        calls.add("left $velocity")
                    }

                    override fun markerRight(marker: MarkerView, velocity: Int) {
                        calls.add("right $velocity")
                    }

                    override fun markerKeyUp() {
                        calls.add("keyUp")
                    }
                }
            test(marker, calls)
        }
    }

    private companion object {
        const val HELD_REPEATS = 15
        const val TOUCH_X = 50f
    }
}
