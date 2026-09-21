package com.example.friendminder.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.friendminder.data.models.ContactGroup

/** Room-backed storage for [ContactGroup] (FRM-81). */
@Entity(tableName = "contact_groups")
data class ContactGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    val icon: String?,
    val createdAt: Long
)

fun ContactGroupEntity.toModel() = ContactGroup(
    id = id,
    name = name,
    color = color,
    icon = icon,
    createdAt = createdAt
)

fun ContactGroup.toEntity() = ContactGroupEntity(
    id = id,
    name = name,
    color = color,
    icon = icon,
    createdAt = createdAt
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
