package com.ringdroid.feature.contact

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.Contacts
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ringdroid.R
import com.ringdroid.core.permissions.PermissionUtils
import com.ringdroid.data.ContactItem
import com.ringdroid.theme.AppTheme

class ChooseContactActivity : ComponentActivity() {
    private var contacts by mutableStateOf<List<ContactItem>>(emptyList())
    private var filter by mutableStateOf("")
    private lateinit var ringtoneUri: Uri

    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (granted) {
                loadContacts()
            } else {
                Toast.makeText(this, R.string.require_contacts_permission, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.choose_contact_title)

        ringtoneUri =
            intent.data
                ?: run {
                    finish()
                    return
                }

        if (PermissionUtils.hasContactPermissions(this)) {
            loadContacts()
        } else {
            contactsPermissionLauncher.launch(PermissionUtils.CONTACTS_PERMISSIONS)
        }

        setContent {
            AppTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.ringdroid_app_name)) })
                    }
                ) { padding ->
                    ChooseContactScreen(
                        contacts = contacts,
                        filter = filter,
                        onFilterChange = {
                            filter = it
                            loadContacts()
                        },
                        onContactClick = ::assignRingtone,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    private fun loadContacts() {
        if (!PermissionUtils.hasContactPermissions(this)) return
        contacts = queryContacts(filter)
    }

    private fun assignRingtone(contact: ContactItem) {
        val uri = Uri.withAppendedPath(Contacts.CONTENT_URI, contact.id)

        val values = ContentValues().apply { put(Contacts.CUSTOM_RINGTONE, ringtoneUri.toString()) }

        contentResolver.update(uri, values, null, null)

        Toast.makeText(
                this,
                "${getString(R.string.success_contact_ringtone)} ${contact.name}",
                Toast.LENGTH_SHORT,
            )
            .show()

        finish()
    }

    private fun queryContacts(filter: String?): List<ContactItem> {
        val result = mutableListOf<ContactItem>()

        val selection =
            if (!filter.isNullOrBlank()) {
                "${Contacts.DISPLAY_NAME} LIKE ?"
            } else {
                null
            }

        val args =
            if (!filter.isNullOrBlank()) {
                arrayOf("%$filter%")
            } else {
                null
            }

        val cursor =
            contentResolver.query(
                Contacts.CONTENT_URI,
                arrayOf(
                    Contacts._ID,
                    Contacts.DISPLAY_NAME,
                    Contacts.STARRED,
                    Contacts.CUSTOM_RINGTONE,
                ),
                selection,
                args,
                "STARRED DESC, TIMES_CONTACTED DESC, LAST_TIME_CONTACTED DESC, DISPLAY_NAME ASC",
            ) ?: return emptyList()

        cursor.use {
            while (it.moveToNext()) {
                result +=
                    ContactItem(
                        id = it.getString(0),
                        name = it.getString(1),
                        starred = it.getInt(2) == 1,
                        hasCustomRingtone = !it.getString(3).isNullOrEmpty(),
                    )
            }
        }

        return result
    }
}
