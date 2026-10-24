package com.ringdroid.select;

import android.app.AlertDialog;
import android.app.RecoverableSecurityException;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import com.ringdroid.R;
import com.ringdroid.databinding.DialogConfirmDeleteBinding;
import com.ringdroid.util.RingdroidUtils;

import java.io.File;
import java.util.List;

/**
 * Deletes audio files, asking for the system's consent where Android requires it. Must be created
 * while the activity is being initialized.
 */
final class AudioDeleter {
    private static final String TAG = "AudioDeleter";
    private final ComponentActivity mActivity;
    private final Runnable mOnDeleted;
    private final ActivityResultLauncher<IntentSenderRequest> mConsentLauncher;
    private Uri mPendingUri;

    AudioDeleter(ComponentActivity activity, Runnable onDeleted) {
        mActivity = activity;
        mOnDeleted = onDeleted;
        mConsentLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                    Uri pending = mPendingUri;
                    mPendingUri = null;
                    if (result.getResultCode() != ComponentActivity.RESULT_OK) {
                        return;
                    }
                    if (pending != null) {
                        deleteFromMediaStore(pending);
                    } else {
                        mOnDeleted.run();
                    }
                });
    }

    void delete(Uri uri, String filePath, String title) {
        if (mActivity.isFinishing() || mActivity.isDestroyed()) {
            return;
        }
        // From Android 11 the system shows its own confirmation.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                mConsentLauncher.launch(new IntentSenderRequest.Builder(MediaStore
                        .createDeleteRequest(mActivity.getContentResolver(), List.of(uri)))
                        .build());
            } catch (IllegalArgumentException | SecurityException e) {
                Log.e(TAG, "Failed to request delete of " + uri, e);
                showFailed();
            }
            return;
        }

        DialogConfirmDeleteBinding binding = DialogConfirmDeleteBinding
                .inflate(mActivity.getLayoutInflater());
        if (title != null && !title.isEmpty()) {
            binding.deleteMessage
                    .setText(mActivity.getString(R.string.confirm_delete_message, title));
        }
        AlertDialog dialog = new AlertDialog.Builder(mActivity).setView(binding.getRoot())
                .setCancelable(true).create();
        RingdroidUtils.setTransparentBackground(dialog);
        binding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        binding.btnConfirmDelete.setOnClickListener(v -> {
            dialog.dismiss();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && filePath != null) {
                File file = new File(filePath);
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete " + file);
                }
            }
            deleteFromMediaStore(uri);
        });
        dialog.show();
    }

    private void deleteFromMediaStore(Uri uri) {
        try {
            if (mActivity.getContentResolver().delete(uri, null, null) == 0) {
                showFailed();
            } else {
                mOnDeleted.run();
            }
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
                    && e instanceof RecoverableSecurityException) {
                mPendingUri = uri;
                mConsentLauncher.launch(new IntentSenderRequest.Builder(
                        ((RecoverableSecurityException) e).getUserAction().getActionIntent())
                        .build());
            } else {
                showFailed();
            }
        } catch (IllegalArgumentException | IllegalStateException
                | UnsupportedOperationException e) {
            showFailed();
        }
    }

    private void showFailed() {
        Toast.makeText(mActivity, R.string.delete_failed, Toast.LENGTH_SHORT).show();
    }
}
