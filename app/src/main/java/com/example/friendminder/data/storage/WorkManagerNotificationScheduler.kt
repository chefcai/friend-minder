package com.example.friendminder.data.storage

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.friendminder.work.SuggestionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val UNIQUE_WORK_PREFIX = "friend_minder_suggestion_slot_"
private const val MAX_SLOTS = 5
private const val MINUTES_PER_DAY = 24 * 60

/**
 * WorkManager-backed [NotificationScheduler] (FRM-8). Each notification slot
 * (1 per contactsPerDay, up to [MAX_SLOTS] per PRD §9) is its own uniquely
 * named work request, so raising/lowering contactsPerDay never duplicates or
 * orphans work — rescheduling always cancels slots [contactsPerDay, MAX_SLOTS)
 * first.
 *
 * Fixed-time slots use a real PeriodicWorkRequest (WorkManager repeats it).
 * Random-window slots use a fresh OneTimeWorkRequest each day instead, since
 * PeriodicWorkRequest can't re-randomize its own fire time — [SuggestionWorker]
 * calls [enqueueRandomOneTime] again after each run to re-arm tomorrow.
 */
class WorkManagerNotificationScheduler(private val context: Context) : NotificationScheduler {

    private fun slotName(slot: Int) = "$UNIQUE_WORK_PREFIX$slot"

    override suspend fun scheduleDaily(hour: Int, minute: Int, contactsPerDay: Int) {
        withContext(Dispatchers.IO) {
            val slots = contactsPerDay.coerceIn(1, MAX_SLOTS)
            cancelSlotsFrom(slots)
            for (slot in 0 until slots) {
                // Spread slots evenly across the day starting at (hour, minute).
                val baseMinutes = hour * 60 + minute
                val offsetMinutes = slot * (MINUTES_PER_DAY / slots)
                val slotTotalMinutes = (baseMinutes + offsetMinutes) % MINUTES_PER_DAY
                enqueueDailySlot(slot, slotTotalMinutes / 60, slotTotalMinutes % 60)
            }
        }
    }

    override suspend fun scheduleWithRandomTime(startHour: Int, endHour: Int, contactsPerDay: Int) {
        withContext(Dispatchers.IO) {
            val slots = contactsPerDay.coerceIn(1, MAX_SLOTS)
            cancelSlotsFrom(slots)
            for (slot in 0 until slots) {
                enqueueRandomOneTime(slot, startHour, endHour)
            }
        }
    }

    /**
     * Picks a fresh random minute within [startHour, endHour) for [slot] and
     * enqueues a one-time work request for it. Public (non-suspend, called
     * directly from [SuggestionWorker.doWork]) so a random-mode slot can
     * re-arm itself for tomorrow right after it fires.
     */
    fun enqueueRandomOneTime(slot: Int, startHour: Int, endHour: Int) {
        val windowMinutes = (((endHour - startHour + 24) % 24).coerceAtLeast(1)) * 60
        val offsetMinutes = Random.nextInt(windowMinutes)
        val targetHour = (startHour + offsetMinutes / 60) % 24
        val targetMinute = offsetMinutes % 60

        val input = Data.Builder()
            .putInt(SuggestionWorker.KEY_SLOT, slot)
            .putBoolean(SuggestionWorker.KEY_RANDOM_MODE, true)
            .putInt(SuggestionWorker.KEY_RANDOM_START_HOUR, startHour)
            .putInt(SuggestionWorker.KEY_RANDOM_END_HOUR, endHour)
            .build()

        val request = OneTimeWorkRequestBuilder<SuggestionWorker>()
            .setInitialDelay(delayUntilNext(targetHour, targetMinute), TimeUnit.MILLISECONDS)
            .setInputData(input)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(slotName(slot), ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueueDailySlot(slot: Int, hour: Int, minute: Int) {
        val input = Data.Builder()
            .putInt(SuggestionWorker.KEY_SLOT, slot)
            .putBoolean(SuggestionWorker.KEY_RANDOM_MODE, false)
            .build()

        val request = PeriodicWorkRequestBuilder<SuggestionWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntilNext(hour, minute), TimeUnit.MILLISECONDS)
            .setInputData(input)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(slotName(slot), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun cancelSlotsFrom(startSlot: Int) {
        val workManager = WorkManager.getInstance(context)
        for (slot in startSlot until MAX_SLOTS) {
            workManager.cancelUniqueWork(slotName(slot))
        }
    }

    private fun delayUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    override suspend fun cancelSchedule() {
        withContext(Dispatchers.IO) {
            cancelSlotsFrom(0)
        }
    }

    override suspend fun isScheduled(): Boolean = withContext(Dispatchers.IO) {
        val workManager = WorkManager.getInstance(context)
        (0 until MAX_SLOTS).any { slot ->
            workManager.getWorkInfosForUniqueWork(slotName(slot)).get().any { !it.state.isFinished }
        }
    }
}
