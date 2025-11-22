package com.ringdroid

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object RingdroidUtils {
    @JvmStatic
    fun setDefaultRingTone(
        activity: Activity,
        type: Int,
        ringtoneUri: Uri?,
        shouldFinish: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(activity)) {
            Toast
                .makeText(activity, R.string.required_system_modify_permission, Toast.LENGTH_SHORT)
                .show()
            return
        }

        RingtoneManager.setActualDefaultRingtoneUri(activity, type, ringtoneUri)
        if (type == RingtoneManager.TYPE_NOTIFICATION) {
            Toast
                .makeText(
                    activity,
                    R.string.default_notification_success_message,
                    Toast.LENGTH_SHORT,
                ).show()
        } else {
            Toast
                .makeText(activity, R.string.default_ringtone_success_message, Toast.LENGTH_SHORT)
                .show()
        }

        if (shouldFinish) {
            activity.finish()
        }
    }
}
