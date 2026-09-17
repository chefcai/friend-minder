package com.example.friendminder.domain.services

import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.ContactGroup

/**
 * Architect <-> Designer/Publisher contract for contact groups (PRD §6.1;
 * FRM-30/FRM-31). Intended callers are Publisher's GroupsFragment /
 * GroupsViewModel, and ContactDetailFragment's group-assignment section.
 *
 * Example usage:
 * ```
 * val group = ServiceLocator.groupService.createGroup(name = "Close Friends", color = 0xFF4CAF50.toInt(), icon = null)
 * ServiceLocator.groupService.assignContactToGroup(contactId = "42", groupId = group.id)
 * val members: List<Contact> = ServiceLocator.groupService.getContactsInGroup(group.id)
 * ```
 */
interface GroupService {
    suspend fun getGroups(): List<ContactGroup>
    suspend fun createGroup(name: String, color: Int, icon: String?): ContactGroup
    suspend fun renameGroup(groupId: String, newName: String)
    suspend fun recolorGroup(groupId: String, color: Int)

    /** Deletes the group only; does not remove or affect any contact (PRD §6.1). */
    suspend fun deleteGroup(groupId: String)

    suspend fun assignContactToGroup(contactId: String, groupId: String)
    suspend fun removeContactFromGroup(contactId: String, groupId: String)
    suspend fun getGroupsForContact(contactId: String): List<ContactGroup>
    suspend fun getContactsInGroup(groupId: String): List<Contact>

    /** PRD §7 sketches `ContactGroup.memberCount` as a cached field; computed here on demand instead so it can never go stale. */
    suspend fun getMemberCount(groupId: String): Int
}
