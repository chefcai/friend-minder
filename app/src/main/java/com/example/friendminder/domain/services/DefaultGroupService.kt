package com.example.friendminder.domain.services

import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.ContactGroup
import com.example.friendminder.data.storage.ContactGroupRepository
import com.example.friendminder.data.storage.FriendListRepository
import java.util.UUID

class DefaultGroupService(
    private val groupRepository: ContactGroupRepository,
    private val friendListRepository: FriendListRepository
) : GroupService {

    override suspend fun getGroups(): List<ContactGroup> = groupRepository.getGroups()

    override suspend fun createGroup(name: String, color: Int, icon: String?): ContactGroup {
        val group = ContactGroup(id = UUID.randomUUID().toString(), name = name, color = color, icon = icon)
        groupRepository.createGroup(group)
        return group
    }

    override suspend fun renameGroup(groupId: String, newName: String) {
        val group = groupRepository.getGroup(groupId) ?: return
        groupRepository.updateGroup(group.copy(name = newName))
    }

    override suspend fun recolorGroup(groupId: String, color: Int) {
        val group = groupRepository.getGroup(groupId) ?: return
        groupRepository.updateGroup(group.copy(color = color))
    }

    override suspend fun deleteGroup(groupId: String) = groupRepository.deleteGroup(groupId)

    override suspend fun assignContactToGroup(contactId: String, groupId: String) =
        groupRepository.addContactToGroup(contactId, groupId)

    override suspend fun removeContactFromGroup(contactId: String, groupId: String) =
        groupRepository.removeContactFromGroup(contactId, groupId)

    override suspend fun getGroupsForContact(contactId: String): List<ContactGroup> {
        val ids = groupRepository.getGroupIdsForContact(contactId)
        return groupRepository.getGroups().filter { it.id in ids }
    }

    override suspend fun getContactsInGroup(groupId: String): List<Contact> {
        val ids = groupRepository.getContactIdsInGroup(groupId)
        return friendListRepository.getFriendList().filter { it.id in ids }
    }

    override suspend fun getMemberCount(groupId: String): Int =
        groupRepository.getContactIdsInGroup(groupId).size
}
