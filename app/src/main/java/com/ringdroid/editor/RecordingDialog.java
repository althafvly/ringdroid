package com.ringdroid.editor;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.widget.TextView;

import com.ringdroid.databinding.RecordAudioBinding;
import com.ringdroid.util.RingdroidUtils;

import java.util.Locale;

/** Dialog shown while recording, with an elapsed-time counter and stop / cancel buttons. */
final class RecordingDialog {
    private final AlertDialog mDialog;
    private final TextView mTimer;
    private volatile long mLastUpdateTime;

    RecordingDialog(Context context, Runnable onStop, Runnable onCancel) {
        RecordAudioBinding binding = RecordAudioBinding.inflate(LayoutInflater.from(context));
        mDialog = new AlertDialog.Builder(context).setView(binding.getRoot()).setCancelable(true)
                .setOnCancelListener(dialog -> onCancel.run()).create();
        RingdroidUtils.setTransparentBackground(mDialog);

        ObjectAnimator pulse = ObjectAnimator.ofFloat(binding.recordingDot, "alpha", 1.0f, 0.2f);
        pulse.setDuration(750);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        mDialog.setOnShowListener(dialog -> pulse.start());
        mDialog.setOnDismissListener(dialog -> pulse.cancel());

        binding.recordCancel.setOnClickListener(v -> {
            onCancel.run();
            mDialog.dismiss();
        });
        binding.recordStop.setOnClickListener(v -> {
            onStop.run();
            mDialog.dismiss();
        });
        mTimer = binding.recordAudioTimer;
    }

    void show() {
        mDialog.show();
    }

    /** Safe to call from any thread. */
    void dismiss() {
        mDialog.dismiss();
    }

    /** Called from the recording thread; updates are throttled to every 50 ms. */
    void setElapsedSeconds(double seconds) {
        long now = SystemClock.uptimeMillis();
        if (now - mLastUpdateTime <= 50) {
            return;
        }
        mLastUpdateTime = now;
        int min = (int) (seconds / 60);
        float sec = (float) (seconds - 60 * min);
        String text = String.format(Locale.getDefault(), "%d:%05.2f", min, sec);
        mTimer.post(() -> mTimer.setText(text));
    }
}
