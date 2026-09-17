package com.example.friendminder.data.storage

/**
 * Pure time-math for FRM-8, factored out of [WorkManagerNotificationScheduler]
 * so it is unit-testable without WorkManager/Calendar. All times are 24h
 * device-local hour/minute pairs.
 */
object SlotScheduling {

    private const val MINUTES_PER_DAY = 24 * 60

    /**
     * Spreads [totalSlots] notification times evenly across the day starting
     * at ([baseHour], [baseMinute]). E.g. totalSlots=3 starting at 9:00 gives
     * roughly 9:00, 17:00, 1:00 (8h apart, wrapping past midnight).
     */
    fun spreadSlotTime(baseHour: Int, baseMinute: Int, slot: Int, totalSlots: Int): Pair<Int, Int> {
        require(totalSlots > 0) { "totalSlots must be positive" }
        val baseMinutes = baseHour * 60 + baseMinute
        val offsetMinutes = slot * (MINUTES_PER_DAY / totalSlots)
        val slotTotalMinutes = (baseMinutes + offsetMinutes) % MINUTES_PER_DAY
        return (slotTotalMinutes / 60) to (slotTotalMinutes % 60)
    }

    /**
     * Picks a random hour/minute within [startHour, endHour) (wrapping past
     * midnight if endHour <= startHour), using [randomMinuteOfWindow] to draw
     * the offset — inject a fixed value in tests for determinism. Falls back
     * to a minimal one-hour window if start == end, rather than a zero-width
     * window that would crash the random draw.
     */
    fun randomTimeInWindow(startHour: Int, endHour: Int, randomMinuteOfWindow: (Int) -> Int): Pair<Int, Int> {
        val windowMinutes = (((endHour - startHour + 24) % 24).coerceAtLeast(1)) * 60
        val offsetMinutes = randomMinuteOfWindow(windowMinutes)
        val targetHour = (startHour + offsetMinutes / 60) % 24
        val targetMinute = offsetMinutes % 60
        return targetHour to targetMinute
    }
}
