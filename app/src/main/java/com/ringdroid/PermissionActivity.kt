package com.ringdroid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Switch
import com.ringdroid.PermissionUtils.hasContactPermissions
import com.ringdroid.PermissionUtils.hasMediaAudioPermission
import com.ringdroid.PermissionUtils.hasMicPermissions
import com.ringdroid.PermissionUtils.hasStoragePermission
import com.ringdroid.PermissionUtils.hasWriteSettingsPermission
import com.ringdroid.PermissionUtils.requestContactPermissions
import com.ringdroid.PermissionUtils.requestMediaAudioPermission
import com.ringdroid.PermissionUtils.requestMicPermissions
import com.ringdroid.PermissionUtils.requestStoragePermission
import com.ringdroid.PermissionUtils.requestWriteSettingsPermission
import com.ringdroid.databinding.PermissionScreenBinding

class PermissionActivity : Activity() {
    private var storageSwitch: Switch? = null
    private var writeSettingsSwitch: Switch? = null
    private var micSwitch: Switch? = null
    private var contactSwitch: Switch? = null
    private var mediaAudioSwitch: Switch? = null
    private var nextButton: Button? = null
    private var binding: PermissionScreenBinding? = null

    private val buildType = BuildConfig.FLAVOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PermissionScreenBinding.inflate(layoutInflater)
        setContentView(binding!!.getRoot())

        val forceShow = intent.getBooleanExtra(EXTRA_FORCE_SHOW, false)

        storageSwitch = binding!!.switchStorage
        writeSettingsSwitch = binding!!.switchWriteSettings
        micSwitch = binding!!.switchMic
        contactSwitch = binding!!.switchContacts
        nextButton = binding!!.btnNext
        mediaAudioSwitch = binding!!.switchMediaAudio

        val hasStoragePermission = hasStoragePermission(this)
        val hasMediaAudioPermission = hasMediaAudioPermission(this)

        if (!forceShow && (hasStoragePermission || hasMediaAudioPermission)) {
            startMainActivity()
        } else {
            if (!hasStoragePermission && !hasMediaAudioPermission) {
                showPermissionInfoDialog()
            }
            updateUI(forceShow)
        }
    }

    private fun showPermissionInfoDialog() {
        var message = R.string.storage_permission_required_for_editor_play
        if (buildType != "play") {
            message = R.string.storage_permission_required_for_editor_fdroid
        }
        AlertDialog.Builder(this).setTitle(R.string.storage_permission).setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun updateUI(forceShow: Boolean) {
        val hasStoragePermission = hasStoragePermission(this)
        val hasMediaAudioPermission = hasMediaAudioPermission(this)
        val hasWritePermission = hasWriteSettingsPermission(this)
        val hasContactPermissions = hasContactPermissions(this)
        val hasMicPermissions = hasMicPermissions(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaAudioSwitch!!.isChecked = hasMediaAudioPermission
            mediaAudioSwitch!!.isClickable = !hasMediaAudioPermission
            mediaAudioSwitch!!.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    requestMediaAudioPermission(this)
                }
            }
        } else {
            binding!!.switchMediaAudioEntry.visibility = View.GONE
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 || buildType == "fdroid") {
            storageSwitch!!.isChecked = hasStoragePermission
            storageSwitch!!.isClickable = !hasStoragePermission
            storageSwitch!!.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    requestStoragePermission(this)
                }
            }
        } else {
            binding!!.switchStorageEntry.visibility = View.GONE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            writeSettingsSwitch!!.isChecked = hasWritePermission
            writeSettingsSwitch!!.isClickable = !hasWritePermission
            writeSettingsSwitch!!.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    requestWriteSettingsPermission(this)
                }
            }
        } else {
            binding!!.switchSystemSettingsEntry.visibility = View.GONE
        }

        contactSwitch!!.isChecked = hasContactPermissions
        contactSwitch!!.isClickable = !hasContactPermissions
        contactSwitch!!.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                requestContactPermissions(this)
            }
        }

        micSwitch!!.isChecked = hasMicPermissions
        micSwitch!!.isClickable = !hasMicPermissions
        micSwitch!!.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                requestMicPermissions(this)
            }
        }

        nextButton!!.isEnabled = hasStoragePermission || hasMediaAudioPermission
        nextButton!!.setOnClickListener { _: View? -> startMainActivity() }
        nextButton!!.visibility = if (forceShow) View.GONE else View.VISIBLE
    }

    private fun startMainActivity() {
        startActivity(Intent(this, RingdroidSelectActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        val forceShow = intent.getBooleanExtra(EXTRA_FORCE_SHOW, false)
        updateUI(forceShow)
    }

    companion object {
        const val EXTRA_FORCE_SHOW = BuildConfig.APPLICATION_ID + ".extra.FORCE_SHOW_PERMISSIONS"
    }
}
