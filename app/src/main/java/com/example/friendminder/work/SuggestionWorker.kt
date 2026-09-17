package com.example.friendminder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.friendminder.data.storage.WorkManagerNotificationScheduler
import com.example.friendminder.notifications.NotificationHelper
import com.example.friendminder.utils.ServiceLocator

private const val NOTIFICATION_ID_BASE = 1000

/**
 * Daily background job. [WorkManagerNotificationScheduler] (FRM-8) schedules
 * one instance of this per notification slot; this class implements the
 * actual suggestion logic (FRM-9) and cooldown bookkeeping (FRM-10): pick a
 * Friend List contact that isn't on cooldown, post the reminder notification
 * for them, and record the suggestion.
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
            return Result.success()
        }

        val cooldownDays = settingsRepo.getCooldownDays()
        val eligible = mutableListOf<com.example.friendminder.data.models.Contact>()
        for (friend in friends) {
            if (!cooldownRepo.isOnCooldown(friend.id, cooldownDays)) {
                eligible.add(friend)
            }
        }

        // If every friend is on cooldown (small Friend List, low cooldown
        // days), fall back to the least-recently-suggested contact rather
        // than silently skipping the day (PRD §8: cooldown should soften,
        // not block, reminders).
        val pool = if (eligible.isNotEmpty()) {
            eligible
        } else {
            var oldest = friends.first()
            var oldestTimestamp = cooldownRepo.getLastSuggestion(oldest.id) ?: 0L
            for (friend in friends) {
                val timestamp = cooldownRepo.getLastSuggestion(friend.id) ?: 0L
                if (timestamp < oldestTimestamp) {
                    oldest = friend
                    oldestTimestamp = timestamp
                }
            }
            listOf(oldest)
        }

        val chosen = pool.random()
        val messageTemplate = settingsRepo.getMessageTemplate()
        val notificationId = NOTIFICATION_ID_BASE + inputData.getInt(KEY_SLOT, 0)
        val posted = NotificationHelper.postSuggestion(appContext, chosen, messageTemplate, notificationId)

        if (posted) {
            cooldownRepo.setLastSuggestion(chosen.id, System.currentTimeMillis())
        }

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
