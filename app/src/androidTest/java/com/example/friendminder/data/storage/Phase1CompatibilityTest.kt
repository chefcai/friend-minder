package com.example.friendminder.data.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Migration" test for FRM-31's backward-compatibility requirement. Phase 2
 * storage is purely additive (see ARCHITECTURE-PHASE2.md "Migration
 * strategy") — there's no schema transform to run, so this instead verifies
 * the actual contract: pre-existing Phase 1 SharedPreferences data is
 * untouched and still readable once Phase 2 repositories exist in the app,
 * and those new repositories return safe, empty defaults for a contact that
 * predates Phase 2 entirely.
 */
@RunWith(AndroidJUnit4::class)
class Phase1CompatibilityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(
            "friend_minder_prefs",
            "friend_minder_cooldowns",
            "friend_minder_groups",
            "friend_minder_reminder_frequency",
            "friend_minder_special_dates",
            "friend_minder_outreach_log",
            "friend_minder_statistics_cache",
            "friend_minder_contact_methods",
            "friend_minder_schema"
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun mvpFriendListAndCooldownDataSurvivesPhase2CodeBeingPresent() = runBlocking {
        // Simulate a Phase 1 install: write the MVP's exact JSON shape directly, the way
        // SharedPrefsFriendListRepository/SharedPrefsCooldownRepository would have, with no
        // Phase 2 code ever having run.
        val legacyFriendListJson = """[{"id":"legacy-1","name":"Alice","phoneNumber":"555-0100","photoUri":null}]"""
        context.getSharedPreferences("friend_minder_prefs", Context.MODE_PRIVATE)
            .edit().putString("friend_list_json", legacyFriendListJson).apply()
        context.getSharedPreferences("friend_minder_cooldowns", Context.MODE_PRIVATE)
            .edit().putLong("last_suggested_legacy-1", 12345L).apply()

        // MVP repositories still read it fine.
        val friendListRepository = SharedPrefsFriendListRepository(context)
        val friends = friendListRepository.getFriendList()
        assertEquals(1, friends.size)
        assertEquals("Alice", friends.first().name)

        val cooldownRepository = SharedPrefsCooldownRepository(context)
        assertEquals(12345L, cooldownRepository.getLastSuggestion("legacy-1"))
        // New in Phase 2, absent from the legacy data — must default safely, not crash.
        assertEquals(0, cooldownRepository.getReminderCount("legacy-1"))

        // Phase 2 repositories, seeing this same pre-existing contact for the first time,
        // return empty/null defaults rather than fabricating data.
        val groupRepository = SharedPrefsContactGroupRepository(context)
        assertTrue(groupRepository.getGroupIdsForContact("legacy-1").isEmpty())

        val frequencyRepository = SharedPrefsReminderFrequencyRepository(context)
        assertNull(frequencyRepository.getOverride("legacy-1"))

        val specialDateRepository = SharedPrefsSpecialDateRepository(context)
        assertTrue(specialDateRepository.getForContact("legacy-1").isEmpty())

        val outreachLogRepository = SharedPrefsOutreachLogRepository(context)
        assertTrue(outreachLogRepository.getForContact("legacy-1").isEmpty())

        // FRM-183, same rule: a contact that predates the SMS/Call preference
        // store reads back as "never set" (null), not a fabricated default -
        // getEffectiveMethod is what resolves that to SMS for callers.
        val contactMethodRepository = SharedPrefsContactMethodRepository(context)
        assertNull(contactMethodRepository.getMethod("legacy-1"))
    }

    @Test
    fun schemaVersionDefaultsToMvpForPreExistingInstallThenAdvances() {
        // No schema_version key written yet -> looks exactly like a Phase 1 install.
        assertEquals(SchemaVersion.MVP, SchemaVersion.current(context))

        SchemaVersion.markCurrent(context, SchemaVersion.PHASE_2)
        assertEquals(SchemaVersion.PHASE_2, SchemaVersion.current(context))
    }
}
