package com.example.friendminder.domain.services

import com.example.friendminder.data.models.AggregateStatistics
import com.example.friendminder.data.models.ContactStatistics
import com.example.friendminder.data.storage.CooldownRepository
import com.example.friendminder.data.storage.FriendListRepository
import com.example.friendminder.data.storage.ReminderFrequencyRepository
import com.example.friendminder.data.storage.SettingsRepository
import com.example.friendminder.data.storage.StatisticsCacheRepository
import java.util.concurrent.TimeUnit

private const val CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000L // PRD §8.4: refresh on contact change or daily.
private const val TOP_N = 5
private const val AGGREGATE_WINDOW_DAYS = 30L

class DefaultStatisticsService(
    private val friendListRepository: FriendListRepository,
    private val outreachLogService: OutreachLogService,
    private val cooldownRepository: CooldownRepository,
    private val reminderFrequencyRepository: ReminderFrequencyRepository,
    private val settingsRepository: SettingsRepository,
    private val cacheRepository: StatisticsCacheRepository
) : StatisticsService {

    override suspend fun getStatistics(contactId: String, forceRefresh: Boolean): ContactStatistics {
        if (!forceRefresh) {
            cacheRepository.get(contactId)?.let { cached ->
                if (System.currentTimeMillis() - cached.computedAt < CACHE_TTL_MILLIS) return cached
            }
        }
        val computed = compute(contactId)
        cacheRepository.put(computed)
        return computed
    }

    override suspend fun getAggregateStatistics(forceRefresh: Boolean): AggregateStatistics {
        val friends = friendListRepository.getFriendList()
        val stats = friends.map { getStatistics(it.id, forceRefresh) }
        val monthAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(AGGREGATE_WINDOW_DAYS)
        return AggregateStatistics(
            totalFriends = friends.size,
            medianDaysSinceContact = StatisticsCalculator.median(stats.mapNotNull { it.daysSinceContact }),
            healthiestStreaks = stats.sortedByDescending { it.streak }.take(TOP_N),
            mostNeglected = stats.sortedByDescending { it.daysSinceContact ?: Int.MAX_VALUE }.take(TOP_N),
            monthlyOutreachCount = outreachLogService.countSince(monthAgo)
        )
    }

    override suspend fun invalidate(contactId: String) = cacheRepository.invalidate(contactId)

    private suspend fun compute(contactId: String): ContactStatistics {
        val history = outreachLogService.getHistory(contactId) // newest first
        val now = System.currentTimeMillis()
        val lastContacted = history.firstOrNull()?.timestamp
        val frequencyDays = reminderFrequencyRepository.getOverride(contactId)
            ?: settingsRepository.getCooldownDays()
        val remindersSent = cooldownRepository.getReminderCount(contactId)
        return ContactStatistics(
            contactId = contactId,
            lastContacted = lastContacted,
            daysSinceContact = lastContacted?.let { StatisticsCalculator.daysSince(it, now) },
            streak = StatisticsCalculator.streak(history.map { it.timestamp }, frequencyDays),
            reachRate = StatisticsCalculator.reachRate(history.size, remindersSent),
            totalContacts = history.size,
            computedAt = now
        )
    }
}
