package com.ringdroid.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.SeekBar
import com.ringdroid.databinding.DialogAmplifyBinding
import com.ringdroid.util.RingdroidUtils
import java.util.Locale

/** Lets the user pick an output volume between 10% and the seek bar maximum. */
internal object AmplifyDialog {
    private const val MIN_PERCENT = 10

    fun show(
        context: Context,
        currentFactor: Float,
        onApply: (Float) -> Unit,
    ) {
        val binding = DialogAmplifyBinding.inflate(LayoutInflater.from(context))
        val current = maxOf(MIN_PERCENT, (currentFactor * 100).toInt())
        binding.amplifySeekbar.progress = current
        binding.amplifyPercentageText.text = formatPercent(current)

        val dialog =
            AlertDialog
                .Builder(context)
                .setView(binding.root)
                .setCancelable(true)
                .create()
        RingdroidUtils.setTransparentBackground(dialog)
        binding.amplifySeekbar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    if (progress < MIN_PERCENT) {
                        seekBar.progress = MIN_PERCENT
                    }
                    binding.amplifyPercentageText.text = formatPercent(maxOf(progress, MIN_PERCENT))
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            },
        )
        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnApply.setOnClickListener {
            onApply(binding.amplifySeekbar.progress / 100.0f)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun formatPercent(percent: Int) = "%d%%".format(Locale.getDefault(), percent)
}
