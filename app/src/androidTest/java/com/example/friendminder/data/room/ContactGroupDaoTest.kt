package com.example.friendminder.data.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FRM-81: verifies [ContactGroupDao], and in particular that deleting a
 * group cascades to its membership rows via the `groupId` foreign key —
 * the structural replacement for `SharedPrefsContactGroupRepository
 * .deleteGroup()`'s old manual membership-cleanup loop.
 */
@RunWith(AndroidJUnit4::class)
class ContactGroupDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ContactGroupDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.contactGroupDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletingGroupCascadesToMembershipRows() = runBlocking {
        dao.insertGroup(ContactGroupEntity("g1", "Close Friends", 0xFF0000, null, 1000L))
        dao.addMembership(ContactGroupMembershipEntity("contact-1", "g1"))
        dao.addMembership(ContactGroupMembershipEntity("contact-2", "g1"))

        assertEquals(setOf("contact-1", "contact-2"), dao.getContactIdsInGroup("g1").toSet())

        dao.deleteGroup("g1")

        assertNull(dao.getGroup("g1"))
        assertTrue(dao.getContactIdsInGroup("g1").isEmpty())
        assertTrue(dao.getGroupIdsForContact("contact-1").isEmpty())
    }

    @Test
    fun membershipIsQueryableFromEitherDirection() = runBlocking {
        dao.insertGroup(ContactGroupEntity("g1", "Family", 0x00FF00, null, 1000L))
        dao.insertGroup(ContactGroupEntity("g2", "Work", 0x0000FF, null, 2000L))
        dao.addMembership(ContactGroupMembershipEntity("contact-1", "g1"))
        dao.addMembership(ContactGroupMembershipEntity("contact-1", "g2"))

        assertEquals(setOf("g1", "g2"), dao.getGroupIdsForContact("contact-1").toSet())
        assertEquals(setOf("contact-1"), dao.getContactIdsInGroup("g1").toSet())
    }

    @Test
    fun removingOneMembershipLeavesOthersIntact() = runBlocking {
        dao.insertGroup(ContactGroupEntity("g1", "Family", 0x00FF00, null, 1000L))
        dao.addMembership(ContactGroupMembershipEntity("contact-1", "g1"))
        dao.addMembership(ContactGroupMembershipEntity("contact-2", "g1"))

        dao.removeMembership("contact-1", "g1")

        assertEquals(setOf("contact-2"), dao.getContactIdsInGroup("g1").toSet())
    }

    @Test
    fun reminderFrequencyDaysDefaultsToNullAndRoundTrips() = runBlocking {
        // GH #121/FRM-97: a group created without an explicit interval must
        // come back null (not 0 or any sentinel) - that's what keeps it out
        // of DefaultGroupService.getEffectiveInterval's precedence calculation.
        dao.insertGroup(ContactGroupEntity("g1", "Close Friends", 0xFF0000, null, 1000L))
        assertNull(dao.getGroup("g1")?.reminderFrequencyDays)

        dao.updateGroup(ContactGroupEntity("g1", "Close Friends", 0xFF0000, null, 1000L, reminderFrequencyDays = 3))
        assertEquals(3, dao.getGroup("g1")?.reminderFrequencyDays)
    }

    @Test
    fun updatingGroupPreservesMembership() = runBlocking {
        // Regression test for a real bug found while testing GH #121: the
        // old upsertGroup() used @Insert(onConflict = REPLACE) for updates
        // too, and SQLite's INSERT OR REPLACE deletes the conflicting row
        // before re-inserting it - which cascade-deleted every membership
        // row for the group being "updated" (rename, recolor, or setting
        // its reminder frequency all went through this path). A group's
        // members must survive any field-only update to that group.
        dao.insertGroup(ContactGroupEntity("g1", "Close Friends", 0xFF0000, null, 1000L))
        dao.addMembership(ContactGroupMembershipEntity("contact-1", "g1"))
        dao.addMembership(ContactGroupMembershipEntity("contact-2", "g1"))

        dao.updateGroup(ContactGroupEntity("g1", "Close Friends", 0xFF0000, null, 1000L, reminderFrequencyDays = 3))

        assertEquals(3, dao.getGroup("g1")?.reminderFrequencyDays)
        assertEquals(setOf("contact-1", "contact-2"), dao.getContactIdsInGroup("g1").toSet())

        dao.updateGroup(ContactGroupEntity("g1", "Renamed", 0x00FF00, null, 1000L, reminderFrequencyDays = 3))

        assertEquals("Renamed", dao.getGroup("g1")?.name)
        assertEquals(setOf("contact-1", "contact-2"), dao.getContactIdsInGroup("g1").toSet())
    }
}
