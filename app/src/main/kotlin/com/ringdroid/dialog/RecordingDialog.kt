package com.ringdroid.dialog

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.os.SystemClock
import android.view.LayoutInflater
import com.ringdroid.databinding.RecordAudioBinding
import com.ringdroid.util.RingdroidUtils
import java.util.Locale

/** Dialog shown while recording, with an elapsed-time counter and stop / cancel buttons. */
internal class RecordingDialog(
    context: Context,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    private val binding = RecordAudioBinding.inflate(LayoutInflater.from(context))
    private val dialog: AlertDialog

    @Volatile private var lastUpdateTime = 0L

    init {
        dialog =
            AlertDialog
                .Builder(context)
                .setView(binding.root)
                .setCancelable(true)
                .setOnCancelListener { onCancel() }
                .create()
        RingdroidUtils.setTransparentBackground(dialog)
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

    /** Called from the recording thread; updates are throttled to every 50 ms. */
    fun setElapsedSeconds(seconds: Double) {
        val now = SystemClock.uptimeMillis()
        if (now - lastUpdateTime <= 50) {
            return
        }
        lastUpdateTime = now
        val min = (seconds / 60).toInt()
        val sec = (seconds - 60 * min).toFloat()
        val text = "%d:%05.2f".format(Locale.getDefault(), min, sec)
        binding.recordAudioTimer.post { binding.recordAudioTimer.text = text }
    }
}
