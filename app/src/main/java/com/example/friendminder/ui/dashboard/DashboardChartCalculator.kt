package com.example.friendminder.ui.dashboard

/**
 * Pure math for the "Monthly outreach" bar chart (FRM-55; DESIGN-SYSTEM-PHASE2.md
 * §4.6), factored out of [DashboardFragment] so it's unit-testable without
 * Android framework or a fake [com.example.friendminder.domain.services.OutreachLogService] —
 * same rationale as [com.example.friendminder.domain.services.StatisticsCalculator]
 * (FRM-45).
 *
 * [OutreachLogService.countSince][com.example.friendminder.domain.services.OutreachLogService.countSince]
 * only exposes a cumulative "count since X" query, not per-bucket counts, so
 * the Fragment gathers `CHART_WEEKS + 1` cumulative counts at weekly
 * boundaries (now, -7d, -14d, ...) and this function turns that into
 * per-week counts, oldest week first (left-to-right chart order).
 */
object DashboardChartCalculator {

    /**
     * @param cumulativeCounts counts from each week boundary to now, most
     *   recent boundary (0 days ago) first — i.e. `cumulativeCounts[i]` is
     *   `outreachLogService.countSince(now - i * 7 days)`. Since `countSince`
     *   counts everything at or after its cutoff, a *later* cutoff (larger
     *   `i`, further into the past) can only match as many or more logs than
     *   an earlier one — this list is non-decreasing as `i` grows (e.g.
     *   `[0, 1, 3, 4, 4]`), never the reverse. Must have at least 2 entries;
     *   returns one fewer bucket than there are boundaries.
     */
    fun weeklyBuckets(cumulativeCounts: List<Int>): List<Int> {
        require(cumulativeCounts.size >= 2) { "need at least 2 cumulative counts to derive 1 weekly bucket" }
        // cumulativeCounts[it + 1] (the wider, further-back window) minus
        // cumulativeCounts[it] (the narrower one) isolates just the logs that
        // fall in that week — cumulativeCounts[it] - cumulativeCounts[it + 1]
        // (the previous version of this code) computed it backwards and
        // produced negative/zero buckets for every real call site, which
        // BarChartView then silently failed to render as visible bars
        // (FRM-77-adjacent dashboard bug, found via Cai's "chart looks empty
        // despite non-zero Monthly outreach count" report).
        val perWeek = (0 until cumulativeCounts.size - 1).map { cumulativeCounts[it + 1] - cumulativeCounts[it] }
        return perWeek.reversed()
    }

    /**
     * How many weeks ago each bar in a [weeklyBuckets]-shaped list represents,
     * oldest bar first (index 0) to the current week last (`0`) - e.g.
     * `[3, 2, 1, 0]` for 4 bars. Pure index math, split out so
     * [DashboardFragment][com.example.friendminder.ui.dashboard.DashboardFragment]
     * can turn it into localized axis-label strings ("This wk", "2wk ago", ...)
     * without duplicating the oldest-first ordering [weeklyBuckets] already
     * established (GH #101 - axis labels were entirely missing from the chart).
     */
    fun axisWeeksAgo(barCount: Int): List<Int> {
        require(barCount >= 0) { "barCount must be >= 0, was $barCount" }
        return (barCount - 1 downTo 0).toList()
    }
}
