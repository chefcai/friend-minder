package com.example.friendminder.data.storage

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.friendminder.work.SuggestionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "friend_minder_daily_suggestion"

/**
 * Stub WorkManager-backed scheduler. Computes an initial delay to the next
 * occurrence of the target time and enqueues a unique daily PeriodicWorkRequest
 * for [SuggestionWorker]; re-scheduling REPLACEs the existing request so
 * settings changes don't stack duplicate jobs (PRD §10 "Multiple-per-day
 * scheduling" / device reboot handling is WorkManager's responsibility).
 *
 * The random-time-window and multiple-contacts-per-day behavior are left as
 * TODOs for FRM-8/FRM-9 — this scaffold only proves the plumbing compiles and
 * enqueues.
 */
class WorkManagerNotificationScheduler(private val context: Context) : NotificationScheduler {

    override suspend fun scheduleDaily(hour: Int, minute: Int, contactsPerDay: Int) {
        enqueue(initialDelayMillis(hour, minute), contactsPerDay)
    }

    override suspend fun scheduleWithRandomTime(startHour: Int, endHour: Int, contactsPerDay: Int) {
        // TODO(FRM-9): pick a random minute within [startHour, endHour) each cycle.
        enqueue(initialDelayMillis(startHour, 0), contactsPerDay)
    }

    override suspend fun cancelSchedule() {
        withContext(Dispatchers.IO) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    override suspend fun isScheduled(): Boolean = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UNIQUE_WORK_NAME)
            .get()
            .any { !it.state.isFinished }
    }

    private suspend fun enqueue(initialDelayMillis: Long, contactsPerDay: Int) =
        withContext(Dispatchers.IO) {
            val request = PeriodicWorkRequestBuilder<SuggestionWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(SuggestionWorker.KEY_CONTACTS_PER_DAY to contactsPerDay))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

    private fun initialDelayMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
