package com.ringdroid.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.ImageView
import kotlin.math.sqrt

/**
 * A start or end marker in the waveform editor. It passes touch, focus and key events to its
 * listener, and accelerates while the left or right arrow is held.
 */
class MarkerView(context: Context, attrs: AttributeSet?) : ImageView(context, attrs) {
    interface MarkerListener {
        fun markerTouchStart(marker: MarkerView, pos: Float)

        fun markerTouchMove(marker: MarkerView, pos: Float)

        fun markerTouchEnd(marker: MarkerView)

        fun markerFocus(marker: MarkerView)

        fun markerLeft(marker: MarkerView, velocity: Int)

        fun markerRight(marker: MarkerView, velocity: Int)

        fun markerKeyUp()
    }

    private var velocity = 0
    var listener: MarkerListener? = null

    init {
        isFocusable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Raw x, because this view moves while it is dragged.
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                listener?.markerTouchStart(this, event.rawX)
            }

            MotionEvent.ACTION_MOVE -> {
                listener?.markerTouchMove(this, event.rawX)
            }

            MotionEvent.ACTION_UP -> {
                listener?.markerTouchEnd(this)
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if (gainFocus) {
            listener?.markerFocus(this)
        }
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        velocity++
        val step = sqrt(1 + velocity / 2.0).toInt()
        val l = listener
        val handled =
            l != null &&
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        l.markerLeft(this, step)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        l.markerRight(this, step)
                        true
                    }

                    else -> {
                        keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    }
                }
        return handled || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        velocity = 0
        listener?.markerKeyUp()
        return super.onKeyUp(keyCode, event)
    }
}
