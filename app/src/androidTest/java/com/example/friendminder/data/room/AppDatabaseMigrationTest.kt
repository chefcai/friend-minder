package com.example.friendminder.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

/**
 * GH #121 / FRM-97: verifies [AppDatabase.MIGRATION_1_2] adds the nullable
 * `reminderFrequencyDays` column onto an existing v1 `contact_groups` table
 * without touching any pre-existing row - the column must come back NULL for
 * a group that existed before this migration ran, which is exactly "this
 * group doesn't override the interval" and needs no backfill step.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_preservesExistingGroupRowsWithNullNewColumn() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO contact_groups (id, name, color, icon, createdAt) " +
                    "VALUES ('g1', 'Close Friends', -16711936, NULL, 1000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        migrated.query("SELECT id, name, reminderFrequencyDays FROM contact_groups WHERE id = 'g1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertFalse("pre-existing row must migrate to NULL, not 0 or any other sentinel", run {
                val colIndex = cursor.getColumnIndexOrThrow("reminderFrequencyDays")
                !cursor.isNull(colIndex)
            })
        }
    }

    /**
     * FRM-180 / GR-1: after 2->3 every group is Pine, whatever it stored
     * before (all 8 old palette colours, an unknown colour, and a colour that
     * is already Pine); names and intervals are kept and every membership row
     * survives (the migration is an in-place UPDATE, not a delete/re-insert).
     */
    @Test
    fun migrate2To3_setsEveryGroupToPineAndKeepsMemberships() {
        val pine = AppDatabase.MIGRATED_GROUP_COLOR
        val stored = listOf(
            -15498893, -15424581, -8141835, -15505049, // Teal, Cyan, Sky, Deep Teal
            -12805985, -14196108, -3350295, -15390165, // Slate, Ink Blue, Mist, Midnight
            -65536, // unknown (#FF0000)
            pine // already Pine
        )
        val membersPerGroup = 3

        helper.createDatabase(TEST_DB, 2).apply {
            stored.forEachIndexed { i, color ->
                execSQL(
                    "INSERT INTO contact_groups (id, name, color, icon, createdAt, reminderFrequencyDays) " +
                        "VALUES ('g$i', 'Group $i', $color, NULL, 1000, ${if (i % 2 == 0) "14" else "NULL"})"
                )
                repeat(membersPerGroup) { m ->
                    execSQL("INSERT INTO contact_group_membership (contactId, groupId) VALUES ('c$i-$m', 'g$i')")
                }
            }
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        stored.indices.forEach { i ->
            migrated.query("SELECT color, name, reminderFrequencyDays FROM contact_groups WHERE id = 'g$i'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("group g$i (was ${stored[i]})", pine, c.getInt(0))
                assertEquals("Group $i", c.getString(1))
                if (i % 2 == 0) assertEquals(14, c.getInt(2)) else assertTrue(c.isNull(2))
            }
        }
        migrated.query("SELECT COUNT(*) FROM contact_groups WHERE color != $pine").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        migrated.query("SELECT groupId, COUNT(*) FROM contact_group_membership GROUP BY groupId").use { c ->
            var groups = 0
            while (c.moveToNext()) {
                groups++
                assertEquals("members of ${c.getString(0)}", membersPerGroup, c.getInt(1))
            }
            assertEquals("membership rows must survive the colour reset", stored.size, groups)
        }
    }

    @Test
    fun migrate1To3_fullChainValidatesSchema() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO contact_groups (id, name, color, icon, createdAt) " +
                    "VALUES ('g1', 'Mist group', -3350295, NULL, 1000)"
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB, 3, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3
        )
        migrated.query("SELECT color FROM contact_groups WHERE id = 'g1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(AppDatabase.MIGRATED_GROUP_COLOR, c.getInt(0)) // Mist -> Pine
        }
    }
}
