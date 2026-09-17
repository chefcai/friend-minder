package com.example.friendminder.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.friendminder.data.models.Contact
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "friend_minder_prefs"
private const val KEY_FRIEND_LIST = "friend_list_json"

/**
 * Stub SharedPreferences + JSON implementation (PRD §11 storage recommendation
 * for MVP). Deliberately minimal: no caching layer, no migrations. Swap for a
 * Room-backed implementation later if querying needs grow (PRD §11 Option B).
 */
class SharedPrefsFriendListRepository(context: Context) : FriendListRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<Contact>>() {}.type

    override suspend fun getFriendList(): List<Contact> = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_FRIEND_LIST, null) ?: return@withContext emptyList()
        runCatching { gson.fromJson<List<Contact>>(json, listType) }.getOrDefault(emptyList())
    }

    override suspend fun addFriend(contact: Contact) = withContext(Dispatchers.IO) {
        val current = getFriendList().filterNot { it.id == contact.id }
        saveList(current + contact)
    }

    override suspend fun removeFriend(contactId: String) = withContext(Dispatchers.IO) {
        saveList(getFriendList().filterNot { it.id == contactId })
    }

    override suspend fun isFriend(contactId: String): Boolean =
        getFriendList().any { it.id == contactId }

    private fun saveList(contacts: List<Contact>) {
        prefs.edit().putString(KEY_FRIEND_LIST, gson.toJson(contacts)).apply()
    }
}
