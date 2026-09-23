package com.example.friendminder.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * FRM-81's partial Room migration: only [OutreachLogEntity],
 * [ContactGroupEntity]/[ContactGroupMembershipEntity], and [SpecialDateEntity]
 * live here. Settings, Cooldown, ReminderFrequency, StatisticsCache, and the
 * Contact/FriendList data stay on SharedPreferences+JSON — see FRM-81's
 * decision doc / ARCHITECTURE-PHASE2.md for why this split was scoped this way.
 */
@Database(
    entities = [
        OutreachLogEntity::class,
        ContactGroupEntity::class,
        ContactGroupMembershipEntity::class,
        SpecialDateEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun outreachLogDao(): OutreachLogDao
    abstract fun contactGroupDao(): ContactGroupDao
    abstract fun specialDateDao(): SpecialDateDao

    companion object {
        private const val DB_NAME = "friend_minder.db"

        /**
         * GH #121 / FRM-97: adds the nullable per-group reminder-interval
         * override column. `NULL` for every existing row is exactly "this
         * group doesn't override the interval" — no backfill needed, and no
         * existing row's meaning changes.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE contact_groups ADD COLUMN reminderFrequencyDays INTEGER DEFAULT NULL"
                )
            }
        }

        /**
         * FRM-175 / GR-1: remaps every stored `contact_groups.color` from the
         * retired 8-colour group palette onto the shared 8-colour identity
         * palette (ui-ux-audit-findings.md, "Group migration, Room v2->v3").
         * Values are signed ARGB Ints, exactly as [ContactGroupEntity.color]
         * stores them. Unknown values are left unchanged (the UI maps them to
         * the nearest identity colour at render time).
         *
         * A single in-place `UPDATE ... CASE` - never a delete/re-insert -
         * so the `ON DELETE CASCADE` membership rows are untouched
         * (PHASE-3-LESSONS: `INSERT OR REPLACE` wipes group memberships).
         */
        val GROUP_COLOR_REMAP_2_3: Map<Int, Int> = mapOf(
            -15498893 to -13865399, // Teal      #138173 -> Pine    #2C6E49
            -15424581 to -16028014, // Cyan      #14A3BB -> Cyan    #0B6E92
            -8141835 to -6501396, //   Sky       #83C3F5 -> Sky     #9CCBEC
            -15505049 to -13865399, // Deep Teal #136967 -> Pine    #2C6E49
            -12805985 to -7417660, //  Slate     #3C989F -> Seafoam #8ED0C4
            -14196108 to -16028014, // Ink Blue  #276274 -> Cyan    #0B6E92
            -3350295 to -6501396, //   Mist      #CCE0E9 -> Sky     #9CCBEC
            -15390165 to -16028014 //  Midnight  #152A2B -> Cyan    #0B6E92
        )

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cases = GROUP_COLOR_REMAP_2_3.entries.joinToString(" ") { (old, new) ->
                    "WHEN $old THEN $new"
                }
                val keys = GROUP_COLOR_REMAP_2_3.keys.joinToString(",")
                db.execSQL(
                    "UPDATE contact_groups SET color = CASE color $cases ELSE color END " +
                        "WHERE color IN ($keys)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }

        /**
         * Test-only: closes and drops the cached singleton so each androidTest
         * gets a fresh connection to a freshly-deleted `friend_minder.db` file,
         * instead of silently reusing whatever a prior test in the same
         * instrumentation process already opened.
         */
        @androidx.annotation.VisibleForTesting
        fun resetForTesting(context: Context) {
            synchronized(this) {
                instance?.close()
                instance = null
            }
            context.applicationContext.deleteDatabase(DB_NAME)
        }
    }
}
