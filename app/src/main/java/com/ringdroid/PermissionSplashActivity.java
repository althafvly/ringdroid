package com.ringdroid;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

public class PermissionSplashActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private boolean permissionsRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (arePermissionsGranted()) {
            startMainActivity();
        } else {
            if (!permissionsRequested) {
                requestPermissions();
                permissionsRequested = true;
            } else {
                exitApp();
            }
        }
    }

    private boolean arePermissionsGranted() {
        return checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        requestPermissions(new String[]{
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.WRITE_CONTACTS,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.RECORD_AUDIO
        }, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;

            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                startMainActivity();
            } else {
                exitApp();
            }
        }
    }

    private void exitApp() {
        Toast.makeText(this, "Permissions are required to use this app. Exiting...", Toast.LENGTH_SHORT).show();
        finishAffinity();
    }

    private void startMainActivity() {
        // Start your main activity here
        Intent intent = new Intent(this, RingdroidSelectActivity.class);
        startActivity(intent);
        finish();
    }
}
