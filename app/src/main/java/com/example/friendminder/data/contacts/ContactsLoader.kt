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
     * Result of [loadContactsWithPhoneNumbers]: one [Contact] per device
     * contact that has at least one phone number (PRD §16 Q3's chosen/default
     * number — the OS-flagged primary when one exists, otherwise whichever
     * number the query returned first), plus every phone number on file for
     * that contact so callers can offer a picker (chefcai/friend-minder#39).
     */
    data class ContactsQueryResult(
        val contacts: List<Contact>,
        val phoneNumbersByContactId: Map<String, List<String>>
    )

    private data class ContactRow(
        val id: String,
        val name: String,
        val number: String,
        val photoUri: String?,
        val isPrimary: Boolean
    )

    /** Groups the Phone table's column indices into one param (keeps [readContactRow] under detekt's LongParameterList threshold). */
    private data class PhoneColumns(
        val idIdx: Int,
        val nameIdx: Int,
        val numberIdx: Int,
        val photoIdx: Int,
        val primaryIdx: Int
    )

    /**
     * Contacts with at least one phone number, one row per contact, sorted by
     * display name. Contacts without any phone number are excluded entirely
     * so they can never be selected (Designer spec §4.5). Also returns every
     * phone number on file per contact (deduped, primary-flagged number
     * first) so [com.example.friendminder.ui.friendlist.FriendListFragment]
     * can prompt a picker for contacts with more than one (PRD §16 Q3,
     * chefcai/friend-minder#39) without a second query pass.
     */
    suspend fun loadContactsWithPhoneNumbers(context: Context): ContactsQueryResult =
        withContext(Dispatchers.IO) {
            val firstSeen = LinkedHashMap<String, Contact>()
            val rowsById = LinkedHashMap<String, MutableList<ContactRow>>()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val columns = PhoneColumns(
                    idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
                    nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY),
                    numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI),
                    primaryIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
                )

                while (cursor.moveToNext()) {
                    val row = readContactRow(cursor, columns) ?: continue
                    if (!firstSeen.containsKey(row.id)) {
                        firstSeen[row.id] = Contact(id = row.id, name = row.name, phoneNumber = row.number, photoUri = row.photoUri)
                    }
                    val rows = rowsById.getOrPut(row.id) { mutableListOf() }
                    if (rows.none { it.number == row.number }) rows += row
                }
            }

            val phoneNumbersByContactId = rowsById.mapValues { (_, rows) ->
                rows.sortedByDescending { it.isPrimary }.map { it.number }
            }
            val contacts = firstSeen.values.map { contact ->
                val preferred = phoneNumbersByContactId[contact.id]?.firstOrNull() ?: contact.phoneNumber
                contact.copy(phoneNumber = preferred)
            }.sortedBy { it.name.lowercase() }

            ContactsQueryResult(contacts = contacts, phoneNumbersByContactId = phoneNumbersByContactId)
        }

    /**
     * Builds a [ContactRow] from the cursor's current row, or `null` if the row should be skipped
     * (missing id, missing name, or missing/blank phone number). Keeps [loadContactsWithPhoneNumbers]'s
     * loop to a single jump statement, with zero jumps of its own here (FRM-#5).
     */
    private fun readContactRow(cursor: Cursor, columns: PhoneColumns): ContactRow? =
        cursor.getString(columns.idIdx)?.let { id ->
            cursor.getString(columns.nameIdx)?.let { name ->
                cursor.getString(columns.numberIdx)?.takeIf { it.isNotBlank() }?.let { number ->
                    ContactRow(
                        id = id,
                        name = name,
                        number = number,
                        photoUri = cursor.getString(columns.photoIdx),
                        isPrimary = cursor.getInt(columns.primaryIdx) != 0
                    )
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

    /**
     * Birthdays (month/day only — no year, PRD §6.3 privacy note) for every
     * id in [contactIds], sourced from ContactsContract's Events table.
     * Contacts with no birthday event, or one in an unparseable format, are
     * omitted rather than guessed at.
     */
    suspend fun loadBirthdays(context: Context, contactIds: Set<String>): Map<String, Pair<Int, Int>> =
        withContext(Dispatchers.IO) {
            if (contactIds.isEmpty()) return@withContext emptyMap()
            val result = mutableMapOf<String, Pair<Int, Int>>()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Event.CONTACT_ID,
                ContactsContract.CommonDataKinds.Event.START_DATE
            )
            val selection = "${ContactsContract.CommonDataKinds.Event.MIMETYPE} = ? AND " +
                "${ContactsContract.CommonDataKinds.Event.TYPE} = ?"
            val selectionArgs = arrayOf(
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
            )
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.CONTACT_ID)
                val dateIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.START_DATE)
                while (cursor.moveToNext()) {
                    val entry = readBirthdayRow(cursor, idIdx, dateIdx, contactIds) ?: continue
                    result[entry.first] = entry.second
                }
            }
            result
        }

    /**
     * Builds a (contactId, month-day) pair from the cursor's current row, or
     * `null` if the row should be skipped (contact not in [contactIds],
     * missing date, or an unparseable date format). Keeps [loadBirthdays]'s
     * loop to a single jump statement (FRM-#5 convention, see [readContactRow]).
     */
    private fun readBirthdayRow(
        cursor: Cursor,
        idIdx: Int,
        dateIdx: Int,
        contactIds: Set<String>
    ): Pair<String, Pair<Int, Int>>? =
        cursor.getString(idIdx)?.takeIf { it in contactIds }?.let { contactId ->
            cursor.getString(dateIdx)?.let { raw ->
                parseMonthDay(raw)?.let { monthDay -> contactId to monthDay }
            }
        }

    /**
     * ContactsContract Event dates are documented as either "--MM-DD" (no
     * year) or "yyyy-MM-dd", but real sync adapters (Samsung, Exchange,
     * imported vCards) are known to deviate: a trailing time/offset
     * ("yyyy-MM-dd'T'HH:mm:ss.SSSZ" or "--MM-dd'T'..."), or a bare "MM-dd"
     * with no leading dashes and no year at all. [MONTH_DAY_PATTERNS] tries
     * each known shape in turn against just the date portion (text before
     * a 'T', if present) and returns null only when none match, rather than
     * guessing at a format we've never seen.
     */
    internal fun parseMonthDay(raw: String): Pair<Int, Int>? {
        val datePart = raw.substringBefore('T')
        for (pattern in MONTH_DAY_PATTERNS) {
            pattern.find(datePart)?.let { (m, d) ->
                val month = m.toInt()
                val day = d.toInt()
                if (month in 1..MAX_MONTH && day in 1..MAX_DAY) return month to day
            }
        }
        return null
    }

    private val MONTH_DAY_PATTERNS = listOf(
        Regex("^--(\\d{2})-(\\d{2})$"), // no year: "--MM-DD"
        Regex("^\\d{4}-(\\d{2})-(\\d{2})$"), // with (possibly placeholder) year: "yyyy-MM-dd"
        Regex("^(\\d{2})-(\\d{2})$") // no year, no leading dashes: "MM-dd" (seen from some OEM sync adapters)
    )

    private const val MAX_MONTH = 12
    private const val MAX_DAY = 31

    private operator fun MatchResult.component1(): String = groupValues[1]
    private operator fun MatchResult.component2(): String = groupValues[2]
}
