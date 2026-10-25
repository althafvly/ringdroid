package com.ringdroid.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.ringdroid.R
import com.ringdroid.databinding.DialogProgressHorizontalBinding
import com.ringdroid.util.createDialog

/** Cancelable progress dialog shown while a sound file is decoded. */
internal class LoadingDialog(context: Context, onCancel: () -> Unit) {
    private val binding = DialogProgressHorizontalBinding.inflate(LayoutInflater.from(context))
    private val dialog: AlertDialog

    init {
        binding.progressBar.max = PROGRESS_MAX
        binding.loadingSubtitle.isVisible = false
        dialog =
            createDialog(context, binding.root).apply { setOnCancelListener { onCancel() } }
        dialog.setCanceledOnTouchOutside(false)
        binding.btnCancel.setOnClickListener {
            onCancel()
            dialog.dismiss()
        }
    }

    fun setSubtitle(subtitle: String?) {
        binding.loadingSubtitle.text = subtitle
        binding.loadingSubtitle.isVisible = !subtitle.isNullOrEmpty()
    }

    fun show() {
        dialog.show()
    }

    /** Safe to call from any thread. */
    fun dismiss() {
        dialog.dismiss()
    }

    fun setProgress(fraction: Double) {
        val progress = (fraction * PROGRESS_MAX).toInt()
        binding.progressBar.progress = progress
        binding.progressPercentageText.text =
            binding.root.context.getString(R.string.progress_percentage, progress)
    }

    private companion object {
        const val PROGRESS_MAX = 100
    }
}
