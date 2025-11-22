package com.ringdroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi

object PermissionUtils {
    const val CONTACT_PERMISSION_REQUEST: Int = 2
    const val MIC_PERMISSION_REQUEST: Int = 3
    private const val STORAGE_PERMISSION_REQUEST = 1

    @JvmStatic
    fun hasContactPermissions(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        return activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            activity
                .checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun requestContactPermissions(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        activity.requestPermissions(
            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
            CONTACT_PERMISSION_REQUEST,
        )
    }

    @JvmStatic
    fun hasMicPermissions(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        return activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun requestMicPermissions(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        activity.requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            MIC_PERMISSION_REQUEST,
        )
    }

    @JvmStatic
    fun hasStoragePermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return activity.checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            return Environment.isExternalStorageManager()
        }
    }

    @JvmStatic
    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST,
            )
        } else {
            openManageAllFilesScreen(activity)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun openManageAllFilesScreen(activity: Activity) {
        val intent =
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:${activity.packageName}"))
        activity.startActivity(intent)
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.M)
    fun openWriteSettingsScreen(activity: Activity) {
        val intent =
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:${activity.packageName}"))
        activity.startActivity(intent)
    }
}
