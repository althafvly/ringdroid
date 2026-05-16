package com.ringdroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

object PermissionUtils {
    const val CONTACT_PERMISSION_REQUEST: Int = 1
    const val STORAGE_PERMISSION_REQUEST: Int = 2
    const val MIC_PERMISSION_REQUEST: Int = 3

    @JvmStatic
    fun hasContactPermissions(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        return activity.checkSelfPermission(
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
                && activity.checkSelfPermission(
            Manifest.permission.WRITE_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun requestContactPermissions(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        activity.requestPermissions(
            arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            ),
            CONTACT_PERMISSION_REQUEST
        )
    }

    @JvmStatic
    fun hasMicPermissions(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        return activity.checkSelfPermission(
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun requestMicPermissions(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        activity.requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            MIC_PERMISSION_REQUEST
        )
    }

    @JvmStatic
    fun hasStoragePermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            activity.checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            activity.checkSelfPermission(
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @JvmStatic
    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST
            )
        } else {
            activity.requestPermissions(
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
                STORAGE_PERMISSION_REQUEST
            )
        }
    }

    @JvmStatic
    fun hasWriteSettingsPermission(activity: Activity?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        return Settings.System.canWrite(activity)
    }

    @JvmStatic
    fun requestWriteSettingsPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:" + activity.packageName)
            activity.startActivity(intent)
        }
    }
}
