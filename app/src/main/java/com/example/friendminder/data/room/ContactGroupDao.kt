package com.example.friendminder.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ContactGroupDao {
    @Query("SELECT * FROM contact_groups")
    suspend fun getGroups(): List<ContactGroupEntity>

    @Query("SELECT * FROM contact_groups WHERE id = :groupId")
    suspend fun getGroup(groupId: String): ContactGroupEntity?

    // Insert only - a real @Update (below) is what edits an existing row.
    // Using @Insert(onConflict = REPLACE) for an update would be wrong here:
    // SQLite's INSERT OR REPLACE deletes the conflicting row before
    // re-inserting it, and since contact_group_membership.groupId is a
    // CASCADE foreign key, that delete silently wipes every member of the
    // group being "updated" (caught via GH #121's setReminderFrequency
    // flow, but renameGroup/recolorGroup shared this same landmine).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ContactGroupEntity)

    // Real SQL UPDATE by primary key - does not delete-then-insert the row,
    // so it never triggers the membership table's CASCADE delete. This is
    // what renameGroup/recolorGroup/setReminderFrequency must use instead
    // of insertGroup whenever the group already exists.
    @Update
    suspend fun updateGroup(group: ContactGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<ContactGroupEntity>)

    /**
     * Deletes only the group row. Its membership rows cascade-delete via the
     * `ForeignKey(onDelete = CASCADE)` on [ContactGroupMembershipEntity.groupId] —
     * no manual cleanup needed, unlike the old SharedPreferences implementation.
     */
    @Query("DELETE FROM contact_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("SELECT groupId FROM contact_group_membership WHERE contactId = :contactId")
    suspend fun getGroupIdsForContact(contactId: String): List<String>

    @Query("SELECT contactId FROM contact_group_membership WHERE groupId = :groupId")
    suspend fun getContactIdsInGroup(groupId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMembership(membership: ContactGroupMembershipEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMemberships(memberships: List<ContactGroupMembershipEntity>)

    @Query("DELETE FROM contact_group_membership WHERE contactId = :contactId AND groupId = :groupId")
    suspend fun removeMembership(contactId: String, groupId: String)
}
