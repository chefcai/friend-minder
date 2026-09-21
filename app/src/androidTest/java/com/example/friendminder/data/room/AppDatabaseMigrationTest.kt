package com.example.friendminder.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
