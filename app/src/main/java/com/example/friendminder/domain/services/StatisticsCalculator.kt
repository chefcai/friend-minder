package com.example.friendminder.domain.services

import com.example.friendminder.data.models.OutreachLog
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
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

    /**
     * Top [n] items by descending [key], with ties broken randomly rather than by
     * whatever incidental order [items] arrived in.
     *
     * Motivating case (dashboard "Needs attention"): every never-contacted contact
     * ties at the same "most neglected" key, and a plain `sortedByDescending` is
     * stable, so those ties would otherwise always resolve to insertion order (in
     * practice, whichever contacts were added to the Friend List earliest) -- the
     * same 5 names forever, regardless of how many other contacts are equally
     * neglected. Shuffling first, then doing a stable sort by [key], keeps the
     * *ordering between different keys* correct while randomizing which of several
     * tied items ends up in the visible top [n] on each call.
     *
     * [shuffle] is injectable (default: [List.shuffled]) purely for
     * unit-testability without relying on real randomness, matching
     * [com.example.friendminder.data.storage.SlotScheduling]'s
     * `randomMinuteOfWindow` pattern.
     */
    fun <T> topNRandomizedTies(
        items: List<T>,
        n: Int,
        shuffle: (List<T>) -> List<T> = List<T>::shuffled,
        key: (T) -> Int
    ): List<T> = shuffle(items).sortedByDescending(key).take(n)

    /** Epoch millis of 00:00 on the 1st of the calendar month containing [now], in [zone]. */
    fun startOfMonthMillis(now: Long, zone: ZoneId): Long =
        monthStart(now, zone).toInstant().toEpochMilli()

    /**
     * Number of distinct contacts in [trackedContactIds] with at least one
     * log in the calendar month containing [now], in [zone] (FRM-169 / OH-1:
     * "contacts reached this month" counts people, not outreach events).
     * The window is [start of month, start of next month); logs for
     * untracked (removed or never-added) contacts are ignored.
     */
    fun distinctContactsReachedInMonth(
        logs: List<OutreachLog>,
        trackedContactIds: Set<String>,
        now: Long,
        zone: ZoneId
    ): Int {
        val start = monthStart(now, zone)
        val startMillis = start.toInstant().toEpochMilli()
        val endMillis = start.plusMonths(1).toInstant().toEpochMilli()
        return logs.asSequence()
            .filter { it.timestamp in startMillis until endMillis }
            .map { it.contactId }
            .filter { it in trackedContactIds }
            .distinct()
            .count()
    }

    private fun monthStart(now: Long, zone: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
}
