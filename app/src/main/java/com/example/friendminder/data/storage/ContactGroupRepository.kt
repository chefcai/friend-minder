package com.example.friendminder.data.storage

import com.example.friendminder.data.models.ContactGroup

/**
 * Persists user-created [ContactGroup]s and their M:N membership with
 * contacts (PRD §6.1, §7; FRM-30/FRM-31). Deliberately separate from
 * [FriendListRepository] — see [ContactGroup]'s doc — so adding Phase 2
 * groups never touches the MVP Contact/FriendList storage format.
 */
interface ContactGroupRepository {
    suspend fun getGroups(): List<ContactGroup>
    suspend fun getGroup(groupId: String): ContactGroup?
    suspend fun createGroup(group: ContactGroup)
    suspend fun updateGroup(group: ContactGroup)

    /** Deletes the group only; contacts and their other group memberships are untouched (PRD §6.1). */
    suspend fun deleteGroup(groupId: String)

    suspend fun getGroupIdsForContact(contactId: String): Set<String>
    suspend fun getContactIdsInGroup(groupId: String): Set<String>
    suspend fun addContactToGroup(contactId: String, groupId: String)
    suspend fun removeContactFromGroup(contactId: String, groupId: String)
}
