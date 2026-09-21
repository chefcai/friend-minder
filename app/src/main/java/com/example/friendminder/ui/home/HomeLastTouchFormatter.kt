package com.example.friendminder.ui.home

import android.content.Context
import com.example.friendminder.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

    /** @param lastContactedMillis epoch millis of the most recent outreach log, or null if never. */
    fun format(context: Context, lastContactedMillis: Long?, now: Instant = Instant.now()): String {
        if (lastContactedMillis == null) {
            return context.getString(R.string.label_never_contacted)
        }

        val zone = ZoneId.systemDefault()
        val lastDate = Instant.ofEpochMilli(lastContactedMillis).atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(lastDate, today).toInt().coerceAtLeast(0)

        return when {
            days == 0 -> context.getString(R.string.label_today)
            days == 1 -> context.getString(R.string.label_home_yesterday)
            days in 2..13 -> context.resources.getQuantityString(R.plurals.format_home_days_ago, days, days)
            days in 14..27 -> {
                val weeks = days / 7
                context.resources.getQuantityString(R.plurals.format_home_weeks_ago, weeks, weeks)
            }
            days in 28..364 -> {
                val months = days / 30
                context.resources.getQuantityString(R.plurals.format_home_months_ago, months, months)
            }
            else -> context.getString(R.string.label_home_over_a_year_ago)
        }
    }
}
