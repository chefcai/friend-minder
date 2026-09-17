package com.example.friendminder.data.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.friendminder.data.models.ContactGroup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Integration/CRUD tests for FRM-30/FRM-31 against real SharedPreferences on a device/emulator. */
@RunWith(AndroidJUnit4::class)
class SharedPrefsContactGroupRepositoryTest {

    private lateinit var repository: SharedPrefsContactGroupRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("friend_minder_groups", Context.MODE_PRIVATE).edit().clear().commit()
        repository = SharedPrefsContactGroupRepository(context)
    }

    @Test
    fun createGetUpdateDeleteGroup() = runBlocking {
        val group = ContactGroup(id = "g1", name = "Close Friends", color = 0xFF00FF00.toInt())
        repository.createGroup(group)
        assertEquals(listOf(group), repository.getGroups())

        val renamed = group.copy(name = "Best Friends")
        repository.updateGroup(renamed)
        assertEquals(renamed, repository.getGroup("g1"))

        repository.deleteGroup("g1")
        assertTrue(repository.getGroups().isEmpty())
    }

    @Test
    fun membershipIsManyToMany() = runBlocking {
        repository.addContactToGroup(contactId = "c1", groupId = "g1")
        repository.addContactToGroup(contactId = "c1", groupId = "g2")
        repository.addContactToGroup(contactId = "c2", groupId = "g1")

        assertEquals(setOf("g1", "g2"), repository.getGroupIdsForContact("c1"))
        assertEquals(setOf("c1", "c2"), repository.getContactIdsInGroup("g1"))

        repository.removeContactFromGroup(contactId = "c1", groupId = "g2")
        assertEquals(setOf("g1"), repository.getGroupIdsForContact("c1"))
    }

    @Test
    fun deletingGroupClearsMembershipButKeepsOtherGroups() = runBlocking {
        repository.createGroup(ContactGroup(id = "g1", name = "Family", color = 0))
        repository.createGroup(ContactGroup(id = "g2", name = "Work", color = 0))
        repository.addContactToGroup(contactId = "c1", groupId = "g1")
        repository.addContactToGroup(contactId = "c1", groupId = "g2")

        repository.deleteGroup("g1")

        assertEquals(setOf("g2"), repository.getGroupIdsForContact("c1"))
        assertNull(repository.getGroup("g1"))
    }
}
