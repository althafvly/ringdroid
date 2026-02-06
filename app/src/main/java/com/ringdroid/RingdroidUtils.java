package com.ringdroid;

import android.app.Activity;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.Toast;

public class RingdroidUtils {
    public static void setDefaultRingTone(Activity activity, int type, Uri ringtoneUri, boolean shouldFinish) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(activity)) {
            Toast.makeText(activity, R.string.required_system_modify_permission, Toast.LENGTH_SHORT).show();
            return;
        }


        try {
            RingtoneManager.setActualDefaultRingtoneUri(activity, type, ringtoneUri);
            if (type == RingtoneManager.TYPE_NOTIFICATION) {
                Toast.makeText(activity, R.string.default_notification_success_message, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, R.string.default_ringtone_success_message, Toast.LENGTH_SHORT).show();
            }
        } catch (IllegalArgumentException e) {
            // On some Android versions/devices, setting ringtones may be restricted
            Toast.makeText(activity, "Unable to set as default ringtone. Your device may not allow this.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "Error setting ringtone: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }


        if (shouldFinish) {
            activity.finish();
        }
    }

    public static Uri getExternalAudioCollectionUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        }
        return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    }

    public static Uri getInternalAudioCollectionUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_INTERNAL);
        }
        return MediaStore.Audio.Media.INTERNAL_CONTENT_URI;
    }
}
