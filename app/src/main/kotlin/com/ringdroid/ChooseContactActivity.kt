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

import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ringdroid.PermissionUtils.hasContactPermissions
import com.ringdroid.PermissionUtils.requestContactPermissions
import com.ringdroid.adapter.ContactsAdapter
import com.ringdroid.data.ContactItem
import com.ringdroid.repo.ContactsRepository
import com.ringdroid.viewmodel.ChooseContactViewModel
import com.ringdroid.viewmodel.ChooseContactViewModelFactory
import kotlinx.coroutines.launch

/**
 * After a ringtone has been saved, this activity lets you pick a contact and
 * assign the ringtone to that contact.
 *
 * Modernized:
 * - AppCompatActivity
 * - RecyclerView instead of ListActivity/ListView
 * - ViewModel + StateFlow instead of LoaderManager/CursorLoader
 */
class ChooseContactActivity :
    AppCompatActivity(),
    TextWatcher {
    private var mRingtoneUri: Uri? = null
    private var mFilter: EditText? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var contactsAdapter: ContactsAdapter

    private val viewModel: ChooseContactViewModel by viewModels {
        ChooseContactViewModelFactory(ContactsRepository(contentResolver))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.choose_contact_title)

        mRingtoneUri = intent?.data

        setContentView(R.layout.choose_contact)

        mFilter = findViewById(R.id.search_filter)
        recyclerView = findViewById(R.id.contacts_list)

        contactsAdapter =
            ContactsAdapter { contact ->
                assignRingtoneToContact(contact)
            }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChooseContactActivity)
            adapter = contactsAdapter
            setHasFixedSize(true)
        }

        mFilter?.addTextChangedListener(this)

        if (hasContactPermissions(this)) {
            observeContacts()
            viewModel.loadContacts(filter = null)
        } else {
            requestContactPermissions(this)
        }
    }

    private fun observeContacts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.contacts.collect { list ->
                    Log.v("Ringdroid", "Loaded ${list.size} contacts")
                    contactsAdapter.submitList(list)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
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
            observeContacts()
            viewModel.loadContacts(filter = mFilter?.text?.toString())
        } else {
            Toast.makeText(this, R.string.require_contacts_permission, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun assignRingtoneToContact(contact: ContactItem) {
        val ringtoneUri = mRingtoneUri
        if (ringtoneUri == null) {
            Toast.makeText(this, R.string.choose_contact_title, Toast.LENGTH_SHORT).show()
            return
        }

        val contactUri =
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)

        val values =
            ContentValues().apply {
                put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri.toString())
            }

        contentResolver.update(contactUri, values, null, null)

        val message =
            "${resources.getText(R.string.success_contact_ringtone)} ${contact.displayName}"

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    // TextWatcher.beforeTextChanged
    override fun beforeTextChanged(
        s: CharSequence?,
        start: Int,
        count: Int,
        after: Int,
    ) {
    }

    // TextWatcher.onTextChanged
    override fun onTextChanged(
        s: CharSequence?,
        start: Int,
        before: Int,
        count: Int,
    ) {
    }

    // TextWatcher.afterTextChanged
    override fun afterTextChanged(s: Editable?) {
        val filterText = mFilter?.text?.toString()
        viewModel.loadContacts(filterText)
    }
}
