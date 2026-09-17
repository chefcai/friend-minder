package com.example.friendminder.data.contacts

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.example.friendminder.data.models.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queries the device's Contacts ContentProvider (FRM-6). Callers must have
 * READ_CONTACTS granted before calling either function here — this object
 * does no permission checking of its own.
 */
object ContactsLoader {

    /**
     * Contacts with at least one phone number, one row per contact (first
     * phone number wins), sorted by display name. Contacts without any phone
     * number are excluded entirely so they can never be selected (Designer
     * spec §4.5).
     */
    suspend fun loadContactsWithPhoneNumbers(context: Context): List<Contact> =
        withContext(Dispatchers.IO) {
            val byId = LinkedHashMap<String, Contact>()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (cursor.moveToNext()) {
                    val contact = readContactRow(cursor, idIdx, nameIdx, numberIdx, photoIdx) ?: continue
                    if (!byId.containsKey(contact.id)) byId[contact.id] = contact // first phone number wins
                }
            }
            byId.values.sortedBy { it.name.lowercase() }
        }

    /**
     * Builds a [Contact] from the cursor's current row, or `null` if the row should be skipped
     * (missing id, missing name, or missing/blank phone number). Keeps [loadContactsWithPhoneNumbers]'s
     * loop to a single jump statement, with zero jumps of its own here (FRM-#5).
     */
    private fun readContactRow(
        cursor: Cursor,
        idIdx: Int,
        nameIdx: Int,
        numberIdx: Int,
        photoIdx: Int
    ): Contact? =
        cursor.getString(idIdx)?.let { id ->
            cursor.getString(nameIdx)?.let { name ->
                cursor.getString(numberIdx)?.takeIf { it.isNotBlank() }?.let { number ->
                    Contact(id = id, name = name, phoneNumber = number, photoUri = cursor.getString(photoIdx))
                }
            }
        }

    /**
     * All contact IDs currently on the device (regardless of phone number) —
     * used for the cheap "does this friend still exist?" check (Designer
     * spec §3.3 / §4.4), which shouldn't require re-querying phone numbers.
     */
    suspend fun loadExistingContactIds(context: Context): Set<String> =
        withContext(Dispatchers.IO) {
            val ids = mutableSetOf<String>()
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                while (cursor.moveToNext()) {
                    cursor.getString(idIdx)?.let { ids += it }
                }
            }
            ids
        }
}
