package com.ringdroid

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.ringdroid.PermissionUtils.hasContactPermissions
import com.ringdroid.PermissionUtils.hasMicPermissions
import com.ringdroid.PermissionUtils.hasStoragePermission
import com.ringdroid.PermissionUtils.openWriteSettingsScreen
import com.ringdroid.PermissionUtils.requestContactPermissions
import com.ringdroid.PermissionUtils.requestMicPermissions
import com.ringdroid.PermissionUtils.requestStoragePermission

class PermissionActivity : AppCompatActivity() {
    private var storageSwitch: SwitchCompat? = null
    private var writeSettingsSwitch: SwitchCompat? = null
    private var micSwitch: SwitchCompat? = null
    private var contactSwitch: SwitchCompat? = null
    private var nextButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.permission_screen)

        storageSwitch = findViewById(R.id.switch_storage)
        writeSettingsSwitch = findViewById(R.id.switch_write_settings)
        micSwitch = findViewById(R.id.switch_mic)
        contactSwitch = findViewById(R.id.switch_contacts)
        nextButton = findViewById(R.id.btn_next)

        if (hasStoragePermission(this)) {
            startMainActivity()
        } else {
            updateUI()
        }
    }

    private fun updateUI() {
        val hasStoragePermission = hasStoragePermission(this)
        val hasContactPermissions = hasContactPermissions(this)
        val hasMicPermissions = hasMicPermissions(this)
        val hasWritePermission =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                true
            } else {
                Settings.System.canWrite(this)
            }

        storageSwitch?.let {
            it.isChecked = hasStoragePermission
            it.isClickable = !hasStoragePermission
            it.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    requestStoragePermission(this)
                }
            }
        }

        writeSettingsSwitch?.let {
            it.isChecked = hasWritePermission
            it.isClickable = !hasWritePermission
            it.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        openWriteSettingsScreen(this)
                    }
                }
            }
        }

        contactSwitch?.let {
            it.isChecked = hasContactPermissions
            it.isClickable = !hasContactPermissions
            it.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    requestContactPermissions(this)
                }
            }
        }

        micSwitch?.let {
            it.isChecked = hasMicPermissions
            it.isClickable = !hasMicPermissions
            it.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    requestMicPermissions(this)
                }
            }
        }

        nextButton?.let {
            it.isEnabled = hasStoragePermission
            it.setOnClickListener { v: View? -> startMainActivity() }
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, RingdroidSelectActivity::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
