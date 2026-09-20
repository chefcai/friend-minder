package com.example.friendminder.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ContactGroupDao {
    @Query("SELECT * FROM contact_groups")
    suspend fun getGroups(): List<ContactGroupEntity>

    @Query("SELECT * FROM contact_groups WHERE id = :groupId")
    suspend fun getGroup(groupId: String): ContactGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: ContactGroupEntity)

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
