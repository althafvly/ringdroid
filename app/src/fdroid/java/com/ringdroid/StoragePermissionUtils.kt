package com.ringdroid

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

open class StoragePermissionUtils {
    fun hasExternalStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
            return Environment.isExternalStorageManager()
        }

        return false
    }

    fun requestExternalStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:" + activity.packageName)
            activity.startActivity(intent)
        }
    }
}
