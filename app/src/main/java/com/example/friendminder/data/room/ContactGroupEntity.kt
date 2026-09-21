package com.example.friendminder.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.friendminder.data.models.ContactGroup

/**
 * Room-backed storage for [ContactGroup] (FRM-81).
 *
 * [reminderFrequencyDays] added by GH #121 / FRM-97 (schema v2, MIGRATION_1_2
 * in [AppDatabase]) — nullable `INTEGER` column, defaulting to `NULL` on
 * migration for every pre-existing row, which is exactly "this group doesn't
 * override the interval" and requires no backfill.
 */
@Entity(tableName = "contact_groups")
data class ContactGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    val icon: String?,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "NULL") val reminderFrequencyDays: Int? = null
)

fun ContactGroupEntity.toModel() = ContactGroup(
    id = id,
    name = name,
    color = color,
    icon = icon,
    createdAt = createdAt,
    reminderFrequencyDays = reminderFrequencyDays
)

fun ContactGroup.toEntity() = ContactGroupEntity(
    id = id,
    name = name,
    color = color,
    icon = icon,
    createdAt = createdAt,
    reminderFrequencyDays = reminderFrequencyDays
)

/**
 * M:N membership between contacts and [ContactGroupEntity] (FRM-81). Plain
 * string `contactId` — not a foreign key — for the same reason as
 * [OutreachLogEntity.contactId]: Contact/FriendList data stays on
 * SharedPreferences. `groupId` IS a real foreign key with `CASCADE` delete,
 * replacing the old `SharedPrefsContactGroupRepository.deleteGroup()`'s
 * manual "strip this groupId from every contact's membership set" loop —
 * deleting the parent row here structurally deletes its membership rows too.
 */
@Entity(
    tableName = "contact_group_membership",
    primaryKeys = ["contactId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = ContactGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ContactGroupMembershipEntity(
    val contactId: String,
    @ColumnInfo(index = true) val groupId: String
)
