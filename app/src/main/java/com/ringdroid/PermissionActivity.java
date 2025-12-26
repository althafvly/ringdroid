package com.ringdroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;

public class PermissionActivity extends Activity {

    private Switch storageSwitch;
    private Switch writeSettingsSwitch;
    private Switch micSwitch;
    private Switch contactSwitch;
    private Switch mediaAudioSwitch;
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
        mediaAudioSwitch = findViewById(R.id.switch_media_audio);

        if (PermissionUtils.hasStoragePermission(this) || PermissionUtils.hasMediaAudioPermission(this)) {
            startMainActivity();
        } else {
            updateUI();
        }
    }

    private void updateUI() {
        boolean hasStoragePermission = PermissionUtils.hasStoragePermission(this);
        boolean hasMediaAudioPermission = PermissionUtils.hasMediaAudioPermission(this);
        boolean hasWritePermission;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            hasWritePermission = true;
        } else {
            hasWritePermission = Settings.System.canWrite(this);
        }
        boolean hasContactPermissions = PermissionUtils.hasContactPermissions(this);
        boolean hasMicPermissions = PermissionUtils.hasMicPermissions(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaAudioSwitch.setChecked(hasMediaAudioPermission);
            mediaAudioSwitch.setClickable(!hasMediaAudioPermission);
            mediaAudioSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    PermissionUtils.requestMediaAudioPermission(this);
                }
            });
        } else {
            findViewById(R.id.switch_media_audio_entry).setVisibility(View.GONE);
        }

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

        nextButton.setEnabled(hasStoragePermission || hasMediaAudioPermission);
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
