package com.ringdroid;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

public class StoragePermissionUtils {

    public static boolean hasExternalStoragePermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
            return Environment.isExternalStorageManager();
        }

        return false;
    }

    public static void requestExternalStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        }
    }
}
