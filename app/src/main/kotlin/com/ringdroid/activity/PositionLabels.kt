package com.ringdroid.activity

import android.os.Handler
import android.text.Editable
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.ringdroid.R
import com.ringdroid.data.EditorSelection
import com.ringdroid.databinding.EditorBinding
import com.ringdroid.view.MarkerView
import java.util.Locale

/**
 * Shows the start and end times in their fields and the markers' descriptions, and moves a marker
 * when its time is typed. Updating an EditText is slow, so the fields only change on a timer.
 */
internal class PositionLabels(
    private val binding: EditorBinding,
    private val selection: EditorSelection,
    private val handler: Handler,
    private val onChanged: () -> Unit,
) {
    private var shownStart = -1
    private var shownEnd = -1
    private val refresh: Runnable =
        object : Runnable {
            override fun run() {
                shownStart =
                    show(selection.startPos, shownStart, binding.starttext, binding.startmarker, R.string.start_marker)
                shownEnd = show(selection.endPos, shownEnd, binding.endtext, binding.endmarker, R.string.end_marker)
                handler.postDelayed(this, REFRESH_INTERVAL_MS)
            }
        }

    init {
        binding.starttext.doAfterTextChanged { onTyped(it, binding.starttext, isStart = true) }
        binding.endtext.doAfterTextChanged { onTyped(it, binding.endtext, isStart = false) }
    }

    fun start() {
        handler.postDelayed(refresh, REFRESH_INTERVAL_MS)
    }

    /** Shows both positions again on the next refresh, for example for a new sound. */
    fun invalidate() {
        shownStart = -1
        shownEnd = -1
    }

    fun format(pixels: Int): String =
        if (binding.waveform.isInitialized) {
            "%.2f".format(Locale.US, binding.waveform.scale.pixelsToSeconds(pixels))
        } else {
            ""
        }

    private fun onTyped(
        text: Editable?,
        field: TextView,
        isStart: Boolean,
    ) {
        val waveform = binding.waveform
        val seconds = text?.toString()?.toDoubleOrNull()
        if (!waveform.isInitialized || seconds == null || !field.hasFocus()) {
            return
        }
        val pos = selection.scroll.trap(waveform.scale.secondsToPixels(seconds))
        if (isStart) {
            selection.startPos = pos
        } else {
            selection.endPos = pos
        }
        onChanged()
    }

    // Returns the position now shown; a field being edited keeps its text.
    private fun show(
        pos: Int,
        shown: Int,
        field: TextView,
        marker: MarkerView,
        labelRes: Int,
    ): Int {
        if (pos == shown || field.hasFocus()) {
            return shown
        }
        val time = format(pos)
        field.text = time
        marker.contentDescription = field.context.getString(labelRes) + " " + time
        return pos
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 100L
    }
}
