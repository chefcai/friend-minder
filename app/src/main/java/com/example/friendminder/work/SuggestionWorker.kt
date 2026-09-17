package com.example.friendminder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.friendminder.data.storage.WorkManagerNotificationScheduler
import com.example.friendminder.notifications.NotificationHelper
import com.example.friendminder.utils.ServiceLocator

/**
 * Daily (or test-triggered, see HomeFragment's test button) background job.
 * [WorkManagerNotificationScheduler] (FRM-8) schedules one instance of this
 * per notification slot; this class implements the actual suggestion logic
 * (FRM-9) and cooldown bookkeeping (FRM-10): pick a Friend List contact that
 * isn't on cooldown, post the reminder notification via [NotificationHelper]
 * (FRM-11/FRM-13, Publisher), and record the suggestion.
 *
 * The eligible-contact/fallback logic lives in [SuggestionSelector], a pure
 * function with no Android dependency, covered directly by unit tests
 * (SuggestionSelectorTest).
 *
 * CoroutineWorker (rather than the plain [androidx.work.Worker] sketched in
 * the original ticket) because our repository interfaces are all `suspend fun`.
 */
class SuggestionWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        /** Retained for backward compatibility with the original ticket sketch; unused. */
        const val KEY_CONTACTS_PER_DAY = "contacts_per_day"
        const val KEY_SLOT = "slot"
        const val KEY_RANDOM_MODE = "random_mode"
        const val KEY_RANDOM_START_HOUR = "random_start_hour"
        const val KEY_RANDOM_END_HOUR = "random_end_hour"
    }

    override suspend fun doWork(): Result {
        val friendListRepo = ServiceLocator.friendListRepository
        val cooldownRepo = ServiceLocator.cooldownRepository
        val settingsRepo = ServiceLocator.settingsRepository

        val friends = friendListRepo.getFriendList()
        if (friends.isEmpty()) {
            rearmIfRandom()
            return Result.success() // nothing to suggest (Designer spec S4.1)
        }

        val cooldownDays = settingsRepo.getCooldownDays()
        val cooldownStatus = mutableMapOf<String, Boolean>()
        val lastSuggested = mutableMapOf<String, Long>()
        for (friend in friends) {
            cooldownStatus[friend.id] = cooldownRepo.isOnCooldown(friend.id, cooldownDays)
            lastSuggested[friend.id] = cooldownRepo.getLastSuggestion(friend.id) ?: 0L
        }

        val pool = SuggestionSelector.selectPool(friends, cooldownStatus, lastSuggested)
        val chosen = pool.randomOrNull()
        if (chosen == null) {
            rearmIfRandom()
            return Result.success()
        }

        cooldownRepo.setLastSuggestion(chosen.id, System.currentTimeMillis())
        NotificationHelper.postReminder(appContext, chosen, settingsRepo.getMessageTemplate())

        rearmIfRandom()
        return Result.success()
    }

    /** Random-mode slots are one-time work; re-arm tomorrow's run with a fresh random minute. */
    private fun rearmIfRandom() {
        if (!inputData.getBoolean(KEY_RANDOM_MODE, false)) return
        val slot = inputData.getInt(KEY_SLOT, 0)
        val startHour = inputData.getInt(KEY_RANDOM_START_HOUR, 9)
        val endHour = inputData.getInt(KEY_RANDOM_END_HOUR, 21)
        WorkManagerNotificationScheduler(appContext).enqueueRandomOneTime(slot, startHour, endHour)
    }
}
