package com.ringdroid.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

object PermissionUtils {
    val micPermission: String = Manifest.permission.RECORD_AUDIO

    /** Before Android 10 the app writes shared storage itself, which needs read access too. */
    val storagePermissions: Array<String>
        get() =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

    fun hasMicPermissions(context: Context): Boolean = context.isGranted(micPermission)

    fun hasStoragePermission(context: Context): Boolean = storagePermissions.all { context.isGranted(it) }

    fun hasWriteSettingsPermission(context: Context): Boolean = Settings.System.canWrite(context)

    fun requestWriteSettingsPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData("package:${context.packageName}".toUri()),
        )
    }

    private fun Context.isGranted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
