package com.example.friendminder.domain.services

import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.ContactGroup
import com.example.friendminder.data.storage.ContactGroupRepository
import com.example.friendminder.data.storage.FriendListRepository
import com.example.friendminder.data.storage.ReminderFrequencyRepository
import com.example.friendminder.data.storage.SettingsRepository
import java.util.UUID

class DefaultGroupService(
    private val groupRepository: ContactGroupRepository,
    private val friendListRepository: FriendListRepository,
    private val reminderFrequencyRepository: ReminderFrequencyRepository,
    private val settingsRepository: SettingsRepository
) : GroupService {

    override suspend fun getGroups(): List<ContactGroup> = groupRepository.getGroups()

    override suspend fun createGroup(name: String, color: Int, icon: String?): ContactGroup {
        val group = ContactGroup(id = UUID.randomUUID().toString(), name = name, color = color, icon = icon)
        groupRepository.createGroup(group)
        return group
    }

    override suspend fun renameGroup(groupId: String, newName: String) {
        val group = groupRepository.getGroup(groupId) ?: return
        groupRepository.updateGroup(group.copy(name = newName))
    }

    override suspend fun recolorGroup(groupId: String, color: Int) {
        val group = groupRepository.getGroup(groupId) ?: return
        groupRepository.updateGroup(group.copy(color = color))
    }

    override suspend fun setReminderFrequency(groupId: String, days: Int?) {
        require(days == null || days in 1..30) { "Group reminder frequency must be 1-30 days (GH #121)" }
        val group = groupRepository.getGroup(groupId) ?: return
        groupRepository.updateGroup(group.copy(reminderFrequencyDays = days))
    }

    override suspend fun deleteGroup(groupId: String) = groupRepository.deleteGroup(groupId)

    override suspend fun assignContactToGroup(contactId: String, groupId: String) =
        groupRepository.addContactToGroup(contactId, groupId)

    override suspend fun removeContactFromGroup(contactId: String, groupId: String) =
        groupRepository.removeContactFromGroup(contactId, groupId)

    override suspend fun getGroupsForContact(contactId: String): List<ContactGroup> {
        val ids = groupRepository.getGroupIdsForContact(contactId)
        return groupRepository.getGroups().filter { it.id in ids }
    }

    override suspend fun getContactsInGroup(groupId: String): List<Contact> {
        val ids = groupRepository.getContactIdsInGroup(groupId)
        return friendListRepository.getFriendList().filter { it.id in ids }
    }

    override suspend fun getMemberCount(groupId: String): Int =
        groupRepository.getContactIdsInGroup(groupId).size

    override suspend fun getEffectiveInterval(contactId: String): EffectiveInterval {
        // Only explicitly-set levels compete. An unset level must NOT fall
        // back to some in-band sentinel (0, -1, ...) here - that's exactly
        // how "unset" would silently win every comparison. Global is the one
        // level with no null state at all, so it always joins as the floor.
        val candidates = mutableListOf<Pair<Int, IntervalSource>>()

        reminderFrequencyRepository.getOverride(contactId)?.let { contactDays ->
            candidates += contactDays to IntervalSource.Contact
        }

        for (group in getGroupsForContact(contactId)) {
            group.reminderFrequencyDays?.let { groupDays ->
                candidates += groupDays to IntervalSource.Group(group.id, group.name)
            }
        }

        candidates += settingsRepository.getCooldownDays() to IntervalSource.Global

        val minDays = candidates.minOf { it.first }
        val winningExplicitSources = candidates
            .filter { it.first == minDays && it.second !is IntervalSource.Global }
            .map { it.second }

        // Global only ever appears alone. Once any contact/group override
        // explicitly ties the winning value, THAT'S the reason - "Every 3
        // days, from Close Friends and also the global default" tells the
        // user nothing they don't already know from their own override.
        // Global is the fallback explanation, used only when nothing
        // explicit produced the winning number.
        val winningSources = winningExplicitSources.ifEmpty { listOf(IntervalSource.Global) }
            // Deterministic tie order - Contact, then Groups by name - so the
            // same inputs always produce the same display, never map/list
            // iteration order by accident.
            .sortedWith(
                compareBy(
                    { source -> sourceRank(source) },
                    { source -> (source as? IntervalSource.Group)?.groupName.orEmpty() }
                )
            )

        return EffectiveInterval(days = minDays, sources = winningSources)
    }

    private fun sourceRank(source: IntervalSource): Int = when (source) {
        is IntervalSource.Contact -> 0
        is IntervalSource.Group -> 1
        is IntervalSource.Global -> 2
    }
}
