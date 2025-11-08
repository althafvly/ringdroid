package com.ringdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class PermissionSplashActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Now handle permission flow reliably
        if (!arePermissionsGranted()) {
            requestBasePermissions();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !android.os.Environment.isExternalStorageManager()) {
            requestAllFilesAccess();
            return;
        }

        if (!Settings.System.canWrite(this)) {
            requestWriteSettingsPermission();
            return;
        }

        startMainActivity();
    }

    private boolean arePermissionsGranted() {
        String[] perms = new String[]{
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.WRITE_CONTACTS,
                android.Manifest.permission.RECORD_AUDIO
        };

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Android 26–29 still requires this
            perms = new String[]{
                    android.Manifest.permission.READ_CONTACTS,
                    android.Manifest.permission.WRITE_CONTACTS,
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }

        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    private void requestBasePermissions() {
        requestPermissions(new String[]{
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.WRITE_CONTACTS,
                android.Manifest.permission.RECORD_AUDIO,
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                        ? android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        : android.Manifest.permission.READ_MEDIA_AUDIO)
        }, PERMISSION_REQUEST_CODE);
    }

    private void requestAllFilesAccess() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        Toast.makeText(this, R.string.allow_file_access, Toast.LENGTH_LONG).show();
        startActivity(intent);
    }

    private void requestWriteSettingsPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        Toast.makeText(this, R.string.allow_modify_system_settings, Toast.LENGTH_LONG).show();
        startActivity(intent);
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, RingdroidSelectActivity.class);
        startActivity(intent);
        finish();
    }
}
