package com.ringdroid;

import android.app.Activity;

public class StoragePermissionUtils {

    public static boolean hasExternalStoragePermission() {
        // Disabled for Play flavor
        return false;
    }

    public static void requestExternalStoragePermission(Activity activity) {
        // Intentionally no-op
    }
}
