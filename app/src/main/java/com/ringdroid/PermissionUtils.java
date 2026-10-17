package com.ringdroid;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public final class PermissionUtils {
    private PermissionUtils() {
    }

    public static String[] getContactPermissions() {
        return new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS};
    }

    public static String getMicPermission() {
        return Manifest.permission.RECORD_AUDIO;
    }

    public static String getStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            return Manifest.permission.WRITE_EXTERNAL_STORAGE;
        }
        return Manifest.permission.READ_MEDIA_AUDIO;
    }

    public static boolean hasContactPermissions(Context context) {
        for (String permission : getContactPermissions()) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasMicPermissions(Context context) {
        return context.checkSelfPermission(getMicPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasStoragePermission(Context context) {
        return context
                .checkSelfPermission(getStoragePermission()) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasWriteSettingsPermission(Context context) {
        return Settings.System.canWrite(context);
    }

    public static void requestWriteSettingsPermission(Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        context.startActivity(intent);
    }
}
