package com.ringdroid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Switch
import com.ringdroid.PermissionUtils.hasContactPermissions
import com.ringdroid.PermissionUtils.hasMicPermissions
import com.ringdroid.PermissionUtils.hasStoragePermission
import com.ringdroid.PermissionUtils.hasWriteSettingsPermission
import com.ringdroid.PermissionUtils.requestContactPermissions
import com.ringdroid.PermissionUtils.requestMicPermissions
import com.ringdroid.PermissionUtils.requestStoragePermission
import com.ringdroid.PermissionUtils.requestWriteSettingsPermission
import com.ringdroid.databinding.PermissionScreenBinding

class PermissionActivity : Activity() {
    private var storageSwitch: Switch? = null
    private var writeSettingsSwitch: Switch? = null
    private var micSwitch: Switch? = null
    private var contactSwitch: Switch? = null
    private var nextButton: Button? = null
    private var binding: PermissionScreenBinding? = null

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

        if (!forceShow && hasStoragePermission(this)) {
            startMainActivity()
        } else {
            updateUI(forceShow)
        }
    }

    private fun updateUI(forceShow: Boolean) {
        val hasStoragePermission = hasStoragePermission(this)
        val hasWritePermission = hasWriteSettingsPermission(this)
        val hasContactPermissions = hasContactPermissions(this)
        val hasMicPermissions = hasMicPermissions(this)

        storageSwitch!!.isChecked = hasStoragePermission
        storageSwitch!!.isClickable = !hasStoragePermission
        storageSwitch!!.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                requestStoragePermission(this)
            }
        }

        writeSettingsSwitch!!.isChecked = hasWritePermission
        writeSettingsSwitch!!.isClickable = !hasWritePermission
        writeSettingsSwitch!!.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                requestWriteSettingsPermission(this)
            }
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

        nextButton!!.isEnabled = hasStoragePermission
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
