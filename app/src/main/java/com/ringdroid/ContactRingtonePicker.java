package com.ringdroid;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

/**
 * Lets the user pick a contact with the system contact picker and assigns a ringtone to it. Must be
 * created while the activity is being initialized.
 */
final class ContactRingtonePicker {
    private static final String TAG = "ContactRingtonePicker";
    private final ComponentActivity mActivity;
    private final Runnable mOnDone;
    private final ActivityResultLauncher<Void> mPickLauncher;
    private final ActivityResultLauncher<String> mPermissionLauncher;
    private Uri mRingtoneUri;

    ContactRingtonePicker(ComponentActivity activity, Runnable onDone) {
        mActivity = activity;
        mOnDone = onDone;
        mPickLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.PickContact(), this::onContactPicked);
        mPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        mPickLauncher.launch(null);
                    } else {
                        Toast.makeText(mActivity, R.string.require_contacts_permission,
                                Toast.LENGTH_LONG).show();
                        mOnDone.run();
                    }
                });
    }

    void pick(Uri ringtoneUri) {
        mRingtoneUri = ringtoneUri;
        if (mActivity.checkSelfPermission(
                Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            mPickLauncher.launch(null);
        } else {
            mPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS);
        }
    }

    private void onContactPicked(Uri contactUri) {
        if (contactUri != null && mRingtoneUri != null) {
            try (Cursor c = mActivity.getContentResolver().query(contactUri,
                    new String[]{Contacts._ID, Contacts.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, c.getLong(0));
                    ContentValues values = new ContentValues();
                    values.put(Contacts.CUSTOM_RINGTONE, mRingtoneUri.toString());
                    mActivity.getContentResolver().update(uri, values, null, null);
                    String message = mActivity.getString(R.string.success_contact_ringtone) + " "
                            + c.getString(1);
                    Toast.makeText(mActivity, message, Toast.LENGTH_SHORT).show();
                }
            } catch (SecurityException | IllegalArgumentException e) {
                Log.e(TAG, "Failed to assign ringtone to " + contactUri, e);
            }
        }
        mOnDone.run();
    }
}
