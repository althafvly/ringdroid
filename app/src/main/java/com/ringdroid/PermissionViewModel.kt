package com.ringdroid

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class PermissionViewModel(application: Application) : AndroidViewModel(application) {
    private val _storagePermission = MutableLiveData<Boolean>()
    val storagePermission: LiveData<Boolean> get() = _storagePermission

    private val _contactPermission = MutableLiveData<Boolean>()
    val contactPermission: LiveData<Boolean> get() = _contactPermission

    private val _micPermission = MutableLiveData<Boolean>()
    val micPermission: LiveData<Boolean> get() = _micPermission

    companion object {
        @JvmStatic
        fun hasWriteSettingsPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
            return Settings.System.canWrite(context)
        }

        @JvmStatic
        fun requestWriteSettingsPermission(activity: Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:" + activity.packageName)
                activity.startActivity(intent)
            }
        }

        @JvmStatic
        fun hasMediaAudioPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            return context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkStoragePermission() {
        val app = getApplication<Application>()
        val hasStorage = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) true
            else app.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        val hasMedia = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) false
            else app.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED

        _storagePermission.value = hasStorage || hasMedia
    }

    fun checkContactPermission() {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            _contactPermission.value = true
            return
        }
        _contactPermission.value = app.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                                   app.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    fun checkMicPermission() {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            _micPermission.value = true
            return
        }
        _micPermission.value = app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}
