package com.example.friendminder.data.storage

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.friendminder.work.BirthdayWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "friend_minder_birthday_check"
private const val CHECK_HOUR = 8 // PRD §6.3 fires "on the day"; a morning check gives the whole day to act on it.

/**
 * Schedules the daily [BirthdayWorker] tick (PRD §8.2, FRM-35). Deliberately
 * its own tiny scheduler rather than another method on
 * [WorkManagerNotificationScheduler]/[NotificationScheduler] — that
 * interface's slot/random-window model is specific to the user-configurable
 * [com.example.friendminder.work.SuggestionWorker] schedule, while the
 * birthday check is a single fixed daily job with no Phase 2 settings UI.
 */
class BirthdayWorkScheduler(private val context: Context) {

    /** Idempotent: uses KEEP so calling this on every app launch doesn't reset an already-scheduled countdown. */
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<BirthdayWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntilNext(CHECK_HOUR), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun delayUntilNext(hour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
