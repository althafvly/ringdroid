package com.ringdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;

import java.util.Objects;

public class PermissionActivity extends Activity {

    public static final String EXTRA_FORCE_SHOW = BuildConfig.APPLICATION_ID + ".extra.FORCE_SHOW_PERMISSIONS";

    private Switch storageSwitch;
    private Switch writeSettingsSwitch;
    private Switch micSwitch;
    private Switch contactSwitch;
    private Switch mediaAudioSwitch;
    private Button nextButton;

    private final String buildType = BuildConfig.FLAVOR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.permission_screen);

        boolean forceShow = getIntent().getBooleanExtra(EXTRA_FORCE_SHOW, false);

        storageSwitch = findViewById(R.id.switch_storage);
        writeSettingsSwitch = findViewById(R.id.switch_write_settings);
        micSwitch = findViewById(R.id.switch_mic);
        contactSwitch = findViewById(R.id.switch_contacts);
        nextButton = findViewById(R.id.btn_next);
        mediaAudioSwitch = findViewById(R.id.switch_media_audio);

        boolean hasStoragePermission = PermissionUtils.hasStoragePermission(this);
        boolean hasMediaAudioPermission = PermissionUtils.hasMediaAudioPermission(this);

        if (!forceShow && (hasStoragePermission || hasMediaAudioPermission)) {
            startMainActivity();
        } else {
            if (!hasStoragePermission && !hasMediaAudioPermission) {
                showPermissionInfoDialog();
            }
            updateUI(forceShow);
        }
    }

    private void showPermissionInfoDialog() {
        int message = R.string.storage_permission_required_for_editor_play;
        if (!Objects.equals(buildType, "play")) {
            message = R.string.storage_permission_required_for_editor_fdroid;
        }
        new AlertDialog.Builder(this).setTitle(R.string.storage_permission).setMessage(message)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void updateUI(boolean forceShow) {
        boolean hasStoragePermission = PermissionUtils.hasStoragePermission(this);
        boolean hasMediaAudioPermission = PermissionUtils.hasMediaAudioPermission(this);
        boolean hasWritePermission = PermissionUtils.hasWriteSettingsPermission(this);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            writeSettingsSwitch.setChecked(hasWritePermission);
            writeSettingsSwitch.setClickable(!hasWritePermission);
            writeSettingsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    PermissionUtils.requestWriteSettingsPermission(this);
                }
            });
        } else {
            findViewById(R.id.switch_system_settings_entry).setVisibility(View.GONE);
        }

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
        nextButton.setVisibility(forceShow ? View.GONE : View.VISIBLE);
    }

    private void startMainActivity() {
        startActivity(new Intent(this, RingdroidSelectActivity.class));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean forceShow = getIntent().getBooleanExtra(EXTRA_FORCE_SHOW, false);
        updateUI(forceShow);
    }
}
