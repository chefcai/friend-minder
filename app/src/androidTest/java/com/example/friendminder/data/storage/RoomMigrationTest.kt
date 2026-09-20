package com.example.friendminder.data.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.friendminder.data.room.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FRM-81: verifies [RoomMigration] copies pre-existing SharedPreferences+JSON
 * data (OutreachLog, ContactGroup/membership, SpecialDate) into Room exactly
 * once, and — critically, per the promise made when this migration was
 * approved — never deletes the old SharedPreferences data as a fallback net.
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(
            "friend_minder_groups",
            "friend_minder_special_dates",
            "friend_minder_outreach_log",
            "friend_minder_schema"
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        AppDatabase.resetForTesting(context)
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTesting(context)
    }

    @Test
    fun migratesOutreachLogsGroupsMembershipAndSpecialDatesWithoutTouchingOldData() = runBlocking {
        // Seed legacy SharedPreferences data via the old repositories themselves,
        // exactly as a Phase 2 (pre-FRM-81) install would have written it.
        SharedPrefsOutreachLogRepository(context).add(
            com.example.friendminder.data.models.OutreachLog(
                id = "log-1",
                contactId = "contact-1",
                timestamp = 1000L,
                type = com.example.friendminder.data.models.OutreachType.SMS
            )
        )
        val groupRepo = SharedPrefsContactGroupRepository(context)
        groupRepo.createGroup(
            com.example.friendminder.data.models.ContactGroup(id = "g1", name = "Family", color = 0xFF0000, createdAt = 1L)
        )
        groupRepo.addContactToGroup("contact-1", "g1")
        SharedPrefsSpecialDateRepository(context).upsert(
            com.example.friendminder.data.models.SpecialDate(id = "d1", contactId = "contact-1", label = "Birthday", month = 5, day = 4)
        )

        RoomMigration.migrateIfNeeded(context)

        val db = AppDatabase.getInstance(context)
        assertEquals(1, db.outreachLogDao().getAll().size)
        assertEquals(1, db.contactGroupDao().getGroups().size)
        assertEquals(setOf("g1"), db.contactGroupDao().getGroupIdsForContact("contact-1").toSet())
        assertEquals(1, db.specialDateDao().getAll().size)
        assertEquals(SchemaVersion.ROOM_MIGRATION_V1, SchemaVersion.current(context))

        // The old data is left in place, not deleted, as a fallback safety net.
        assertTrue(SharedPrefsOutreachLogRepository(context).getAll().isNotEmpty())
        assertTrue(groupRepo.getGroups().isNotEmpty())

        // Running again is a no-op (gated on SchemaVersion), so re-running doesn't duplicate rows.
        RoomMigration.migrateIfNeeded(context)
        assertEquals(1, db.outreachLogDao().getAll().size)
    }

    @Test
    fun noOpWhenThereIsNoLegacyData() = runBlocking {
        RoomMigration.migrateIfNeeded(context)

        val db = AppDatabase.getInstance(context)
        assertTrue(db.outreachLogDao().getAll().isEmpty())
        assertTrue(db.contactGroupDao().getGroups().isEmpty())
        assertTrue(db.specialDateDao().getAll().isEmpty())
        assertEquals(SchemaVersion.ROOM_MIGRATION_V1, SchemaVersion.current(context))
    }
}
