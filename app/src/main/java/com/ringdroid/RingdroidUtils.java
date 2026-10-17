package com.ringdroid;

import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class RingdroidUtils {
    private static final String TAG = "RingdroidUtils";

    public static void setDefaultRingTone(ComponentActivity activity, int type, Uri ringtoneUri,
            boolean shouldFinish) {
        if (!PermissionUtils.hasWriteSettingsPermission(activity)) {
            Toast.makeText(activity, R.string.required_system_modify_permission, Toast.LENGTH_SHORT)
                    .show();
            PermissionUtils.requestWriteSettingsPermission(activity);
            return;
        }

        try {
            RingtoneManager.setActualDefaultRingtoneUri(activity, type, ringtoneUri);
            if (type == RingtoneManager.TYPE_NOTIFICATION) {
                Toast.makeText(activity, R.string.default_notification_success_message,
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, R.string.default_ringtone_success_message,
                        Toast.LENGTH_SHORT).show();
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Unable to set default ringtone due to restriction", e);
            // On some Android versions/devices, setting ringtones may be restricted
            Toast.makeText(activity,
                    "Unable to set as default ringtone. Your device may not allow this.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set default ringtone", e);
            Toast.makeText(activity, "Error setting ringtone: " + e.getMessage(), Toast.LENGTH_LONG)
                    .show();
        }

        if (shouldFinish) {
            activity.finish();
        }
    }

    public static Uri getExternalAudioCollectionUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        }
        return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    }

    public static Uri getInternalAudioCollectionUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_INTERNAL);
        }
        return MediaStore.Audio.Media.INTERNAL_CONTENT_URI;
    }

    public static void applyStatusBarInsets(View toolbarContainer) {
        if (toolbarContainer == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(toolbarContainer, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), insets.top, v.getPaddingRight(), v.getPaddingBottom());
            return windowInsets;
        });
    }

    public static void applyNavigationBarInsets(View view, int extraPaddingBottomDp) {
        if (view == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            int extraPx = (int) (extraPaddingBottomDp
                    * v.getResources().getDisplayMetrics().density);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    insets.bottom + extraPx);
            return windowInsets;
        });
    }

    public static void showKeyboard(View view) {
        if (view == null) {
            return;
        }
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public static void hideKeyboard(View view) {
        if (view == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
