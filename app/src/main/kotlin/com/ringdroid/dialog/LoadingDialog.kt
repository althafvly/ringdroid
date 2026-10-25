package com.ringdroid.dialog

import android.app.AlertDialog
import android.content.Context
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import com.ringdroid.R
import com.ringdroid.databinding.DialogProgressHorizontalBinding
import com.ringdroid.util.RingdroidUtils

/** Cancelable progress dialog shown while a sound file is decoded. */
internal class LoadingDialog(
    context: Context,
    onCancel: () -> Unit,
) {
    private val binding = DialogProgressHorizontalBinding.inflate(LayoutInflater.from(context))
    private val dialog: AlertDialog

    @Volatile private var lastUpdateTime = 0L

    init {
        binding.progressBar.max = 100
        binding.loadingSubtitle.isVisible = false
        dialog =
            AlertDialog
                .Builder(context)
                .setView(binding.root)
                .setCancelable(true)
                .setOnCancelListener { onCancel() }
                .create()
        RingdroidUtils.setTransparentBackground(dialog)
        dialog.setCanceledOnTouchOutside(false)
        binding.btnCancel.setOnClickListener {
            onCancel()
            dialog.dismiss()
        }
    }

    /** Safe to call from any thread. */
    fun setSubtitle(subtitle: String?) {
        binding.root.post {
            binding.loadingSubtitle.text = subtitle
            binding.loadingSubtitle.isVisible = !subtitle.isNullOrEmpty()
        }
    }

    fun show() {
        dialog.show()
    }

    /** Safe to call from any thread. */
    fun dismiss() {
        dialog.dismiss()
    }

    /** Called from the decoding thread; updates are throttled to every 100 ms. */
    fun setProgress(fraction: Double) {
        val now = SystemClock.uptimeMillis()
        if (now - lastUpdateTime <= 100) {
            return
        }
        lastUpdateTime = now
        val progress = (fraction * 100).toInt()
        binding.root.post {
            binding.progressBar.progress = progress
            binding.progressPercentageText.text = binding.root.context.getString(R.string.progress_percentage, progress)
        }
    }
}
