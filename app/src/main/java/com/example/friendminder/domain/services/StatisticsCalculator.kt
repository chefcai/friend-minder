package com.example.friendminder.domain.services

import java.util.concurrent.TimeUnit

/**
 * Pure statistics math, factored out of [DefaultStatisticsService] so it's
 * unit-testable without Android or repository dependencies — the same
 * pattern as [com.example.friendminder.work.SuggestionSelector] and
 * [com.example.friendminder.data.storage.SlotScheduling].
 */
object StatisticsCalculator {

    fun daysSince(timestamp: Long, now: Long): Int =
        ((now - timestamp) / TimeUnit.DAYS.toMillis(1)).toInt()

    /**
     * Length of the trailing (most recent) run of [timestampsNewestFirst]
     * (sorted newest first) whose consecutive gaps never exceed
     * [frequencyDays] + 1 day of grace (PRD glossary: "Streak" = consecutive
     * days reminded/logged without exceeding the reminder frequency gap).
     * 0 for an empty list; a single entry is a streak of 1.
     */
    fun streak(timestampsNewestFirst: List<Long>, frequencyDays: Int): Int {
        if (timestampsNewestFirst.isEmpty()) return 0
        val maxGapMillis = TimeUnit.DAYS.toMillis((frequencyDays + 1).toLong())
        var result = 1
        for (i in 0 until timestampsNewestFirst.size - 1) {
            val gap = timestampsNewestFirst[i] - timestampsNewestFirst[i + 1]
            if (gap <= maxGapMillis) result++ else break
        }
        return result
    }

    /**
     * Percentage (0-100, not a 0-1 fraction) of [remindersSent] that
     * resulted in a logged/sent outreach ([outreachCount]) — PRD glossary
     * "Reach Rate". 0 if no reminders were ever sent.
     */
    fun reachRate(outreachCount: Int, remindersSent: Int): Float {
        if (remindersSent <= 0) return 0f
        return (outreachCount.coerceAtMost(remindersSent).toFloat() / remindersSent) * 100f
    }

    /** Lower-median of [values] (picks the upper of the two middle values on an even-sized list), or null if empty. */
    fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}
