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
     *   `outreachLogService.countSince(now - i * 7 days)`. Must have at
     *   least 2 entries; returns one fewer bucket than there are boundaries.
     */
    fun weeklyBuckets(cumulativeCounts: List<Int>): List<Int> {
        require(cumulativeCounts.size >= 2) { "need at least 2 cumulative counts to derive 1 weekly bucket" }
        val perWeek = (0 until cumulativeCounts.size - 1).map { cumulativeCounts[it] - cumulativeCounts[it + 1] }
        return perWeek.reversed()
    }
}
