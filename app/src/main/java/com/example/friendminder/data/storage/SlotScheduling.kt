package com.example.friendminder.data.storage

import java.util.Calendar

/**
 * Pure time-math for FRM-8, factored out of [WorkManagerNotificationScheduler]
 * so it is unit-testable without WorkManager. All times are 24h device-local
 * hour/minute pairs. [delayMillisUntil] takes "now" as a parameter (rather than
 * reading the system clock itself) specifically so it stays unit-testable too.
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

    /**
     * Milliseconds from [nowMillis] until the next occurrence of ([hour], [minute]).
     * Rolls over to tomorrow if that time has already passed today, or
     * unconditionally when [forceNextDay] is true.
     *
     * [forceNextDay] exists for FRM-77: a random-mode slot re-arms itself right
     * after it fires by drawing a fresh random time from the *full* window
     * ([randomTimeInWindow]) and scheduling it here. Without forcing next-day,
     * a freshly-drawn time that hasn't happened yet today would fire again
     * later the *same* day the slot already fired, producing an extra,
     * unexpected notification.
     */
    fun delayMillisUntil(hour: Int, minute: Int, nowMillis: Long, forceNextDay: Boolean = false): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (forceNextDay || before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    /**
     * Whether the next occurrence of ([hour], [minute]) from [nowMillis]
     * falls later today (true) or has already passed and will next occur
     * tomorrow (false). Same "has this passed today" check [delayMillisUntil]
     * already does, pulled out standalone for SettingsFragment's "next
     * reminder" preview notice (GH #150 follow-up) - re-saving the reminder
     * time on the same day it already fired legitimately schedules another
     * one later today rather than being silently blocked, so this lets the
     * screen say which one is about to happen.
     */
    fun occursLaterToday(hour: Int, minute: Int, nowMillis: Long): Boolean {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return !target.before(now)
    }
}
