package com.example.friendminder.data.models

/**
 * A single entry in the user's curated Friend List.
 *
 * [id] is the stable Android Contacts identifier (ContactsContract.Contacts._ID
 * as a String) so we can detect when a contact has been deleted from the
 * phone (PRD Q4) without re-querying the whole contact list.
 */
data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
)
