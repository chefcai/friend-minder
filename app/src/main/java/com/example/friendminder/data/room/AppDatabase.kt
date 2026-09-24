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
         * FRM-180 / GR-1 (Cai, 2026-09-24, on FRM-177): every stored group
         * colour becomes Pine #2C6E49 (fm_identity_1, the first swatch and the
         * New-group default); users re-pick from the identity palette. This
         * replaces FRM-175's nearest-colour remap, which collapsed the 8 old
         * colours onto only 4 fills. Signed ARGB Int, as
         * [ContactGroupEntity.color] stores it.
         */
        const val MIGRATED_GROUP_COLOR: Int = -13865399

        /**
         * A single in-place `UPDATE` over every row - never a delete/re-insert -
         * so the `ON DELETE CASCADE` membership rows are untouched
         * (PHASE-3-LESSONS: `INSERT OR REPLACE` wipes group memberships).
         * Devices that already ran the FRM-175 remap are not re-migrated.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE contact_groups SET color = $MIGRATED_GROUP_COLOR")
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
