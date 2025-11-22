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
package com.ringdroid

import android.app.ListActivity
import android.app.LoaderManager.LoaderCallbacks
import android.content.ContentValues
import android.content.CursorLoader
import android.content.Loader
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import android.widget.Toast
import com.ringdroid.PermissionUtils.hasContactPermissions
import com.ringdroid.PermissionUtils.requestContactPermissions

/**
 * After a ringtone has been saved, this activity lets you pick a contact and
 * assign the ringtone to that contact.
 */
class ChooseContactActivity :
    ListActivity(),
    TextWatcher,
    LoaderCallbacks<Cursor?> {
    private var mFilter: TextView? = null
    private var mAdapter: SimpleCursorAdapter? = null
    private var mRingtoneUri: Uri? = null

    /** Called when the activity is first created.  */
    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        setTitle(R.string.choose_contact_title)

        val intent = getIntent()
        mRingtoneUri = intent.data

        // Inflate our UI from its XML layout description.
        setContentView(R.layout.choose_contact)

        if (hasContactPermissions(this)) {
            loadData()
        } else {
            requestContactPermissions(this)
        }

        mFilter = findViewById(R.id.search_filter)
        if (mFilter != null) {
            mFilter!!.addTextChangedListener(this)
        }
    }

    private fun loadData() {
        try {
            mAdapter =
                SimpleCursorAdapter(
                    this, // Use a template that displays a text view
                    R.layout.contact_row, // Set an empty cursor right now. Will be set in onLoadFinished()
                    null, // Map from database columns...
                    arrayOf(
                        ContactsContract.Contacts.CUSTOM_RINGTONE,
                        ContactsContract.Contacts.STARRED,
                        ContactsContract.Contacts.DISPLAY_NAME,
                    ), // To widget ids in the row layout...
                    intArrayOf(R.id.row_ringtone, R.id.row_starred, R.id.row_display_name),
                    0,
                )

            mAdapter!!.setViewBinder { view: View?, cursor: Cursor?, columnIndex: Int ->
                val name = cursor!!.getColumnName(columnIndex)
                val value = cursor.getString(columnIndex)
                if (name == ContactsContract.Contacts.CUSTOM_RINGTONE) {
                    if (value != null && !value.isEmpty()) {
                        view!!.visibility = View.VISIBLE
                    } else {
                        view!!.visibility = View.INVISIBLE
                    }
                    return@setViewBinder true
                }
                if (name == ContactsContract.Contacts.STARRED) {
                    if (value != null && value == "1") {
                        view!!.visibility = View.VISIBLE
                    } else {
                        view!!.visibility = View.INVISIBLE
                    }
                    return@setViewBinder true
                }
                false
            }

            listAdapter = mAdapter

            // On click, assign ringtone to contact
            listView.onItemClickListener =
                OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long -> assignRingtoneToContact() }

            loaderManager.initLoader<Cursor?>(0, null, this)
        } catch (e: SecurityException) {
            // No permission to retrieve contacts?
            Log.e("Ringdroid", e.toString())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PermissionUtils.CONTACT_PERMISSION_REQUEST) return

        var allPermissionsGranted = true
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false
                break
            }
        }

        if (allPermissionsGranted) {
            loadData()
        } else {
            Toast.makeText(this, R.string.require_contacts_permission, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun assignRingtoneToContact() {
        val c = mAdapter!!.cursor
        var dataIndex = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
        val contactId = c.getString(dataIndex)

        dataIndex = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
        val displayName = c.getString(dataIndex)

        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)

        val values = ContentValues()
        values.put(ContactsContract.Contacts.CUSTOM_RINGTONE, mRingtoneUri.toString())
        contentResolver.update(uri, values, null, null)

        val message =
            "${resources.getText(R.string.success_contact_ringtone)} $displayName"

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    // Implementation of TextWatcher.beforeTextChanged
    override fun beforeTextChanged(
        s: CharSequence?,
        start: Int,
        count: Int,
        after: Int,
    ) {
    }

    // Implementation of TextWatcher.onTextChanged
    override fun onTextChanged(
        s: CharSequence?,
        start: Int,
        before: Int,
        count: Int,
    ) {
    }

    // Implementation of TextWatcher.afterTextChanged
    override fun afterTextChanged(s: Editable?) {
        val args = Bundle()
        args.putString("filter", mFilter!!.text.toString())
        loaderManager.restartLoader<Cursor?>(0, args, this)
    }

    // Implementation of LoaderCallbacks.onCreateLoader
    override fun onCreateLoader(
        id: Int,
        args: Bundle?,
    ): Loader<Cursor?> {
        var selection: String? = null
        val filter = args?.getString("filter")
        if (filter != null && !filter.isEmpty()) {
            selection = "(DISPLAY_NAME LIKE \"%$filter%\")"
        }
        return CursorLoader(
            this,
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.CUSTOM_RINGTONE,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.LAST_TIME_CONTACTED,
                ContactsContract.Contacts.STARRED,
                ContactsContract.Contacts.TIMES_CONTACTED,
            ),
            selection,
            null,
            "STARRED DESC, " + "TIMES_CONTACTED DESC, " + "LAST_TIME_CONTACTED DESC, " + "DISPLAY_NAME ASC",
        )
    }

    // Implementation of LoaderCallbacks.onLoadFinished
    @Deprecated("Deprecated in Java")
    override fun onLoadFinished(
        loader: Loader<Cursor?>,
        data: Cursor?,
    ) {
        Log.v("Ringdroid", "${data?.count} contacts")
        mAdapter!!.swapCursor(data)
    }

    // Implementation of LoaderCallbacks.onLoaderReset
    @Deprecated("Deprecated in Java")
    override fun onLoaderReset(loader: Loader<Cursor?>?) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no
        // longer using it.
        mAdapter!!.swapCursor(null)
    }
}
