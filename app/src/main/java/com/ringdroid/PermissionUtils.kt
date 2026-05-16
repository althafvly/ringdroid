package com.ringdroid

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

object PermissionUtils {
    @JvmStatic
    fun getContactPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
    }

    @JvmStatic
    fun getMicPermission(): String {
        return Manifest.permission.RECORD_AUDIO
    }

    @JvmStatic
    fun getStoragePermission(): String {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        } else {
            Manifest.permission.READ_MEDIA_AUDIO
        }
    }

    @JvmStatic
    fun hasContactPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        return getContactPermissions().all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @JvmStatic
    fun hasMicPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        return context.checkSelfPermission(getMicPermission()) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun hasStoragePermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        return context.checkSelfPermission(getStoragePermission()) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun hasWriteSettingsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        return Settings.System.canWrite(context)
    }

    @JvmStatic
    fun requestWriteSettingsPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = "package:${context.packageName}".toUri()
            context.startActivity(intent)
        }
    }
}
