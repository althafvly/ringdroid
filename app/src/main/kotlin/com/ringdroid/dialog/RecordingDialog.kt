package com.ringdroid.dialog

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import com.ringdroid.R
import com.ringdroid.databinding.RecordAudioBinding
import com.ringdroid.util.createDialog

/** Dialog shown while recording, with an elapsed-time counter and stop / cancel buttons. */
internal class RecordingDialog(
    context: Context,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    private val binding = RecordAudioBinding.inflate(LayoutInflater.from(context))
    private val dialog: AlertDialog

    init {
        dialog =
            createDialog(context, binding.root).apply { setOnCancelListener { onCancel() } }
        val pulse = ObjectAnimator.ofFloat(binding.recordingDot, "alpha", 1.0f, 0.2f)
        pulse.duration = 750
        pulse.repeatCount = ValueAnimator.INFINITE
        pulse.repeatMode = ValueAnimator.REVERSE
        dialog.setOnShowListener { pulse.start() }
        dialog.setOnDismissListener { pulse.cancel() }
        binding.recordCancel.setOnClickListener {
            onCancel()
            dialog.dismiss()
        }
        binding.recordStop.setOnClickListener {
            onStop()
            dialog.dismiss()
        }
    }

    fun show() {
        dialog.show()
    }

    /** Safe to call from any thread. */
    fun dismiss() {
        dialog.dismiss()
    }

    fun setElapsedSeconds(seconds: Double) {
        val min = (seconds / 60).toInt()
        binding.recordAudioTimer.text = binding.root.context.getString(R.string.recording_time, min, (seconds - 60 * min).toFloat())
    }
}
