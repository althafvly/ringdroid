package com.ringdroid.activity

import android.content.ActivityNotFoundException
import android.media.RingtoneManager
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import com.ringdroid.data.AudioRow
import com.ringdroid.dialog.RowOptionsPopup
import com.ringdroid.util.ContactRingtonePicker
import com.ringdroid.util.DefaultSoundSetter

/**
 * The options menu of a list row: edit, set as default, assign to a contact and delete. Must be
 * created while the activity is being initialized.
 */
internal class RowActions(
    activity: ComponentActivity,
    private val editorLauncher: EditorLauncher,
    private val rowAt: (position: Int) -> AudioRow?,
    onDeleted: () -> Unit,
) {
    private val deleter = AudioDeleter(activity, onDeleted)
    private val contactPicker = ContactRingtonePicker(activity) {}
    private val defaultSoundSetter = DefaultSoundSetter(activity)

    fun show(
        anchor: View,
        position: Int,
    ) {
        val row = rowAt(position) ?: return
        RowOptionsPopup.show(
            anchor,
            row,
            object : RowOptionsPopup.Actions {
                override fun onEdit() {
                    editorLauncher.open(row.uri)
                }

                override fun onSetDefault() {
                    val type = if (row.isRingtone) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION
                    defaultSoundSetter.set(type, row.uri)
                }

                override fun onAssignContact() {
                    try {
                        contactPicker.pick(row.uri)
                    } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "Couldn't open the contact picker", e)
                    }
                }

                override fun onDelete() {
                    deleter.delete(row.uri, row.path, row.title)
                }
            },
        )
    }

    private companion object {
        const val TAG = "RowActions"
    }
}
