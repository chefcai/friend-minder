package com.example.friendminder.data.storage

import android.content.Context

private const val PREFS_NAME = "friend_minder_schema"
private const val KEY_VERSION = "version"

/**
 * The MVP never had an explicit schema version. Phase 2 introduces one
 * purely so a *future* phase has something to branch a real migration on —
 * see ARCHITECTURE-PHASE2.md "Migration strategy" for why Phase 2 itself
 * doesn't need one: its storage is entirely additive (new SharedPreferences
 * files), so no existing MVP data is ever read, rewritten, or otherwise
 * touched by this version bump.
 */
object SchemaVersion {
    const val MVP = 1
    const val PHASE_2 = 2

    /**
     * FRM-81: the one-time copy of OutreachLog/ContactGroup/SpecialDate data
     * from SharedPreferences+JSON into Room. See [com.example.friendminder.data.storage.RoomMigration].
     */
    const val ROOM_MIGRATION_V1 = 3

    fun current(context: Context): Int =
        prefs(context).getInt(KEY_VERSION, MVP)

    fun markCurrent(context: Context, version: Int) {
        prefs(context).edit().putInt(KEY_VERSION, version).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
