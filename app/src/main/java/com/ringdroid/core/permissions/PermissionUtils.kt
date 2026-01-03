package com.ringdroid.core.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

object PermissionUtils : StoragePermissionUtils() {
    const val CONTACT_PERMISSION_REQUEST: Int = 1
    const val MEDIA_AUDIO_PERMISSION_REQUEST: Int = 2
    const val MIC_PERMISSION_REQUEST: Int = 3
    private const val STORAGE_PERMISSION_REQUEST = 4

    val CONTACTS_PERMISSIONS: Array<String> =
        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

    fun hasContactPermissions(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

    fun requestContactPermissions(activity: Activity?) {
        activity?.requestPermissions(CONTACTS_PERMISSIONS, CONTACT_PERMISSION_REQUEST)
    }

    @JvmStatic
    fun hasMicPermissions(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @JvmStatic
    fun requestMicPermissions(activity: Activity?) {
        activity?.requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            MIC_PERMISSION_REQUEST,
        )
    }

    fun hasStoragePermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            hasExternalStoragePermission()
        }

    fun requestStoragePermission(activity: Activity?) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            activity?.requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST,
            )
        } else {
            requestExternalStoragePermission(activity)
        }
    }

    @JvmStatic
    fun hasMediaAudioPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }

        return context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun requestMediaAudioPermission(activity: Activity?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        activity?.requestPermissions(
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
            MEDIA_AUDIO_PERMISSION_REQUEST,
        )
    }

    fun hasWriteSettingsPermission(context: Context): Boolean = Settings.System.canWrite(context)

    fun requestWriteSettingsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = ("package:" + context.packageName).toUri()
        context.startActivity(intent)
    }
}
