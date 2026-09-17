package com.example.friendminder.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.friendminder.data.models.SpecialDate
import com.example.friendminder.data.models.SpecialDateSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "friend_minder_special_dates"
private const val KEY_DATES = "special_dates_json"

class SharedPrefsSpecialDateRepository(context: Context) : SpecialDateRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<SpecialDate>>() {}.type

    override suspend fun getAll(): List<SpecialDate> = withContext(Dispatchers.IO) { readAll() }

    override suspend fun getForContact(contactId: String): List<SpecialDate> =
        getAll().filter { it.contactId == contactId }

    override suspend fun upsert(specialDate: SpecialDate) = withContext(Dispatchers.IO) {
        writeAll(readAll().filterNot { it.id == specialDate.id } + specialDate)
    }

    override suspend fun delete(specialDateId: String) = withContext(Dispatchers.IO) {
        writeAll(readAll().filterNot { it.id == specialDateId })
    }

    override suspend fun replaceContactsSourced(birthdays: List<SpecialDate>) = withContext(Dispatchers.IO) {
        val custom = readAll().filter { it.source == SpecialDateSource.CUSTOM }
        writeAll(custom + birthdays)
    }

    private fun readAll(): List<SpecialDate> {
        val json = prefs.getString(KEY_DATES, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<SpecialDate>>(json, listType) }.getOrDefault(emptyList())
    }

    private fun writeAll(dates: List<SpecialDate>) {
        prefs.edit().putString(KEY_DATES, gson.toJson(dates)).apply()
    }
}
