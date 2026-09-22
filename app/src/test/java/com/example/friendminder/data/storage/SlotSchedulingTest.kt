package com.example.friendminder.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SlotSchedulingTest {

    /** Builds an epoch-millis timestamp for a specific device-local date/time, for deterministic "now" fixtures. */
    private fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `single slot keeps the base time unchanged`() {
        val (hour, minute) = SlotScheduling.spreadSlotTime(baseHour = 9, baseMinute = 30, slot = 0, totalSlots = 1)
        assertEquals(9, hour)
        assertEquals(30, minute)
    }

    @Test
    fun `three slots starting at 9am are spread evenly across the day`() {
        val slot0 = SlotScheduling.spreadSlotTime(9, 0, slot = 0, totalSlots = 3)
        val slot1 = SlotScheduling.spreadSlotTime(9, 0, slot = 1, totalSlots = 3)
        val slot2 = SlotScheduling.spreadSlotTime(9, 0, slot = 2, totalSlots = 3)

        assertEquals(9 to 0, slot0)
        assertEquals(17 to 0, slot1) // +8h
        assertEquals(1 to 0, slot2)  // +16h, wraps past midnight
    }

    @Test
    fun `slot offset wraps past midnight correctly`() {
        val (hour, minute) = SlotScheduling.spreadSlotTime(baseHour = 22, baseMinute = 0, slot = 1, totalSlots = 2)
        // +12h from 22:00 -> 10:00 the next day
        assertEquals(10, hour)
        assertEquals(0, minute)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero total slots is rejected`() {
        SlotScheduling.spreadSlotTime(9, 0, slot = 0, totalSlots = 0)
    }

    @Test
    fun `random window picks a time within the configured range`() {
        // Fixed "random" draw of 0 minutes into the window -> exactly startHour
        val (hour, minute) = SlotScheduling.randomTimeInWindow(startHour = 18, endHour = 21) { windowMinutes ->
            assertEquals(3 * 60, windowMinutes)
            0
        }
        assertEquals(18, hour)
        assertEquals(0, minute)
    }

    @Test
    fun `random window wraps past midnight when end is before start`() {
        // 22:00 -> 2:00 window is 4 hours (240 minutes)
        val (hour, minute) = SlotScheduling.randomTimeInWindow(startHour = 22, endHour = 2) { windowMinutes ->
            assertEquals(4 * 60, windowMinutes)
            180 // 3 hours into the window
        }
        assertEquals(1, hour) // 22:00 + 3h = 1:00
        assertEquals(0, minute)
    }

    @Test
    fun `equal start and end hour falls back to a minimal one-hour window`() {
        // Guards against a zero-width window that would crash the random draw.
        val (hour, minute) = SlotScheduling.randomTimeInWindow(startHour = 9, endHour = 9) { windowMinutes ->
            assertEquals(60, windowMinutes)
            0
        }
        assertEquals(9, hour)
        assertEquals(0, minute)
    }

    @Test
    fun `delayMillisUntil targets later today when the time has not passed yet`() {
        val now = millisAt(2026, 9, 20, hour = 10, minute = 0)
        val delay = SlotScheduling.delayMillisUntil(hour = 14, minute = 0, nowMillis = now)
        assertEquals(4 * 60 * 60 * 1000L, delay) // 4 hours from now, later today
    }

    @Test
    fun `delayMillisUntil rolls to tomorrow when the time has already passed today`() {
        val now = millisAt(2026, 9, 20, hour = 10, minute = 0)
        val delay = SlotScheduling.delayMillisUntil(hour = 9, minute = 0, nowMillis = now)
        assertEquals(23 * 60 * 60 * 1000L, delay) // 23 hours from now, tomorrow at 9:00
    }

    @Test
    fun `forceNextDay rolls to tomorrow even when the time has not passed yet today (FRM-77)`() {
        // This is the exact re-arm scenario from FRM-77 / GitHub #80: a random-mode
        // slot fires, then re-arms with a freshly drawn time that happens to still be
        // later today. Without forceNextDay this would return a same-day delay,
        // letting the slot fire twice in one day.
        val now = millisAt(2026, 9, 20, hour = 10, minute = 0)
        val delay = SlotScheduling.delayMillisUntil(hour = 14, minute = 0, nowMillis = now, forceNextDay = true)
        assertEquals(28 * 60 * 60 * 1000L, delay) // tomorrow at 14:00, not today at 14:00
    }

    @Test
    fun `forceNextDay still rolls to tomorrow when the time has already passed today`() {
        val now = millisAt(2026, 9, 20, hour = 10, minute = 0)
        val delayForced = SlotScheduling.delayMillisUntil(hour = 9, minute = 0, nowMillis = now, forceNextDay = true)
        val delayUnforced = SlotScheduling.delayMillisUntil(hour = 9, minute = 0, nowMillis = now, forceNextDay = false)
        // Already in the past today either way, so forcing changes nothing here.
        assertEquals(delayUnforced, delayForced)
    }

    @Test
    fun `delayMillisUntil is always positive`() {
        val now = millisAt(2026, 9, 20, hour = 23, minute = 59)
        val delay = SlotScheduling.delayMillisUntil(hour = 0, minute = 0, nowMillis = now)
        assertTrue(delay > 0)
    }

    @Test
    fun `occursLaterToday is true when the time has not passed yet today`() {
        val now = millisAt(2026, 9, 20, hour = 10, minute = 0)
        assertTrue(SlotScheduling.occursLaterToday(hour = 14, minute = 0, nowMillis = now))
    }

    @Test
    fun `occursLaterToday is false when the time has already passed today`() {
        val now = millisAt(2026, 9, 20, hour = 10, minute = 0)
        assertEquals(false, SlotScheduling.occursLaterToday(hour = 9, minute = 0, nowMillis = now))
    }

    @Test
    fun `occursLaterToday is true at the exact instant it targets`() {
        val now = millisAt(2026, 9, 20, hour = 10, minute = 0)
        assertTrue(SlotScheduling.occursLaterToday(hour = 10, minute = 0, nowMillis = now))
    }
}
