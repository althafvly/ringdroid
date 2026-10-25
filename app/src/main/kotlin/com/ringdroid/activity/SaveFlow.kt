package com.ringdroid.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.ringdroid.R
import com.ringdroid.databinding.DialogProgressBinding
import com.ringdroid.dialog.AfterSaveActionDialog
import com.ringdroid.dialog.FileSaveDialog
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.util.ContactRingtonePicker
import com.ringdroid.util.DefaultSoundSetter
import com.ringdroid.util.createDialog
import com.ringdroid.util.dismissOnDestroy
import com.ringdroid.util.showErrorAlert
import com.ringdroid.viewmodel.SaveViewModel

/**
 * Asks for a name and kind, shows the progress of the view model's save, then offers the
 * follow-up actions for that kind. Must be created while the activity is being initialized.
 */
internal class SaveFlow(
    private val activity: ComponentActivity,
    viewModelLazy: Lazy<SaveViewModel>,
) : SaveViewModel.Listener {
    private val viewModel by viewModelLazy
    private val contactPicker = ContactRingtonePicker(activity) { activity.finish() }
    private val defaultSoundSetter = DefaultSoundSetter(activity) { activity.finish() }
    private var progressDialog: AlertDialog? = null
    private var followUpShown = false

    fun start(
        soundFile: SoundFile,
        title: String?,
        gain: Float,
        startFrame: Int,
        numFrames: Int,
        durationMs: Int,
    ) {
        FileSaveDialog(activity, title) { newTitle, kind ->
            viewModel.save(soundFile, newTitle, gain, kind, startFrame, numFrames, durationMs)
            onSaveStateChanged()
        }.show()
    }

    /** Shows the state of a save, including one started before the activity was recreated. */
    fun attach() {
        viewModel.events.listener = this
        onSaveStateChanged()
    }

    fun detach() {
        viewModel.events.listener = null
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onSaveStateChanged() {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        if (viewModel.isSaving) {
            if (progressDialog == null) {
                progressDialog =
                    createDialog(activity, DialogProgressBinding.inflate(activity.layoutInflater).root, cancelable = false)
                        .also { it.show() }
            }
            return
        }
        progressDialog?.dismiss()
        progressDialog = null
        // Keep the editor open on errors so the selection isn't lost.
        viewModel.takeError()?.let { showErrorAlert(activity, it) }
        val uri = viewModel.savedUri
        if (uri != null && !followUpShown) {
            followUpShown = true
            showFollowUp(uri)
        }
    }

    private fun showFollowUp(uri: Uri) {
        activity.setResult(Activity.RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        // When picking content for another app, just return the file.
        if (activity.intent.getBooleanExtra(RingdroidEditActivity.EXTRA_WAS_GET_CONTENT_INTENT, false)) {
            activity.finish()
            return
        }
        when (viewModel.fileKind) {
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
                        onFollowUpChosen()
                        defaultSoundSetter.set(RingtoneManager.TYPE_NOTIFICATION, uri)
                    }.setNegativeButton(R.string.alert_no_button) { _, _ -> activity.finish() }
                    .setCancelable(false)
                    .show()
                    .dismissOnDestroy()
            }

            else -> {
                AfterSaveActionDialog(activity) { action ->
                    onFollowUpChosen()
                    when (action) {
                        AfterSaveActionDialog.ACTION_MAKE_DEFAULT -> defaultSoundSetter.set(RingtoneManager.TYPE_RINGTONE, uri)
                        AfterSaveActionDialog.ACTION_CHOOSE_CONTACT -> chooseContact(uri)
                        else -> activity.finish()
                    }
                }.show()
            }
        }
    }

    // A recreated editor shouldn't offer the follow-up again once one was chosen.
    private fun onFollowUpChosen() {
        viewModel.savedUri = null
    }

    private fun chooseContact(uri: Uri) {
        try {
            contactPicker.pick(uri)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Couldn't open the contact picker", e)
        }
    }

    private companion object {
        const val TAG = "SaveFlow"
    }
}
