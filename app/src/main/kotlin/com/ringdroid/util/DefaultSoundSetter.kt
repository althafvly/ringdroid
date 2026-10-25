package com.ringdroid.util

import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ringdroid.R

/**
 * Sets the default ringtone or notification sound. If the "modify system settings" permission is
 * missing, it opens the settings screen and retries when the user comes back.
 */
class DefaultSoundSetter(
    private val activity: ComponentActivity,
    private val onDone: () -> Unit = {},
) {
    private var pendingType = 0
    private var pendingUri: Uri? = null

    init {
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    val uri = pendingUri ?: return
                    pendingUri = null
                    if (PermissionUtils.hasWriteSettingsPermission(activity)) {
                        apply(pendingType, uri)
                    }
                    onDone()
                }
            },
        )
    }

    /** [type] is RingtoneManager.TYPE_RINGTONE or TYPE_NOTIFICATION. */
    fun set(
        type: Int,
        uri: Uri,
    ) {
        if (PermissionUtils.hasWriteSettingsPermission(activity)) {
            apply(type, uri)
            onDone()
            return
        }
        pendingType = type
        pendingUri = uri
        Toast.makeText(activity, R.string.required_system_modify_permission, Toast.LENGTH_SHORT).show()
        PermissionUtils.requestWriteSettingsPermission(activity)
    }

    private fun apply(
        type: Int,
        uri: Uri,
    ) {
        val error =
            try {
                RingtoneManager.setActualDefaultRingtoneUri(activity, type, uri)
                null
            } catch (e: IllegalArgumentException) {
                e
            } catch (e: SecurityException) {
                e
            } catch (e: IllegalStateException) {
                e
            }
        if (error != null) {
            Log.e(TAG, "Failed to set default sound", error)
            Toast.makeText(activity, "Error setting ringtone: " + error.message, Toast.LENGTH_LONG).show()
            return
        }
        val message =
            if (type == RingtoneManager.TYPE_NOTIFICATION) {
                R.string.default_notification_success_message
            } else {
                R.string.default_ringtone_success_message
            }
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "DefaultSoundSetter"
    }
}
