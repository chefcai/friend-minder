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
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val UNIQUE_WORK_PREFIX = "friend_minder_suggestion_slot_"
private const val MAX_SLOTS = 5

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
 *
 * The actual time math is factored out to [SlotScheduling], a pure function
 * with no Android dependency, so it's covered directly by unit tests
 * (SlotSchedulingTest) rather than only implicitly through this class.
 */
class WorkManagerNotificationScheduler(private val context: Context) : NotificationScheduler {

    private fun slotName(slot: Int) = "$UNIQUE_WORK_PREFIX$slot"

    override suspend fun scheduleDaily(hour: Int, minute: Int, contactsPerDay: Int) {
        withContext(Dispatchers.IO) {
            val slots = contactsPerDay.coerceIn(1, MAX_SLOTS)
            cancelSlotsFrom(slots)
            for (slot in 0 until slots) {
                val (slotHour, slotMinute) = SlotScheduling.spreadSlotTime(hour, minute, slot, slots)
                enqueueDailySlot(slot, slotHour, slotMinute)
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
     *
     * [forceNextDay] must be true for that re-arm call (FRM-77): the random
     * draw comes from the full window with no floor relative to "now", so
     * without forcing next-day, a freshly-drawn time later today would fire
     * again the same day the slot already fired. Fresh (non-rearm) calls from
     * [scheduleWithRandomTime] leave it false, letting today's slot still fire
     * today if its random time hasn't passed yet.
     */
    fun enqueueRandomOneTime(slot: Int, startHour: Int, endHour: Int, forceNextDay: Boolean = false) {
        val (targetHour, targetMinute) = SlotScheduling.randomTimeInWindow(startHour, endHour) { windowMinutes ->
            Random.nextInt(windowMinutes)
        }

        val input = Data.Builder()
            .putInt(SuggestionWorker.KEY_SLOT, slot)
            .putBoolean(SuggestionWorker.KEY_RANDOM_MODE, true)
            .putInt(SuggestionWorker.KEY_RANDOM_START_HOUR, startHour)
            .putInt(SuggestionWorker.KEY_RANDOM_END_HOUR, endHour)
            .build()

        val request = OneTimeWorkRequestBuilder<SuggestionWorker>()
            .setInitialDelay(delayUntilNext(targetHour, targetMinute, forceNextDay), TimeUnit.MILLISECONDS)
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

        // GH #150: ExistingPeriodicWorkPolicy.UPDATE does not reliably apply a
        // changed initialDelay to a periodic request that's already pending or
        // has already fired at least once - WorkManager can silently keep the
        // stale schedule (or drop the run entirely) instead of adopting the
        // new hour/minute, so a Settings save that only changes the time can
        // produce zero reminders that day with no error and no log. REPLACE
        // guarantees the old work is torn down and a fresh request - honoring
        // this call's freshly computed initialDelay - takes its place.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(slotName(slot), ExistingPeriodicWorkPolicy.REPLACE, request)
    }

    private fun cancelSlotsFrom(startSlot: Int) {
        val workManager = WorkManager.getInstance(context)
        for (slot in startSlot until MAX_SLOTS) {
            workManager.cancelUniqueWork(slotName(slot))
        }
    }

    private fun delayUntilNext(hour: Int, minute: Int, forceNextDay: Boolean = false): Long =
        SlotScheduling.delayMillisUntil(hour, minute, System.currentTimeMillis(), forceNextDay)

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
