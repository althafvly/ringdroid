package com.ringdroid.core.permissions

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri

open class StoragePermissionUtils {
    companion object {
        fun hasExternalStoragePermission(): Boolean {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
                return Environment.isExternalStorageManager()
            }

            return false
        }

        fun requestExternalStoragePermission(activity: Activity?) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                if (activity != null) {
                    intent.data = ("package:" + activity.packageName).toUri()
                    activity.startActivity(intent)
                }
            }
        }
    }
}
