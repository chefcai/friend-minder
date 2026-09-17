package com.example.friendminder.data.storage

import com.example.friendminder.data.models.Contact

/**
 * Persists the user's curated Friend List: the subset of phone contacts
 * eligible to be surfaced by the daily reminder (PRD §4, §9).
 */
interface FriendListRepository {
    suspend fun getFriendList(): List<Contact>
    suspend fun addFriend(contact: Contact)
    suspend fun removeFriend(contactId: String)
    suspend fun isFriend(contactId: String): Boolean
}
