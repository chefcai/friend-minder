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
        dao.upsertGroup(ContactGroupEntity("g1", "Close Friends", 0xFF0000, null, 1000L))
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
        dao.upsertGroup(ContactGroupEntity("g1", "Family", 0x00FF00, null, 1000L))
        dao.upsertGroup(ContactGroupEntity("g2", "Work", 0x0000FF, null, 2000L))
        dao.addMembership(ContactGroupMembershipEntity("contact-1", "g1"))
        dao.addMembership(ContactGroupMembershipEntity("contact-1", "g2"))

        assertEquals(setOf("g1", "g2"), dao.getGroupIdsForContact("contact-1").toSet())
        assertEquals(setOf("contact-1"), dao.getContactIdsInGroup("g1").toSet())
    }

    @Test
    fun removingOneMembershipLeavesOthersIntact() = runBlocking {
        dao.upsertGroup(ContactGroupEntity("g1", "Family", 0x00FF00, null, 1000L))
        dao.addMembership(ContactGroupMembershipEntity("contact-1", "g1"))
        dao.addMembership(ContactGroupMembershipEntity("contact-2", "g1"))

        dao.removeMembership("contact-1", "g1")

        assertEquals(setOf("contact-2"), dao.getContactIdsInGroup("g1").toSet())
    }
}
