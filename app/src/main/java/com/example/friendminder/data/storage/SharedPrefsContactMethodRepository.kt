package com.example.friendminder.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.friendminder.data.models.ContactMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "friend_minder_contact_methods"
private const val KEY_PREFIX = "method_"

/**
 * SharedPreferences-backed [ContactMethodRepository] (FRM-183) — same shape
 * as [SharedPrefsReminderFrequencyRepository]: one prefix-keyed primitive per
 * contact, no JSON blob, nothing to migrate for pre-existing installs (a
 * missing key just reads back as null, per [getMethod]).
 */
class SharedPrefsContactMethodRepository(context: Context) : ContactMethodRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun getMethod(contactId: String): ContactMethod? = withContext(Dispatchers.IO) {
        val stored = prefs.getString(KEY_PREFIX + contactId, null) ?: return@withContext null
        // Defensive against a future enum rename/removal leaving a stale value behind
        // rather than crashing readers - "unrecognized" collapses to "never set".
        runCatching { ContactMethod.valueOf(stored) }.getOrNull()
    }

    override suspend fun setMethod(contactId: String, method: ContactMethod) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_PREFIX + contactId, method.name).apply()
    }

    override suspend fun clearMethod(contactId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_PREFIX + contactId).apply()
    }
}
