package com.ringdroid.repo

import android.content.ContentResolver
import android.provider.ContactsContract
import com.ringdroid.data.ContactItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository responsible for querying the Contacts provider.
 */
class ContactsRepository(
    private val contentResolver: ContentResolver,
) {
    suspend fun loadContacts(filter: String?): List<ContactItem> =
        withContext(Dispatchers.IO) {
            val projection =
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.CUSTOM_RINGTONE,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.LAST_TIME_CONTACTED,
                    ContactsContract.Contacts.STARRED,
                    ContactsContract.Contacts.TIMES_CONTACTED,
                )

            var selection: String? = null
            var selectionArgs: Array<String>? = null

            if (!filter.isNullOrBlank()) {
                selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
                selectionArgs = arrayOf("%$filter%")
            }

            val sortOrder =
                "${ContactsContract.Contacts.STARRED} DESC, " +
                    "${ContactsContract.Contacts.TIMES_CONTACTED} DESC, " +
                    "${ContactsContract.Contacts.LAST_TIME_CONTACTED} DESC, " +
                    "${ContactsContract.Contacts.DISPLAY_NAME} ASC"

            val result = mutableListOf<ContactItem>()

            contentResolver
                .query(
                    ContactsContract.Contacts.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                    val ringCol =
                        cursor.getColumnIndexOrThrow(ContactsContract.Contacts.CUSTOM_RINGTONE)
                    val nameCol =
                        cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                    val starCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val customRingtone = cursor.getString(ringCol)
                        val name = cursor.getString(nameCol) ?: ""
                        val starred = cursor.getInt(starCol) == 1

                        result.add(
                            ContactItem(
                                id = id,
                                displayName = name,
                                hasCustomRingtone = !customRingtone.isNullOrEmpty(),
                                isStarred = starred,
                            ),
                        )
                    }
                }

            result
        }
}
