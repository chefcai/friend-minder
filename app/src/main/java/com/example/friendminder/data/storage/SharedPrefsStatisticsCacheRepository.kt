package com.example.friendminder.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.friendminder.data.models.ContactStatistics
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "friend_minder_statistics_cache"
private const val KEY_CACHE = "cache_json"

class SharedPrefsStatisticsCacheRepository(context: Context) : StatisticsCacheRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, ContactStatistics>>() {}.type

    override suspend fun get(contactId: String): ContactStatistics? = withContext(Dispatchers.IO) {
        readAll()[contactId]
    }

    override suspend fun put(statistics: ContactStatistics) = withContext(Dispatchers.IO) {
        writeAll(readAll() + (statistics.contactId to statistics))
    }

    override suspend fun getAll(): Map<String, ContactStatistics> = withContext(Dispatchers.IO) { readAll() }

    override suspend fun invalidate(contactId: String) = withContext(Dispatchers.IO) {
        writeAll(readAll() - contactId)
    }

    override suspend fun invalidateAll() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_CACHE).apply()
    }

    private fun readAll(): Map<String, ContactStatistics> {
        val json = prefs.getString(KEY_CACHE, null) ?: return emptyMap()
        return runCatching { gson.fromJson<Map<String, ContactStatistics>>(json, mapType) }.getOrDefault(emptyMap())
    }

    private fun writeAll(cache: Map<String, ContactStatistics>) {
        prefs.edit().putString(KEY_CACHE, gson.toJson(cache)).apply()
    }
}
