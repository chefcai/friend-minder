package com.example.friendminder.ui.home

import android.content.Context
import com.example.friendminder.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * The last-touch string ladder (FRM-99, SCREENS-PHASE3.md §1.4) - a single
 * ladder applied strictly, calendar-day based rather than elapsed-hours
 * based, so something logged at 11pm yesterday reads "Yesterday" at 1am
 * rather than "2 hours ago". This is deliberately a fresh formatter, not a
 * reuse of the Dashboard-era `format_days_ago`/`label_today` strings (see
 * strings.xml's Home section) - the ladder itself is different (this one
 * adds a weeks/months/"over a year" tail those never needed).
 */
object HomeLastTouchFormatter {

    /**
     * The pure day-count-to-bucket classification, factored out of [format]
     * so the ladder's thresholds and rounding are unit-testable without an
     * Android [Context] - same rationale as
     * [com.example.friendminder.ui.dashboard.DashboardChartCalculator] (FRM-55).
     *
     * FRM-181: weeks are rounded to the nearest week rather than floored, so
     * 27-29 days - closer to 4 weeks than 3 - read "4 weeks ago" instead of
     * undercounting, and the months branch now starts at day 30 (not 28) so
     * those same days never hit the old `days / 30 == 0` ("0 months ago") bug.
     */
    internal sealed class Bucket {
        object Today : Bucket()
        object Yesterday : Bucket()
        data class Days(val count: Int) : Bucket()
        data class Weeks(val count: Int) : Bucket()
        data class Months(val count: Int) : Bucket()
        object OverAYear : Bucket()
    }

    /** @param days whole calendar days since the last outreach; must be >= 0. */
    internal fun bucketFor(days: Int): Bucket = when {
        days == 0 -> Bucket.Today
        days == 1 -> Bucket.Yesterday
        days in 2..13 -> Bucket.Days(days)
        days in 14..29 -> Bucket.Weeks((days / 7.0).roundToInt())
        days in 30..364 -> Bucket.Months(days / 30)
        else -> Bucket.OverAYear
    }

    /** @param lastContactedMillis epoch millis of the most recent outreach log, or null if never. */
    fun format(context: Context, lastContactedMillis: Long?, now: Instant = Instant.now()): String {
        if (lastContactedMillis == null) {
            return context.getString(R.string.label_never_contacted)
        }

        val zone = ZoneId.systemDefault()
        val lastDate = Instant.ofEpochMilli(lastContactedMillis).atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(lastDate, today).toInt().coerceAtLeast(0)

        return when (val bucket = bucketFor(days)) {
            Bucket.Today -> context.getString(R.string.label_today)
            Bucket.Yesterday -> context.getString(R.string.label_home_yesterday)
            is Bucket.Days ->
                context.resources.getQuantityString(R.plurals.format_home_days_ago, bucket.count, bucket.count)
            is Bucket.Weeks ->
                context.resources.getQuantityString(R.plurals.format_home_weeks_ago, bucket.count, bucket.count)
            is Bucket.Months ->
                context.resources.getQuantityString(R.plurals.format_home_months_ago, bucket.count, bucket.count)
            Bucket.OverAYear -> context.getString(R.string.label_home_over_a_year_ago)
        }
    }
}
