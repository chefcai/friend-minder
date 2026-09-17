package com.example.friendminder.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.friendminder.data.models.ContactGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "friend_minder_groups"
private const val KEY_GROUPS = "groups_json"
private const val KEY_MEMBERSHIP = "membership_json"

/**
 * SharedPreferences + JSON implementation, matching the MVP's storage
 * pattern (see ARCHITECTURE-PHASE2.md "Storage layer decision" for why
 * Phase 2 stays with this instead of introducing Room). Two independent
 * blobs — the group list, and a contactId -> Set<groupId> membership map —
 * so looking up one contact's groups never requires deserializing every
 * group in the app.
 */
class SharedPrefsContactGroupRepository(context: Context) : ContactGroupRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val groupListType = object : TypeToken<List<ContactGroup>>() {}.type
    private val membershipType = object : TypeToken<MutableMap<String, MutableSet<String>>>() {}.type

    override suspend fun getGroups(): List<ContactGroup> = withContext(Dispatchers.IO) { readGroups() }

    override suspend fun getGroup(groupId: String): ContactGroup? =
        getGroups().firstOrNull { it.id == groupId }

    override suspend fun createGroup(group: ContactGroup) = withContext(Dispatchers.IO) {
        writeGroups(readGroups().filterNot { it.id == group.id } + group)
    }

    override suspend fun updateGroup(group: ContactGroup) = withContext(Dispatchers.IO) {
        writeGroups(readGroups().map { if (it.id == group.id) group else it })
    }

    override suspend fun deleteGroup(groupId: String) = withContext(Dispatchers.IO) {
        writeGroups(readGroups().filterNot { it.id == groupId })
        val membership = readMembership()
        membership.values.forEach { it.remove(groupId) }
        writeMembership(membership)
    }

    override suspend fun getGroupIdsForContact(contactId: String): Set<String> =
        withContext(Dispatchers.IO) { readMembership()[contactId]?.toSet() ?: emptySet() }

    override suspend fun getContactIdsInGroup(groupId: String): Set<String> =
        withContext(Dispatchers.IO) { readMembership().filterValues { groupId in it }.keys }

    override suspend fun addContactToGroup(contactId: String, groupId: String) = withContext(Dispatchers.IO) {
        val membership = readMembership()
        membership.getOrPut(contactId) { mutableSetOf() }.add(groupId)
        writeMembership(membership)
    }

    override suspend fun removeContactFromGroup(contactId: String, groupId: String) = withContext(Dispatchers.IO) {
        val membership = readMembership()
        membership[contactId]?.remove(groupId)
        writeMembership(membership)
    }

    private fun readGroups(): List<ContactGroup> {
        val json = prefs.getString(KEY_GROUPS, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<ContactGroup>>(json, groupListType) }.getOrDefault(emptyList())
    }

    private fun writeGroups(groups: List<ContactGroup>) {
        prefs.edit().putString(KEY_GROUPS, gson.toJson(groups)).apply()
    }

    private fun readMembership(): MutableMap<String, MutableSet<String>> {
        val json = prefs.getString(KEY_MEMBERSHIP, null) ?: return mutableMapOf()
        return runCatching { gson.fromJson<MutableMap<String, MutableSet<String>>>(json, membershipType) }
            .getOrDefault(mutableMapOf())
    }

    private fun writeMembership(membership: Map<String, Set<String>>) {
        prefs.edit().putString(KEY_MEMBERSHIP, gson.toJson(membership)).apply()
    }
}
