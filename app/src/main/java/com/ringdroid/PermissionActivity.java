package com.ringdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import com.ringdroid.databinding.PermissionScreenBinding;
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
    private PermissionScreenBinding binding;

    private final String buildType = BuildConfig.FLAVOR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = PermissionScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        boolean forceShow = getIntent().getBooleanExtra(EXTRA_FORCE_SHOW, false);

        storageSwitch = binding.switchStorage;
        writeSettingsSwitch = binding.switchWriteSettings;
        micSwitch = binding.switchMic;
        contactSwitch = binding.switchContacts;
        nextButton = binding.btnNext;
        mediaAudioSwitch = binding.switchMediaAudio;

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
            binding.switchMediaAudioEntry.setVisibility(View.GONE);
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 || Objects.equals(buildType, "fdroid")) {
            storageSwitch.setChecked(hasStoragePermission);
            storageSwitch.setClickable(!hasStoragePermission);
            storageSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    PermissionUtils.requestStoragePermission(this);
                }
            });
        } else {
            binding.switchStorageEntry.setVisibility(View.GONE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            writeSettingsSwitch.setChecked(hasWritePermission);
            writeSettingsSwitch.setClickable(!hasWritePermission);
            writeSettingsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    PermissionUtils.requestWriteSettingsPermission(this);
                }
            });
        } else {
            binding.switchSystemSettingsEntry.setVisibility(View.GONE);
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
