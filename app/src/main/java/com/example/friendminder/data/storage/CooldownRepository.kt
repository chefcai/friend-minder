package com.example.friendminder.data.storage

/**
 * Tracks the last time each contact was *suggested* by the app (not the last
 * time the user actually texted them — PRD §8 "Decisions Made") so the
 * random picker can avoid re-surfacing someone within their cooldown window.
 */
interface CooldownRepository {
    /** Timestamp in epoch millis, or null if this contact has never been suggested. */
    suspend fun getLastSuggestion(contactId: String): Long?

    suspend fun setLastSuggestion(contactId: String, timestamp: Long)

    /** True if [contactId] was suggested within the last [cooldownDays] days. */
    suspend fun isOnCooldown(contactId: String, cooldownDays: Int): Boolean
}
