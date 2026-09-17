package com.example.friendminder.data.storage

/**
 * Per-contact override for the global reminder cooldown (PRD §6.2, §7;
 * FRM-32). Null means "use the app-wide default from [SettingsRepository]".
 * Its own tiny store, not a field on [com.example.friendminder.data.models.Contact] —
 * see [com.example.friendminder.data.models.ContactGroup]'s doc for why
 * Phase 2 per-contact data stays out of the MVP Contact/FriendList JSON blob.
 */
interface ReminderFrequencyRepository {
    /** Days between reminders for [contactId], or null if using the global default. */
    suspend fun getOverride(contactId: String): Int?
    suspend fun setOverride(contactId: String, days: Int)
    suspend fun clearOverride(contactId: String)
}
