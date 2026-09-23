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
     * FRM-175 / GR-1: all 8 retired group colours remap to their identity
     * colour; an unknown colour is left unchanged; membership rows survive
     * (the migration is an in-place UPDATE, not a delete/re-insert).
     */
    @Test
    fun migrate2To3_remapsAllEightOldGroupColoursAndLeavesUnknownUnchanged() {
        val unknown = -65536 // #FF0000, not in the old palette
        val oldToNew = AppDatabase.GROUP_COLOR_REMAP_2_3
        assertEquals(8, oldToNew.size)

        helper.createDatabase(TEST_DB, 2).apply {
            oldToNew.keys.forEachIndexed { i, old ->
                execSQL(
                    "INSERT INTO contact_groups (id, name, color, icon, createdAt, reminderFrequencyDays) " +
                        "VALUES ('g$i', 'Group $i', $old, NULL, 1000, 14)"
                )
                execSQL("INSERT INTO contact_group_membership (contactId, groupId) VALUES ('c$i', 'g$i')")
            }
            execSQL(
                "INSERT INTO contact_groups (id, name, color, icon, createdAt, reminderFrequencyDays) " +
                    "VALUES ('gx', 'Unknown', $unknown, NULL, 1000, NULL)"
            )
            execSQL("INSERT INTO contact_group_membership (contactId, groupId) VALUES ('cx', 'gx')")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        oldToNew.entries.forEachIndexed { i, (old, new) ->
            migrated.query("SELECT color, name, reminderFrequencyDays FROM contact_groups WHERE id = 'g$i'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("old colour $old", new, c.getInt(0))
                assertEquals("Group $i", c.getString(1))
                assertEquals(14, c.getInt(2))
            }
        }
        migrated.query("SELECT color FROM contact_groups WHERE id = 'gx'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(unknown, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM contact_group_membership").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("membership rows must survive the colour remap", 9, c.getInt(0))
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
            assertEquals(-6501396, c.getInt(0)) // Mist -> Sky
        }
    }
}
