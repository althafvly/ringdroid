package com.ringdroid;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            requestAllFilesAccess();
            return;
        }

        if (!Settings.System.canWrite(this)) {
            requestWriteSettingsPermission();
            return;
        }

        startMainActivity();
    }

    private String[] getRequiredPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.READ_CONTACTS);
        perms.add(Manifest.permission.WRITE_CONTACTS);
        perms.add(Manifest.permission.RECORD_AUDIO);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        return perms.toArray(new String[0]);
    }

    private boolean arePermissionsGranted() {
        for (String p : getRequiredPermissions()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    private void requestBasePermissions() {
        requestPermissions(getRequiredPermissions(), PERMISSION_REQUEST_CODE);
    }

    @TargetApi(Build.VERSION_CODES.R)
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
