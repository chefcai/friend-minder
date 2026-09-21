package com.example.friendminder.data.storage

import android.content.Context
import com.example.friendminder.data.models.ContactGroup
import com.example.friendminder.data.room.AppDatabase
import com.example.friendminder.data.room.ContactGroupDao
import com.example.friendminder.data.room.ContactGroupMembershipEntity
import com.example.friendminder.data.room.toEntity
import com.example.friendminder.data.room.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room-backed [ContactGroupRepository] (FRM-81), replacing
 * [SharedPrefsContactGroupRepository] as the production implementation.
 * [deleteGroup] no longer needs to manually strip membership rows - the
 * `groupId` foreign key's `CASCADE` delete on [ContactGroupMembershipEntity]
 * does that structurally.
 */
class RoomContactGroupRepository(context: Context) : ContactGroupRepository {

    private val dao: ContactGroupDao = AppDatabase.getInstance(context).contactGroupDao()

    override suspend fun getGroups(): List<ContactGroup> = withContext(Dispatchers.IO) {
        dao.getGroups().map { it.toModel() }
    }

    override suspend fun getGroup(groupId: String): ContactGroup? = withContext(Dispatchers.IO) {
        dao.getGroup(groupId)?.toModel()
    }

    override suspend fun createGroup(group: ContactGroup) = withContext(Dispatchers.IO) {
        dao.insertGroup(group.toEntity())
    }

    // Must be a real UPDATE, not an insert-with-REPLACE - see the kdoc on
    // ContactGroupDao.insertGroup for why REPLACE here would silently wipe
    // the group's membership rows via the CASCADE foreign key.
    override suspend fun updateGroup(group: ContactGroup) = withContext(Dispatchers.IO) {
        dao.updateGroup(group.toEntity())
    }

    override suspend fun deleteGroup(groupId: String) = withContext(Dispatchers.IO) {
        dao.deleteGroup(groupId)
    }

    override suspend fun getGroupIdsForContact(contactId: String): Set<String> = withContext(Dispatchers.IO) {
        dao.getGroupIdsForContact(contactId).toSet()
    }

    override suspend fun getContactIdsInGroup(groupId: String): Set<String> = withContext(Dispatchers.IO) {
        dao.getContactIdsInGroup(groupId).toSet()
    }

    override suspend fun addContactToGroup(contactId: String, groupId: String) = withContext(Dispatchers.IO) {
        dao.addMembership(ContactGroupMembershipEntity(contactId, groupId))
    }

    override suspend fun removeContactFromGroup(contactId: String, groupId: String) = withContext(Dispatchers.IO) {
        dao.removeMembership(contactId, groupId)
    }
}
