package com.ringdroid.editor;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.SeekBar;

import com.ringdroid.databinding.DialogAmplifyBinding;
import com.ringdroid.util.RingdroidUtils;

import java.util.Locale;
import java.util.function.Consumer;

/** Lets the user pick an output volume between 10% and the seek bar maximum. */
final class AmplifyDialog {
    private static final int MIN_PERCENT = 10;

    private AmplifyDialog() {
    }

    static void show(Context context, float currentFactor, Consumer<Float> onApply) {
        DialogAmplifyBinding binding = DialogAmplifyBinding.inflate(LayoutInflater.from(context));
        int current = Math.max(MIN_PERCENT, (int) (currentFactor * 100));
        binding.amplifySeekbar.setProgress(current);
        binding.amplifyPercentageText.setText(formatPercent(current));

        AlertDialog dialog = new AlertDialog.Builder(context).setView(binding.getRoot())
                .setCancelable(true).create();
        RingdroidUtils.setTransparentBackground(dialog);

        binding.amplifySeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < MIN_PERCENT) {
                    seekBar.setProgress(MIN_PERCENT);
                    progress = MIN_PERCENT;
                }
                binding.amplifyPercentageText.setText(formatPercent(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        binding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        binding.btnApply.setOnClickListener(v -> {
            onApply.accept(binding.amplifySeekbar.getProgress() / 100.0f);
            dialog.dismiss();
        });
        dialog.show();
    }

    private static String formatPercent(int percent) {
        return String.format(Locale.getDefault(), "%d%%", percent);
    }
}
