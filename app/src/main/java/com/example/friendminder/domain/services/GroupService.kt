package com.example.friendminder.domain.services

import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.ContactGroup

/**
 * Architect <-> Designer/Publisher contract for contact groups (PRD §6.1;
 * FRM-30/FRM-31). Intended callers are Publisher's GroupsFragment /
 * GroupsViewModel, and ContactDetailFragment's group-assignment section.
 *
 * Example usage:
 * ```
 * val group = ServiceLocator.groupService.createGroup(name = "Close Friends", color = 0xFF4CAF50.toInt(), icon = null)
 * ServiceLocator.groupService.assignContactToGroup(contactId = "42", groupId = group.id)
 * val members: List<Contact> = ServiceLocator.groupService.getContactsInGroup(group.id)
 * ```
 */
interface GroupService {
    suspend fun getGroups(): List<ContactGroup>
    suspend fun createGroup(name: String, color: Int, icon: String?): ContactGroup
    suspend fun renameGroup(groupId: String, newName: String)
    suspend fun recolorGroup(groupId: String, color: Int)

    /**
     * Sets or clears this group's check-in interval override (GH #121 /
     * FRM-97). `days` must be `null` (clears the override — this group no
     * longer participates in a member's effective-interval calculation) or
     * in 1..30, matching [com.example.friendminder.data.storage.ReminderFrequencyRepository]'s
     * per-contact override range. Shares `ValuePickerDialogFragment`
     * (SCREENS-PHASE3 §8.4) with the global cooldown, per-contact frequency
     * and add-contact bulk default — one interval picker, not three.
     */
    suspend fun setReminderFrequency(groupId: String, days: Int?)

    /** Deletes the group only; does not remove or affect any contact (PRD §6.1). */
    suspend fun deleteGroup(groupId: String)

    suspend fun assignContactToGroup(contactId: String, groupId: String)
    suspend fun removeContactFromGroup(contactId: String, groupId: String)
    suspend fun getGroupsForContact(contactId: String): List<ContactGroup>
    suspend fun getContactsInGroup(groupId: String): List<Contact>

    /** PRD §7 sketches `ContactGroup.memberCount` as a cached field; computed here on demand instead so it can never go stale. */
    suspend fun getMemberCount(groupId: String): Int

    /**
     * Resolves [contactId]'s effective check-in interval (GH #121 / FRM-97):
     * the minimum of every explicitly-set interval that applies — the
     * contact's own override, every group it belongs to that has one set,
     * and the app-wide global default, which always participates since it
     * is never "unset". This is the single source of truth other services
     * (the reminder scheduler, [StatisticsService]'s streak calculation)
     * must use instead of each re-deriving contact-vs-global precedence on
     * its own — that duplication is exactly what let this stop short of
     * groups in the first place.
     */
    suspend fun getEffectiveInterval(contactId: String): EffectiveInterval
}
