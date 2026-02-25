package com.ringdroid

import android.app.Activity

open class StoragePermissionUtils {
    fun hasExternalStoragePermission(): Boolean {
        // Disabled for Play flavor
        return false
    }

    fun requestExternalStoragePermission(activity: Activity?) {
        // Intentionally no-op
    }
}
