package com.ringdroid.core.permissions

import android.app.Activity

open class StoragePermissionUtils {
    companion object {
        fun hasExternalStoragePermission(): Boolean {
            // Disabled for Play flavor
            return false
        }

        fun requestExternalStoragePermission(activity: Activity?) {
            // Intentionally no-op
        }
    }
}
