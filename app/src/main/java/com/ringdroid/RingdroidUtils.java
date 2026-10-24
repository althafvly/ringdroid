package com.ringdroid;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;

import android.content.Context;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RingdroidUtils {
    private static final String TAG = "RingdroidUtils";
    static final ExecutorService BACKGROUND = Executors.newCachedThreadPool();

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
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Failed to set default ringtone", e);
            Toast.makeText(activity, "Error setting ringtone: " + e.getMessage(), Toast.LENGTH_LONG)
                    .show();
        }

        if (shouldFinish) {
            activity.finish();
        }
    }

    public static void setTransparentBackground(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
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

    // Strips any path from a file name supplied by another app.
    static String toSafeFileName(String name, String fallback) {
        if (name == null) {
            return fallback;
        }
        String safeName = name.substring(name.lastIndexOf('/') + 1);
        return safeName.isEmpty() || safeName.equals(".") || safeName.equals("..")
                ? fallback
                : safeName;
    }

    public static File getFileFromUri(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }

        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            return path != null ? new File(path) : null;
        }

        // Try direct MediaStore _data if available
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{MediaStore.MediaColumns.DATA}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                if (idx >= 0) {
                    String path = cursor.getString(idx);
                    if (path != null) {
                        File file = new File(path);
                        if (file.exists() && file.canRead()) {
                            return file;
                        }
                    }
                }
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
        }

        // For all content:// URIs from third-party apps, stream into a cache file
        String fileName = "shared_audio";
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    fileName = toSafeFileName(name, fileName);
                }
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
        }

        File cacheFile = new File(context.getCacheDir(), fileName);
        try (InputStream in = context.getContentResolver().openInputStream(uri);
                OutputStream out = new FileOutputStream(cacheFile)) {
            if (in == null) {
                return null;
            }
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            return cacheFile;
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Failed to copy content URI to cache: " + uri, e);
            return null;
        }
    }
}
