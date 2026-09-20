package com.example.friendminder.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.friendminder.data.models.OutreachLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "friend_minder_outreach_log"
private const val KEY_LOGS = "logs_json"

/**
 * FRM-48 (performance testing, stats cache): [getAll] used to re-read and
 * re-parse the entire logs_json blob with Gson on *every* call, including
 * every call [getForContact] makes on your behalf. That's harmless for one
 * contact, but [DefaultStatisticsService.getAggregateStatistics] calls
 * [getForContact] (via `getHistory`) once per friend in the friend list, plus
 * one more [getAll] of its own for the monthly count - so a single Dashboard
 * load with a cold stats cache does (friend count + 1) full JSON
 * deserializations of the *entire* outreach history, not just the one
 * contact's slice each call actually needed. That's O(friends x total log
 * count), and it gets worse the longer someone's used the app (more logs)
 * and the more friends they track - exactly the kind of thing that's fine
 * in dev with a handful of seeded contacts and only shows up as real jank
 * once a user has months of history.
 *
 * Fix: cache the parsed list in memory for this repository's lifetime
 * (it's a ServiceLocator singleton, so that's the process lifetime),
 * invalidated on every write. Reads become a single parse per cold start
 * (or per write), not per call.
 *
 * One more thing worth calling out since it wasn't obvious until measuring
 * on-device: [getAll] originally *always* hopped onto [Dispatchers.IO],
 * even on a cache hit that's just a volatile field read. That dispatcher
 * hop has real, measurable overhead of its own (single-digit milliseconds
 * on the emulator, from thread-pool scheduling, not the work itself) - at
 * 150 calls that overhead alone dominated the total, even after the Gson
 * re-parse was gone. [getAll] now checks the cache *before* dispatching, so
 * a warm cache never touches `Dispatchers.IO` at all.
 */
class SharedPrefsOutreachLogRepository(context: Context) : OutreachLogRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<OutreachLog>>() {}.type

    // Guards read-modify-write on [cachedLogs] so two concurrent add()/delete()
    // calls can't race and drop one write (last-writer-wins on the SharedPreferences
    // commit was already a pre-existing risk; this at least keeps the in-memory
    // cache from disagreeing with what actually got persisted).
    private val mutex = Mutex()

    @Volatile
    private var cachedLogs: List<OutreachLog>? = null

    override suspend fun getAll(): List<OutreachLog> {
        cachedLogs?.let { return it }
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                cachedLogs ?: readAll().also { cachedLogs = it }
            }
        }
    }

    override suspend fun getForContact(contactId: String): List<OutreachLog> =
        getAll().filter { it.contactId == contactId }

    override suspend fun add(log: OutreachLog) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = readAll() + log
            writeAll(updated)
            cachedLogs = updated
        }
    }

    override suspend fun delete(logId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = readAll().filterNot { it.id == logId }
            writeAll(updated)
            cachedLogs = updated
        }
    }

    private fun readAll(): List<OutreachLog> {
        val json = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<OutreachLog>>(json, listType) }.getOrDefault(emptyList())
    }

    private fun writeAll(logs: List<OutreachLog>) {
        prefs.edit().putString(KEY_LOGS, gson.toJson(logs)).apply()
    }
}
