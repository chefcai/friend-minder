package com.example.friendminder.data.storage

import android.content.Context
import com.example.friendminder.data.room.AppDatabase
import com.example.friendminder.data.room.ContactGroupMembershipEntity
import com.example.friendminder.data.room.toEntity

/**
 * One-time copy of [OutreachLog][com.example.friendminder.data.models.OutreachLog],
 * [ContactGroup][com.example.friendminder.data.models.ContactGroup]/membership, and
 * [SpecialDate][com.example.friendminder.data.models.SpecialDate] data from their old
 * SharedPreferences+JSON storage into Room (FRM-81).
 *
 * Deliberately reads through the existing `SharedPrefsXxxRepository` classes'
 * own public methods rather than re-parsing their JSON blobs directly, so
 * this migration can't drift from those classes' own (de)serialization
 * logic. Deliberately does NOT delete the old SharedPreferences data
 * afterward - it's left in place as a fallback/rollback safety net, at the
 * cost of a small amount of now-unused disk space.
 *
 * Must run to completion (see [FriendMinderApplication.onCreate] - this is
 * invoked with `runBlocking`, not fire-and-forget) before anything reads
 * from the Room-backed repositories, since [ServiceLocator] wires
 * `contactGroupRepository`, `specialDateRepository`, and `outreachLogRepository`
 * straight to the Room implementations - an unmigrated fresh Room database
 * would otherwise look like "no data" to the very first screen the user sees.
 */
object RoomMigration {

    /** Gated on [SchemaVersion] so this copy happens exactly once per install. */
    suspend fun migrateIfNeeded(context: Context) {
        if (SchemaVersion.current(context) >= SchemaVersion.ROOM_MIGRATION_V1) return
        migrate(context)
        SchemaVersion.markCurrent(context, SchemaVersion.ROOM_MIGRATION_V1)
    }

    private suspend fun migrate(context: Context) {
        val db = AppDatabase.getInstance(context)

        val oldOutreachLogs = SharedPrefsOutreachLogRepository(context).getAll()
        if (oldOutreachLogs.isNotEmpty()) {
            db.outreachLogDao().insertAll(oldOutreachLogs.map { it.toEntity() })
        }

        val oldSpecialDates = SharedPrefsSpecialDateRepository(context).getAll()
        if (oldSpecialDates.isNotEmpty()) {
            db.specialDateDao().insertAll(oldSpecialDates.map { it.toEntity() })
        }

        val oldGroupRepo = SharedPrefsContactGroupRepository(context)
        val oldGroups = oldGroupRepo.getGroups()
        if (oldGroups.isNotEmpty()) {
            db.contactGroupDao().insertGroups(oldGroups.map { it.toEntity() })
        }
        val oldMemberships = oldGroups.flatMap { group ->
            oldGroupRepo.getContactIdsInGroup(group.id).map { contactId ->
                ContactGroupMembershipEntity(contactId = contactId, groupId = group.id)
            }
        }
        if (oldMemberships.isNotEmpty()) {
            db.contactGroupDao().insertMemberships(oldMemberships)
        }
    }
}
