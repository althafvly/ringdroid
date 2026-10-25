package com.ringdroid.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.ringdroid.R
import com.ringdroid.databinding.DialogProgressBinding
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.util.ContactRingtonePicker
import com.ringdroid.util.DefaultSoundSetter
import com.ringdroid.util.RingdroidUtils

/**
 * Asks for a name and kind, saves the selection in the background, then offers the follow-up
 * actions for that kind. Must be created while the activity is being initialized.
 */
internal class SaveFlow(
    private val activity: ComponentActivity,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val contactPicker = ContactRingtonePicker(activity) { activity.finish() }
    private val defaultSoundSetter = DefaultSoundSetter(activity) { activity.finish() }
    private var progressDialog: AlertDialog? = null
    private var fileKind = FileSaveDialog.FILE_KIND_RINGTONE

    fun start(
        soundFile: SoundFile,
        title: String?,
        gain: Float,
        startFrame: Int,
        numFrames: Int,
        durationMs: Int,
    ) {
        FileSaveDialog(activity, title) { newTitle, kind ->
            fileKind = kind
            save(RingtoneSaver(activity, soundFile, gain, kind), newTitle, startFrame, numFrames, durationMs)
        }.show()
    }

    /** Closes the progress dialog; called when the editor is destroyed. */
    fun dismiss() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun save(
        saver: RingtoneSaver,
        title: CharSequence,
        startFrame: Int,
        numFrames: Int,
        durationMs: Int,
    ) {
        val dialog =
            AlertDialog
                .Builder(activity)
                .setView(DialogProgressBinding.inflate(activity.layoutInflater).root)
                .setCancelable(false)
                .create()
        RingdroidUtils.setTransparentBackground(dialog)
        progressDialog = dialog
        if (!activity.isFinishing && !activity.isDestroyed) {
            dialog.show()
        }
        RingdroidUtils.BACKGROUND.execute {
            try {
                val uri = saver.save(title, startFrame, numFrames, durationMs)
                dialog.dismiss()
                handler.post { afterSaving(uri) }
            } catch (e: RingtoneSaver.SaveException) {
                dialog.dismiss()
                Log.e(TAG, "Save failed", e.cause)
                // Keep the editor open so the selection isn't lost.
                handler.post { showAlert(e.messageId) }
            }
        }
    }

    private fun afterSaving(uri: Uri) {
        activity.setResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        // When picking content for another app, just return the file.
        if (activity.intent.getBooleanExtra(RingdroidEditActivity.EXTRA_WAS_GET_CONTENT_INTENT, false)) {
            activity.finish()
            return
        }
        when (fileKind) {
            FileSaveDialog.FILE_KIND_MUSIC, FileSaveDialog.FILE_KIND_ALARM -> {
                Toast.makeText(activity, R.string.save_success_message, Toast.LENGTH_SHORT).show()
                activity.finish()
            }

            FileSaveDialog.FILE_KIND_NOTIFICATION -> {
                AlertDialog
                    .Builder(activity)
                    .setTitle(R.string.alert_title_success)
                    .setMessage(R.string.set_default_notification)
                    .setPositiveButton(R.string.alert_yes_button) { _, _ ->
                        defaultSoundSetter.set(RingtoneManager.TYPE_NOTIFICATION, uri)
                    }.setNegativeButton(R.string.alert_no_button) { _, _ -> activity.finish() }
                    .setCancelable(false)
                    .show()
            }

            else -> {
                AfterSaveActionDialog(activity) { action ->
                    when (action) {
                        AfterSaveActionDialog.ACTION_MAKE_DEFAULT -> defaultSoundSetter.set(RingtoneManager.TYPE_RINGTONE, uri)
                        AfterSaveActionDialog.ACTION_CHOOSE_CONTACT -> chooseContact(uri)
                        else -> activity.finish()
                    }
                }.show()
            }
        }
    }

    private fun chooseContact(uri: Uri) {
        try {
            contactPicker.pick(uri)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Couldn't open the contact picker", e)
        }
    }

    private fun showAlert(messageId: Int) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            showErrorAlert(activity, messageId)
        }
    }

    private companion object {
        const val TAG = "SaveFlow"
    }
}

/** Shows an error alert that leaves the current screen open. */
internal fun showErrorAlert(
    activity: ComponentActivity,
    messageId: Int,
) {
    AlertDialog
        .Builder(activity)
        .setTitle(R.string.alert_title_failure)
        .setMessage(messageId)
        .setPositiveButton(R.string.alert_ok_button, null)
        .show()
}
