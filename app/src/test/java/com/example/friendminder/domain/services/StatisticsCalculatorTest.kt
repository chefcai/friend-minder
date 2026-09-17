package com.example.friendminder.domain.services

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [StatisticsCalculator] (FRM-45): pure math with no Android
 * or repository dependencies, factored out of [DefaultStatisticsService]
 * specifically so it's testable in plain JUnit like this. Covers the
 * `daysSince`/`streak`/`reachRate`/`median` functions the PRD's
 * lastContacted/daysSinceContact/streak/reachRate stats are built from -
 * `lastContacted` itself is just `history.firstOrNull()?.timestamp` inline
 * in [DefaultStatisticsService.compute] (no math to test there).
 */
class StatisticsCalculatorTest {

    private val oneDayMillis = TimeUnit.DAYS.toMillis(1)

    // --- daysSince ---

    @Test
    fun `daysSince is zero for the same instant`() {
        val now = 1_000_000_000L
        assertEquals(0, StatisticsCalculator.daysSince(timestamp = now, now = now))
    }

    @Test
    fun `daysSince truncates a partial day rather than rounding`() {
        val now = 10 * oneDayMillis
        val timestamp = now - (oneDayMillis - 1) // 23h59m59.999s ago
        assertEquals(0, StatisticsCalculator.daysSince(timestamp, now))
    }

    @Test
    fun `daysSince counts whole elapsed days`() {
        val now = 10 * oneDayMillis
        val timestamp = now - (3 * oneDayMillis)
        assertEquals(3, StatisticsCalculator.daysSince(timestamp, now))
    }

    // --- streak ---

    @Test
    fun `streak is zero for an empty history`() {
        assertEquals(0, StatisticsCalculator.streak(emptyList(), frequencyDays = 7))
    }

    @Test
    fun `streak is one for a single entry`() {
        assertEquals(1, StatisticsCalculator.streak(listOf(5_000L), frequencyDays = 7))
    }

    @Test
    fun `streak counts consecutive entries within the frequency-plus-grace gap`() {
        // frequencyDays=3 -> max gap is 4 days; three entries each exactly
        // 4 days apart (newest first) should all count.
        val newest = 100 * oneDayMillis
        val timestamps = listOf(newest, newest - 4 * oneDayMillis, newest - 8 * oneDayMillis)
        assertEquals(3, StatisticsCalculator.streak(timestamps, frequencyDays = 3))
    }

    @Test
    fun `streak stops at the first gap that exceeds the frequency-plus-grace window`() {
        val newest = 100 * oneDayMillis
        val timestamps = listOf(
            newest,
            newest - 2 * oneDayMillis, // within a 1-day (frequency 0 + 1 grace) window? no - see next
            newest - 10 * oneDayMillis
        )
        // frequencyDays=1 -> max gap 2 days: entry 0->1 gap is 2 days (counts),
        // entry 1->2 gap is 8 days (breaks) - so streak stops at 2, not 3.
        assertEquals(2, StatisticsCalculator.streak(timestamps, frequencyDays = 1))
    }

    @Test
    fun `streak includes a gap exactly at the boundary`() {
        val newest = 100 * oneDayMillis
        // frequencyDays=2 -> max gap is exactly 3 days; a gap of precisely
        // 3 days should still count (the function uses <=, not <).
        val timestamps = listOf(newest, newest - 3 * oneDayMillis)
        assertEquals(2, StatisticsCalculator.streak(timestamps, frequencyDays = 2))
    }

    // --- reachRate ---

    @Test
    fun `reachRate is zero when no reminders were sent`() {
        assertEquals(0f, StatisticsCalculator.reachRate(outreachCount = 5, remindersSent = 0), 0f)
    }

    @Test
    fun `reachRate is zero when remindersSent is negative`() {
        assertEquals(0f, StatisticsCalculator.reachRate(outreachCount = 0, remindersSent = -1), 0f)
    }

    @Test
    fun `reachRate computes a percentage not a fraction`() {
        assertEquals(50f, StatisticsCalculator.reachRate(outreachCount = 5, remindersSent = 10), 0f)
    }

    @Test
    fun `reachRate is capped at 100 when outreachCount exceeds remindersSent`() {
        // Can happen if a contact is logged as reached outside the reminder
        // flow (e.g. an ad-hoc outreach log entry) more often than reminders
        // were actually sent - shouldn't report over 100%.
        assertEquals(100f, StatisticsCalculator.reachRate(outreachCount = 20, remindersSent = 10), 0f)
    }

    // --- median ---

    @Test
    fun `median is null for an empty list`() {
        assertNull(StatisticsCalculator.median(emptyList()))
    }

    @Test
    fun `median of an odd-sized list is the middle value`() {
        assertEquals(3, StatisticsCalculator.median(listOf(5, 1, 3, 2, 4)))
    }

    @Test
    fun `median of an even-sized list is the upper middle value`() {
        // Sorted: 1, 2, 3, 4 - documented behavior picks the upper of the
        // two middle values, i.e. 3 (index size/2 = 2), not the lower (2).
        assertEquals(3, StatisticsCalculator.median(listOf(4, 1, 3, 2)))
    }

    @Test
    fun `median handles a single-element list`() {
        assertEquals(42, StatisticsCalculator.median(listOf(42)))
    }
}
