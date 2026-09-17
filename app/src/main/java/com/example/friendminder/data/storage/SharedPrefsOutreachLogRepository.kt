package com.example.friendminder.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.friendminder.data.models.OutreachLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "friend_minder_outreach_log"
private const val KEY_LOGS = "logs_json"

class SharedPrefsOutreachLogRepository(context: Context) : OutreachLogRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<OutreachLog>>() {}.type

    override suspend fun getAll(): List<OutreachLog> = withContext(Dispatchers.IO) { readAll() }

    override suspend fun getForContact(contactId: String): List<OutreachLog> =
        getAll().filter { it.contactId == contactId }

    override suspend fun add(log: OutreachLog) = withContext(Dispatchers.IO) {
        writeAll(readAll() + log)
    }

    override suspend fun delete(logId: String) = withContext(Dispatchers.IO) {
        writeAll(readAll().filterNot { it.id == logId })
    }

    private fun readAll(): List<OutreachLog> {
        val json = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<OutreachLog>>(json, listType) }.getOrDefault(emptyList())
    }

    private fun writeAll(logs: List<OutreachLog>) {
        prefs.edit().putString(KEY_LOGS, gson.toJson(logs)).apply()
    }
}
