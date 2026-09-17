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

    /**
     * Lifetime count of times [contactId] has been suggested (PRD §6.5
     * "Reach Rate" denominator; FRM-38). Added for Phase 2 — the MVP only
     * ever needed the *last* suggestion, not a running total.
     */
    suspend fun getReminderCount(contactId: String): Int

    /** Increments [contactId]'s reminder count. Call alongside [setLastSuggestion] whenever a reminder actually fires. */
    suspend fun incrementReminderCount(contactId: String)
}
