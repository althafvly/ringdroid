package com.ringdroid.editor;

import android.app.AlertDialog;
import android.content.Context;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;

import com.ringdroid.R;
import com.ringdroid.databinding.DialogProgressHorizontalBinding;
import com.ringdroid.util.RingdroidUtils;

/** Cancelable progress dialog shown while a sound file is decoded. */
final class LoadingDialog {
    private final AlertDialog mDialog;
    private final DialogProgressHorizontalBinding mBinding;
    private volatile long mLastUpdateTime;

    LoadingDialog(Context context, String subtitle, Runnable onCancel) {
        mBinding = DialogProgressHorizontalBinding.inflate(LayoutInflater.from(context));
        mBinding.progressBar.setMax(100);
        if (subtitle != null && !subtitle.isEmpty()) {
            mBinding.loadingSubtitle.setText(subtitle);
            mBinding.loadingSubtitle.setVisibility(View.VISIBLE);
        } else {
            mBinding.loadingSubtitle.setVisibility(View.GONE);
        }
        mDialog = new AlertDialog.Builder(context).setView(mBinding.getRoot()).setCancelable(true)
                .setOnCancelListener(dialog -> onCancel.run()).create();
        RingdroidUtils.setTransparentBackground(mDialog);
        mDialog.setCanceledOnTouchOutside(false);
        mBinding.btnCancel.setOnClickListener(v -> {
            onCancel.run();
            mDialog.dismiss();
        });
    }

    void show() {
        mDialog.show();
    }

    /** Safe to call from any thread. */
    void dismiss() {
        mDialog.dismiss();
    }

    /** Called from the decoding thread; updates are throttled to every 100 ms. */
    void setProgress(double fraction) {
        long now = SystemClock.uptimeMillis();
        if (now - mLastUpdateTime <= 100) {
            return;
        }
        mLastUpdateTime = now;
        int progress = (int) (fraction * 100);
        mBinding.getRoot().post(() -> {
            mBinding.progressBar.setProgress(progress);
            mBinding.progressPercentageText.setText(mBinding.getRoot().getContext()
                    .getString(R.string.progress_percentage, progress));
        });
    }
}
