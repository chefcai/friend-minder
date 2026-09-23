package com.example.friendminder.data.models

/**
 * Computed per-contact metrics (PRD §6.5, §7). Produced by
 * [com.example.friendminder.domain.services.StatisticsService] and cached via
 * [com.example.friendminder.data.storage.StatisticsCacheRepository] rather
 * than recomputed on every read (PRD §8.4 performance guidance).
 *
 * @param reachRate percentage (0-100), not a 0-1 fraction.
 * @param totalContacts lifetime count of logged outreach entries for this contact.
 * @param computedAt when this snapshot was produced; the cache uses it to decide staleness.
 */
data class ContactStatistics(
    val contactId: String,
    val lastContacted: Long?,
    val daysSinceContact: Int?,
    val streak: Int,
    val reachRate: Float,
    val totalContacts: Int,
    val computedAt: Long
)

/** App-wide roll-up shown on the Dashboard (PRD §6.5 "Aggregate stats"). */
data class AggregateStatistics(
    val totalFriends: Int,
    val medianDaysSinceContact: Int?,
    val healthiestStreaks: List<ContactStatistics>,
    val mostNeglected: List<ContactStatistics>,
    /** Outreach *events* in the trailing 30 days, across all contacts. Kept for existing consumers. */
    val monthlyOutreachCount: Int,
    /**
     * Distinct tracked contacts with at least one outreach in the current
     * calendar month, in the device time zone (FRM-169 / OH-1). This is the
     * figure behind Overall History's "N contacts reached this month".
     */
    val contactsReachedThisMonth: Int
)
