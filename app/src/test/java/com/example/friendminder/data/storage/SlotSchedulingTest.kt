package com.example.friendminder.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class SlotSchedulingTest {

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
}
