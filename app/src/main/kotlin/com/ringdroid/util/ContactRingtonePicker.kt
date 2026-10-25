package com.ringdroid.util

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.Contacts
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.ringdroid.R

/**
 * Lets the user pick a contact with the system contact picker and assigns a ringtone to it. Must be
 * created while the activity is being initialized.
 */
class ContactRingtonePicker(
    private val activity: ComponentActivity,
    private val onDone: Runnable,
) {
    private val pickLauncher =
        activity.registerForActivityResult(ActivityResultContracts.PickContact(), ::onContactPicked)
    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pickLauncher.launch(null)
            } else {
                Toast.makeText(activity, R.string.require_contacts_permission, Toast.LENGTH_LONG).show()
                onDone.run()
            }
        }

    // The ringtone waiting for the user to pick a contact.
    private val pending = PendingAction(activity, "ContactRingtonePicker")

    fun pick(ringtoneUri: Uri) {
        pending.uri = ringtoneUri
        if (activity.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            pickLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    private fun onContactPicked(contactUri: Uri?) {
        val ringtone = pending.uri
        pending.uri = null
        if (contactUri != null && ringtone != null) {
            try {
                assign(contactUri, ringtone)
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to assign ringtone to $contactUri", e)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to assign ringtone to $contactUri", e)
            }
        }
        onDone.run()
    }

    private fun assign(
        contactUri: Uri,
        ringtone: Uri,
    ) {
        val projection = arrayOf(Contacts._ID, Contacts.DISPLAY_NAME)
        activity.contentResolver.query(contactUri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, c.getLong(0))
                val values = ContentValues().apply { put(Contacts.CUSTOM_RINGTONE, ringtone.toString()) }
                activity.contentResolver.update(uri, values, null, null)
                val message = activity.getString(R.string.success_contact_ringtone) + " " + c.getString(1)
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private companion object {
        const val TAG = "ContactRingtonePicker"
    }
}
