package com.example.friendminder.domain.services

import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.ContactGroup
import com.example.friendminder.data.storage.ContactGroupRepository
import com.example.friendminder.data.storage.FriendListRepository
import com.example.friendminder.data.storage.ReminderFrequencyRepository
import com.example.friendminder.data.storage.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GH #121 / FRM-97: the shortest-interval-wins precedence across
 * contact / group / global. Hand-rolled fakes (no mocking library in this
 * project, by design - see ServiceLocator's doc) covering only what
 * [DefaultGroupService.getEffectiveInterval] actually reads.
 */
class DefaultGroupServiceTest {

    private class FakeContactGroupRepository : ContactGroupRepository {
        val groups = mutableMapOf<String, ContactGroup>()
        val membership = mutableMapOf<String, MutableSet<String>>() // contactId -> groupIds

        override suspend fun getGroups(): List<ContactGroup> = groups.values.toList()
        override suspend fun getGroup(groupId: String): ContactGroup? = groups[groupId]
        override suspend fun createGroup(group: ContactGroup) { groups[group.id] = group }
        override suspend fun updateGroup(group: ContactGroup) { groups[group.id] = group }
        override suspend fun deleteGroup(groupId: String) { groups.remove(groupId) }
        override suspend fun getGroupIdsForContact(contactId: String): Set<String> =
            membership[contactId].orEmpty()
        override suspend fun getContactIdsInGroup(groupId: String): Set<String> =
            membership.filterValues { groupId in it }.keys
        override suspend fun addContactToGroup(contactId: String, groupId: String) {
            membership.getOrPut(contactId) { mutableSetOf() }.add(groupId)
        }
        override suspend fun removeContactFromGroup(contactId: String, groupId: String) {
            membership[contactId]?.remove(groupId)
        }
    }

    private class FakeFriendListRepository : FriendListRepository {
        override suspend fun getFriendList(): List<Contact> = emptyList()
        override suspend fun addFriend(contact: Contact) = Unit
        override suspend fun removeFriend(contactId: String) = Unit
        override suspend fun isFriend(contactId: String): Boolean = false
    }

    private class FakeReminderFrequencyRepository : ReminderFrequencyRepository {
        val overrides = mutableMapOf<String, Int>()
        override suspend fun getOverride(contactId: String): Int? = overrides[contactId]
        override suspend fun setOverride(contactId: String, days: Int) { overrides[contactId] = days }
        override suspend fun clearOverride(contactId: String) { overrides.remove(contactId) }
    }

    private class FakeSettingsRepository : SettingsRepository {
        var cooldownDays = 3
        override suspend fun getReminderTime(): Pair<Int, Int>? = null
        override suspend fun setReminderTime(hour: Int, minute: Int) = Unit
        override suspend fun isRandomTimeEnabled(): Boolean = false
        override suspend fun setRandomTimeEnabled(enabled: Boolean) = Unit
        override suspend fun getRandomTimeRange(): Pair<Int, Int>? = null
        override suspend fun setRandomTimeRange(start: Int, end: Int) = Unit
        override suspend fun getContactsPerDay(): Int = 1
        override suspend fun setContactsPerDay(count: Int) = Unit
        override suspend fun isMessageEnabled(): Boolean = true
        override suspend fun setMessageEnabled(enabled: Boolean) = Unit
        override suspend fun getMessageTemplates(): List<String> = listOf("Hi")
        override suspend fun setMessageTemplates(templates: List<String>) = Unit
        override suspend fun getCooldownDays(): Int = cooldownDays
        override suspend fun setCooldownDays(days: Int) { cooldownDays = days }
        override suspend fun isDirectSendEnabled(): Boolean = false
        override suspend fun setDirectSendEnabled(enabled: Boolean) = Unit
        override suspend fun isBirthdayCheckEnabled(): Boolean = true
        override suspend fun setBirthdayCheckEnabled(enabled: Boolean) = Unit
    }

    private val groupRepo = FakeContactGroupRepository()
    private val friendListRepo = FakeFriendListRepository()
    private val frequencyRepo = FakeReminderFrequencyRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val service = DefaultGroupService(groupRepo, friendListRepo, frequencyRepo, settingsRepo)

    private fun group(id: String, name: String, days: Int?) =
        ContactGroup(id = id, name = name, color = 0, reminderFrequencyDays = days)

    @Test
    fun nothingSetAnywhereFallsBackToGlobal() = runBlocking {
        settingsRepo.cooldownDays = 3

        val result = service.getEffectiveInterval("c1")

        assertEquals(3, result.days)
        assertEquals(listOf(IntervalSource.Global), result.sources)
    }

    @Test
    fun rowOne_groupShorterThanContact_groupWins() = runBlocking {
        // Cai's table, row 1: contact=7, group=3, global unspecified (kept high) -> 3, group wins
        frequencyRepo.overrides["c1"] = 7
        groupRepo.createGroup(group("g1", "Close Friends", 3))
        groupRepo.addContactToGroup("c1", "g1")
        settingsRepo.cooldownDays = 30

        val result = service.getEffectiveInterval("c1")

        assertEquals(3, result.days)
        assertEquals(listOf(IntervalSource.Group("g1", "Close Friends")), result.sources)
    }

    @Test
    fun rowTwo_groupShorterThanGlobal_noContactOverride_groupWins() = runBlocking {
        // Row 2: contact unset, group=1, global=3 -> 1, group wins
        groupRepo.createGroup(group("g1", "Family", 1))
        groupRepo.addContactToGroup("c1", "g1")
        settingsRepo.cooldownDays = 3

        val result = service.getEffectiveInterval("c1")

        assertEquals(1, result.days)
        assertEquals(listOf(IntervalSource.Group("g1", "Family")), result.sources)
    }

    @Test
    fun rowThree_contactShorterThanGroup_contactWins() = runBlocking {
        // Row 3: contact=1, group=7, global unspecified -> 1, contact wins
        frequencyRepo.overrides["c1"] = 1
        groupRepo.createGroup(group("g1", "Book Club", 7))
        groupRepo.addContactToGroup("c1", "g1")
        settingsRepo.cooldownDays = 30

        val result = service.getEffectiveInterval("c1")

        assertEquals(1, result.days)
        assertEquals(listOf(IntervalSource.Contact), result.sources)
    }

    @Test
    fun multiGroupMinimumWinsAcrossAllGroups() = runBlocking {
        groupRepo.createGroup(group("g1", "Close Friends", 5))
        groupRepo.createGroup(group("g2", "Band", 2))
        groupRepo.addContactToGroup("c1", "g1")
        groupRepo.addContactToGroup("c1", "g2")
        settingsRepo.cooldownDays = 10

        val result = service.getEffectiveInterval("c1")

        assertEquals(2, result.days)
        assertEquals(listOf(IntervalSource.Group("g2", "Band")), result.sources)
    }

    @Test
    fun tiedGroupsBothAppearInSourcesSortedByName() = runBlocking {
        groupRepo.createGroup(group("g1", "Close Friends", 3))
        groupRepo.createGroup(group("g2", "Band", 3))
        groupRepo.addContactToGroup("c1", "g1")
        groupRepo.addContactToGroup("c1", "g2")
        settingsRepo.cooldownDays = 10

        val result = service.getEffectiveInterval("c1")

        assertEquals(3, result.days)
        assertEquals(
            listOf(IntervalSource.Group("g2", "Band"), IntervalSource.Group("g1", "Close Friends")),
            result.sources
        )
    }

    @Test
    fun unsetGroupInterval_doesNotParticipate() = runBlocking {
        // A group the contact belongs to with NO override set must be invisible to
        // the calculation - not treated as "0" or otherwise able to win.
        groupRepo.createGroup(group("g1", "No Override Group", null))
        groupRepo.addContactToGroup("c1", "g1")
        settingsRepo.cooldownDays = 5

        val result = service.getEffectiveInterval("c1")

        assertEquals(5, result.days)
        assertEquals(listOf(IntervalSource.Global), result.sources)
    }

    @Test
    fun contactAndGlobalTie_contactSourceWinsTiebreak() = runBlocking {
        frequencyRepo.overrides["c1"] = 3
        settingsRepo.cooldownDays = 3

        val result = service.getEffectiveInterval("c1")

        assertEquals(3, result.days)
        assertEquals(listOf(IntervalSource.Contact), result.sources)
    }

    @Test
    fun groupAndGlobalTie_groupSourceWinsTiebreak() = runBlocking {
        groupRepo.createGroup(group("g1", "Close Friends", 3))
        groupRepo.addContactToGroup("c1", "g1")
        settingsRepo.cooldownDays = 3

        val result = service.getEffectiveInterval("c1")

        assertEquals(3, result.days)
        assertEquals(listOf(IntervalSource.Group("g1", "Close Friends")), result.sources)
    }

    @Test(expected = IllegalArgumentException::class)
    fun settingOutOfRangeGroupFrequencyThrows() = runBlocking {
        groupRepo.createGroup(group("g1", "Close Friends", null))
        service.setReminderFrequency("g1", 31)
    }

    @Test
    fun clearingGroupFrequencySetsItBackToNull() = runBlocking {
        groupRepo.createGroup(group("g1", "Close Friends", 5))

        service.setReminderFrequency("g1", null)

        assertTrue(groupRepo.getGroup("g1")?.reminderFrequencyDays == null)
    }
}
