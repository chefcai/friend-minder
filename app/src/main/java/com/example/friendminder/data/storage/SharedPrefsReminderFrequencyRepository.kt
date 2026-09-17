package com.example.friendminder.data.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "friend_minder_reminder_frequency"
private const val KEY_PREFIX = "override_days_"

class SharedPrefsReminderFrequencyRepository(context: Context) : ReminderFrequencyRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun getOverride(contactId: String): Int? = withContext(Dispatchers.IO) {
        val value = prefs.getInt(KEY_PREFIX + contactId, -1)
        if (value <= 0) null else value
    }

    override suspend fun setOverride(contactId: String, days: Int) = withContext(Dispatchers.IO) {
        require(days in 1..30) { "Reminder frequency must be 1-30 days (PRD §6.2)" }
        prefs.edit().putInt(KEY_PREFIX + contactId, days).apply()
    }

    override suspend fun clearOverride(contactId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_PREFIX + contactId).apply()
    }
}
