package com.example.friendminder.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "friend_minder_settings"
private const val KEY_HOUR = "reminder_hour"
private const val KEY_MINUTE = "reminder_minute"
private const val KEY_RANDOM_ENABLED = "random_time_enabled"
private const val KEY_RANDOM_START = "random_start_hour"
private const val KEY_RANDOM_END = "random_end_hour"
private const val KEY_CONTACTS_PER_DAY = "contacts_per_day"
private const val KEY_MESSAGE_ENABLED = "message_enabled"
private const val KEY_MESSAGE_TEMPLATE = "message_template" // legacy single-template key, FRM-30 migration source
private const val KEY_MESSAGE_TEMPLATES = "message_templates_json"
private const val KEY_COOLDOWN_DAYS = "cooldown_days"
private const val KEY_DIRECT_SEND_ENABLED = "direct_send_enabled"

// PRD-locked defaults (§16 Open Questions -> Resolved Decisions)
private const val DEFAULT_CONTACTS_PER_DAY = 1
private const val DEFAULT_COOLDOWN_DAYS = 3
private const val DEFAULT_MESSAGE_ENABLED = true
private const val DEFAULT_DIRECT_SEND_ENABLED = false
private val DEFAULT_MESSAGE_TEMPLATES = listOf(
    "Hey, How's it going?",
    "Thinking of you — how have you been?",
    "It's been a while! What's new with you?"
)

class SharedPrefsSettingsRepository(context: Context) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val templatesListType = object : TypeToken<List<String>>() {}.type

    override suspend fun getReminderTime(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val hour = prefs.getInt(KEY_HOUR, -1)
        val minute = prefs.getInt(KEY_MINUTE, -1)
        if (hour < 0 || minute < 0) null else hour to minute
    }

    override suspend fun setReminderTime(hour: Int, minute: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()
    }

    override suspend fun isRandomTimeEnabled(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_RANDOM_ENABLED, false)
    }

    override suspend fun setRandomTimeEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_RANDOM_ENABLED, enabled).apply()
    }

    override suspend fun getRandomTimeRange(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val start = prefs.getInt(KEY_RANDOM_START, -1)
        val end = prefs.getInt(KEY_RANDOM_END, -1)
        if (start < 0 || end < 0) null else start to end
    }

    override suspend fun setRandomTimeRange(start: Int, end: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(KEY_RANDOM_START, start).putInt(KEY_RANDOM_END, end).apply()
    }

    override suspend fun getContactsPerDay(): Int = withContext(Dispatchers.IO) {
        prefs.getInt(KEY_CONTACTS_PER_DAY, DEFAULT_CONTACTS_PER_DAY)
    }

    override suspend fun setContactsPerDay(count: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(KEY_CONTACTS_PER_DAY, count).apply()
    }

    override suspend fun isMessageEnabled(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_MESSAGE_ENABLED, DEFAULT_MESSAGE_ENABLED)
    }

    override suspend fun setMessageEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_MESSAGE_ENABLED, enabled).apply()
    }

    override suspend fun getMessageTemplates(): List<String> = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_MESSAGE_TEMPLATES, null)
        val stored = if (json != null) {
            runCatching { gson.fromJson<List<String>>(json, templatesListType) }.getOrNull()
        } else {
            null
        }
        val nonBlank = stored?.map { it.trim() }?.filter { it.isNotBlank() }

        if (!nonBlank.isNullOrEmpty()) return@withContext nonBlank

        // Nothing valid under the new key yet — migrate the legacy single
        // template (pre-FRM-30) if one was set, otherwise fall back to defaults.
        val legacy = prefs.getString(KEY_MESSAGE_TEMPLATE, null)?.trim()
        if (!legacy.isNullOrBlank()) listOf(legacy) else DEFAULT_MESSAGE_TEMPLATES
    }

    override suspend fun setMessageTemplates(templates: List<String>) = withContext(Dispatchers.IO) {
        val cleaned = templates.map { it.trim() }.filter { it.isNotBlank() }
        prefs.edit()
            .putString(KEY_MESSAGE_TEMPLATES, gson.toJson(cleaned))
            .remove(KEY_MESSAGE_TEMPLATE) // superseded by KEY_MESSAGE_TEMPLATES
            .apply()
    }

    override suspend fun getCooldownDays(): Int = withContext(Dispatchers.IO) {
        prefs.getInt(KEY_COOLDOWN_DAYS, DEFAULT_COOLDOWN_DAYS)
    }

    override suspend fun setCooldownDays(days: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(KEY_COOLDOWN_DAYS, days).apply()
    }

    override suspend fun isDirectSendEnabled(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_DIRECT_SEND_ENABLED, DEFAULT_DIRECT_SEND_ENABLED)
    }

    override suspend fun setDirectSendEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_DIRECT_SEND_ENABLED, enabled).apply()
    }
}
