package com.ringdroid.feature.permission

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ringdroid.core.permissions.PermissionUtils
import com.ringdroid.feature.select.RingdroidSelectActivity
import com.ringdroid.theme.AppTheme

class PermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-skip if already granted
        if (
            PermissionUtils.hasStoragePermission(this) ||
                PermissionUtils.hasMediaAudioPermission(this)
        ) {
            startMainActivity()
            return
        }

        setContent { AppTheme { PermissionScreen(onNext = ::startMainActivity) } }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, RingdroidSelectActivity::class.java))
        finish()
    }
}
