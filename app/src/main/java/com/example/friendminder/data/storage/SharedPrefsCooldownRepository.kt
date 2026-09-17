package com.example.friendminder.data.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val PREFS_NAME = "friend_minder_cooldowns"
private const val KEY_PREFIX = "last_suggested_"
private const val COUNT_KEY_PREFIX = "reminder_count_"

class SharedPrefsCooldownRepository(context: Context) : CooldownRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun getLastSuggestion(contactId: String): Long? = withContext(Dispatchers.IO) {
        val value = prefs.getLong(KEY_PREFIX + contactId, -1L)
        if (value == -1L) null else value
    }

    override suspend fun setLastSuggestion(contactId: String, timestamp: Long) =
        withContext(Dispatchers.IO) {
            prefs.edit().putLong(KEY_PREFIX + contactId, timestamp).apply()
        }

    override suspend fun isOnCooldown(contactId: String, cooldownDays: Int): Boolean {
        val last = getLastSuggestion(contactId) ?: return false
        val cooldownMillis = TimeUnit.DAYS.toMillis(cooldownDays.toLong())
        return (System.currentTimeMillis() - last) < cooldownMillis
    }

    override suspend fun getReminderCount(contactId: String): Int = withContext(Dispatchers.IO) {
        prefs.getInt(COUNT_KEY_PREFIX + contactId, 0)
    }

    override suspend fun incrementReminderCount(contactId: String) = withContext(Dispatchers.IO) {
        val current = prefs.getInt(COUNT_KEY_PREFIX + contactId, 0)
        prefs.edit().putInt(COUNT_KEY_PREFIX + contactId, current + 1).apply()
    }
}
