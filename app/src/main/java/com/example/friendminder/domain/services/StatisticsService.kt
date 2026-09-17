package com.example.friendminder.domain.services

import com.example.friendminder.data.models.AggregateStatistics
import com.example.friendminder.data.models.ContactStatistics

/**
 * Architect <-> Designer/Publisher contract for the Dashboard and Contact
 * Detail stats sections (PRD §6.5; FRM-38 UI, computed here). Results are
 * cached (PRD §8.4); pass [forceRefresh] = true after a contact change (new
 * log, contact removed, frequency changed) to bypass a stale cache entry —
 * or call [invalidate] directly.
 *
 * Example usage:
 * ```
 * val stats = ServiceLocator.statisticsService.getStatistics(contactId = "42")
 * val dashboard = ServiceLocator.statisticsService.getAggregateStatistics()
 * ```
 */
interface StatisticsService {
    suspend fun getStatistics(contactId: String, forceRefresh: Boolean = false): ContactStatistics
    suspend fun getAggregateStatistics(forceRefresh: Boolean = false): AggregateStatistics
    suspend fun invalidate(contactId: String)
}
