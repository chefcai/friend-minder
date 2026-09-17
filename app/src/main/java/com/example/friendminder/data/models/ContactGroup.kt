package com.example.friendminder.data.models

/**
 * A user-created category for organizing Friend List contacts (PRD §6.1,
 * §7). Contacts have an M:N relationship with groups, tracked separately in
 * [com.example.friendminder.data.storage.ContactGroupRepository] rather than
 * as a field on [Contact] itself. Keeping Phase 2 group data out of the
 * Contact/FriendList JSON blob means the MVP's serialized format is never
 * touched, so Phase 2 installs stay backward compatible with Phase 1 data
 * by construction (FRM-31) rather than by a migration step.
 *
 * [ContactGroup] deliberately has no `memberCount` field, unlike the PRD §7
 * sketch — see [com.example.friendminder.domain.services.GroupService.getMemberCount]
 * doc for why that's computed on demand instead of cached on the model.
 */
data class ContactGroup(
    val id: String,
    val name: String,
    val color: Int,
    val icon: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
