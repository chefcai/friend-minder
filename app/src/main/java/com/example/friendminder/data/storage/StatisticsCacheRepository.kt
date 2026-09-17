package com.example.friendminder.data.storage

import com.example.friendminder.data.models.ContactStatistics

/**
 * Lazy cache for computed [ContactStatistics] (PRD §8.4: "compute on-demand
 * and cache; refresh on contact change or daily"). Not a source of truth —
 * [com.example.friendminder.domain.services.StatisticsService] recomputes
 * and overwrites an entry whenever it's missing or stale.
 */
interface StatisticsCacheRepository {
    suspend fun get(contactId: String): ContactStatistics?
    suspend fun put(statistics: ContactStatistics)
    suspend fun getAll(): Map<String, ContactStatistics>
    suspend fun invalidate(contactId: String)
    suspend fun invalidateAll()
}
