package com.ringdroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Switch;

public class PermissionActivity extends Activity {

    private Switch storageSwitch;
    private Switch writeSettingsSwitch;
    private Switch micSwitch;
    private Switch contactSwitch;
    private Button nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.permission_screen);

        storageSwitch = findViewById(R.id.switch_storage);
        writeSettingsSwitch = findViewById(R.id.switch_write_settings);
        micSwitch = findViewById(R.id.switch_mic);
        contactSwitch = findViewById(R.id.switch_contacts);
        nextButton = findViewById(R.id.btn_next);

        if (PermissionUtils.hasStoragePermission(this)) {
            startMainActivity();
        } else {
            updateUI();
        }
    }

    private void updateUI() {
        boolean hasStoragePermission = PermissionUtils.hasStoragePermission(this);
        boolean hasWritePermission = Settings.System.canWrite(this);
        boolean hasContactPermissions = PermissionUtils.hasContactPermissions(this);
        boolean hasMicPermissions = PermissionUtils.hasMicPermissions(this);

        storageSwitch.setChecked(hasStoragePermission);
        storageSwitch.setClickable(!hasStoragePermission);
        storageSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                PermissionUtils.requestStoragePermission(this);
            }
        });

        writeSettingsSwitch.setChecked(hasWritePermission);
        writeSettingsSwitch.setClickable(!hasWritePermission);
        writeSettingsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                PermissionUtils.openWriteSettingsScreen(this);
            }
        });

        contactSwitch.setChecked(hasContactPermissions);
        contactSwitch.setClickable(!hasContactPermissions);
        contactSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                PermissionUtils.requestContactPermissions(this);
            }
        });

        micSwitch.setChecked(hasMicPermissions);
        micSwitch.setClickable(!hasMicPermissions);
        micSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                PermissionUtils.requestMicPermissions(this);
            }
        });

        nextButton.setEnabled(hasStoragePermission);
        nextButton.setOnClickListener(v -> startMainActivity());
    }

    private void startMainActivity() {
        startActivity(new Intent(this, RingdroidSelectActivity.class));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }
}