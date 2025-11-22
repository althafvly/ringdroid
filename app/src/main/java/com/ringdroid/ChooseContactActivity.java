/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ringdroid;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

/**
 * After a ringtone has been saved, this activity lets you pick a contact and
 * assign the ringtone to that contact.
 */
public class ChooseContactActivity extends Activity implements TextWatcher, LoaderManager.LoaderCallbacks<Cursor> {
    private TextView mFilter;
    private SimpleCursorAdapter mAdapter;
    private Uri mRingtoneUri;

    public ChooseContactActivity() {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setTitle(R.string.choose_contact_title);

        Intent intent = getIntent();
        mRingtoneUri = intent.getData();

        // Inflate our UI from its XML layout description.
        setContentView(R.layout.choose_contact);

        if (PermissionUtils.hasContactPermissions(this)) {
            loadData();
        } else {
            PermissionUtils.requestContactPermissions(this);
        }

        mFilter = findViewById(R.id.search_filter);
        if (mFilter != null) {
            mFilter.addTextChangedListener(this);
        }
    }

    private void loadData() {
        try {
            ListView listView = findViewById(android.R.id.list);
            mAdapter = new SimpleCursorAdapter(this,
                    // Use a template that displays a text view
                    R.layout.contact_row,
                    // Set an empty cursor right now. Will be set in onLoadFinished()
                    null,
                    // Map from database columns...
                    new String[]{Contacts.CUSTOM_RINGTONE, Contacts.STARRED, Contacts.DISPLAY_NAME},
                    // To widget ids in the row layout...
                    new int[]{R.id.row_ringtone, R.id.row_starred, R.id.row_display_name}, 0);

            mAdapter.setViewBinder((view, cursor, columnIndex) -> {
                String name = cursor.getColumnName(columnIndex);
                String value = cursor.getString(columnIndex);
                if (name.equals(Contacts.CUSTOM_RINGTONE)) {
                    if (value != null && !value.isEmpty()) {
                        view.setVisibility(View.VISIBLE);
                    } else {
                        view.setVisibility(View.INVISIBLE);
                    }
                    return true;
                }
                if (name.equals(Contacts.STARRED)) {
                    if (value != null && value.equals("1")) {
                        view.setVisibility(View.VISIBLE);
                    } else {
                        view.setVisibility(View.INVISIBLE);
                    }
                    return true;
                }

                return false;
            });

            listView.setAdapter(mAdapter);

            // On click, assign ringtone to contact
            listView.setOnItemClickListener((parent, view, position, id) -> assignRingtoneToContact());

            getLoaderManager().initLoader(0, null, this);
        } catch (SecurityException e) {
            // No permission to retrieve contacts?
            Log.e("Ringdroid", e.toString());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != PermissionUtils.CONTACT_PERMISSION_REQUEST)
            return;

        boolean allPermissionsGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (allPermissionsGranted) {
            loadData();
        } else {
            Toast.makeText(this, R.string.require_contacts_permission, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void assignRingtoneToContact() {
        Cursor c = mAdapter.getCursor();
        int dataIndex = c.getColumnIndexOrThrow(Contacts._ID);
        String contactId = c.getString(dataIndex);

        dataIndex = c.getColumnIndexOrThrow(Contacts.DISPLAY_NAME);
        String displayName = c.getString(dataIndex);

        Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, contactId);

        ContentValues values = new ContentValues();
        values.put(Contacts.CUSTOM_RINGTONE, mRingtoneUri.toString());
        getContentResolver().update(uri, values, null, null);

        String message = getResources().getText(R.string.success_contact_ringtone) + " " + displayName;

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    /* Implementation of TextWatcher.beforeTextChanged */
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    /* Implementation of TextWatcher.onTextChanged */
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    /* Implementation of TextWatcher.afterTextChanged */
    public void afterTextChanged(Editable s) {
        Bundle args = new Bundle();
        args.putString("filter", mFilter.getText().toString());
        getLoaderManager().restartLoader(0, args, this);
    }

    /* Implementation of LoaderCallbacks.onCreateLoader */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String selection = null;
        String filter = args != null ? args.getString("filter") : null;
        if (filter != null && !filter.isEmpty()) {
            selection = "(DISPLAY_NAME LIKE \"%" + filter + "%\")";
        }
        return new CursorLoader(this, Contacts.CONTENT_URI,
                new String[]{Contacts._ID, Contacts.CUSTOM_RINGTONE, Contacts.DISPLAY_NAME,
                        Contacts.LAST_TIME_CONTACTED, Contacts.STARRED, Contacts.TIMES_CONTACTED},
                selection, null,
                "STARRED DESC, " + "TIMES_CONTACTED DESC, " + "LAST_TIME_CONTACTED DESC, " + "DISPLAY_NAME ASC");
    }

    /* Implementation of LoaderCallbacks.onLoadFinished */
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.v("Ringdroid", data.getCount() + " contacts");
        mAdapter.swapCursor(data);
    }

    /* Implementation of LoaderCallbacks.onLoaderReset */
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);
    }
}
