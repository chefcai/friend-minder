package com.example.friendminder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.friendminder.data.storage.WorkManagerNotificationScheduler
import com.example.friendminder.notifications.NotificationHelper
import com.example.friendminder.utils.ServiceLocator

/**
 * Daily background job. [WorkManagerNotificationScheduler] (FRM-8) schedules
 * one instance of this per notification slot; this class implements the
 * actual suggestion logic (FRM-9) and cooldown bookkeeping (FRM-10): pick a
 * Friend List contact that isn't on cooldown, post the reminder notification
 * via [NotificationHelper]
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
        val groupService = ServiceLocator.groupService

        val friends = friendListRepo.getFriendList()
        if (friends.isEmpty()) {
            rearmIfRandom()
            return Result.success() // nothing to suggest (Designer spec S4.1)
        }

        val cooldownStatus = mutableMapOf<String, Boolean>()
        val lastSuggested = mutableMapOf<String, Long>()
        for (friend in friends) {
            // GH #121/FRM-97: shortest-interval-wins across contact/group/global,
            // not just a contact override falling back straight to the global
            // default - a group's interval can win even with no contact override set.
            val effectiveCooldownDays = groupService.getEffectiveInterval(friend.id).days
            cooldownStatus[friend.id] = cooldownRepo.isOnCooldown(friend.id, effectiveCooldownDays)
            lastSuggested[friend.id] = cooldownRepo.getLastSuggestion(friend.id) ?: 0L
        }

        val pool = SuggestionSelector.selectPool(friends, cooldownStatus, lastSuggested)
        val chosen = pool.randomOrNull()
        if (chosen == null) {
            rearmIfRandom()
            return Result.success()
        }

        cooldownRepo.setLastSuggestion(chosen.id, System.currentTimeMillis())
        cooldownRepo.incrementReminderCount(chosen.id) // FRM-38: StatisticsService reach-rate denominator.
        // Random pick from the template pool (chefcai/friend-minder#30) so
        // recipients don't see the exact same wording every reminder;
        // getMessageTemplates() guarantees a non-empty list. Respect the
        // user's "include a message" opt-out independently of the pool.
        val message = if (settingsRepo.isMessageEnabled()) {
            settingsRepo.getMessageTemplates().random()
        } else {
            ""
        }
        NotificationHelper.postReminder(appContext, chosen, message)

        rearmIfRandom()
        return Result.success()
    }

    /**
     * Random-mode slots are one-time work; re-arm tomorrow's run with a fresh
     * random minute. forceNextDay = true (FRM-77): without it, the freshly
     * drawn random minute could still be later today, firing this same slot
     * twice in one day.
     */
    private fun rearmIfRandom() {
        if (!inputData.getBoolean(KEY_RANDOM_MODE, false)) return
        val slot = inputData.getInt(KEY_SLOT, 0)
        val startHour = inputData.getInt(KEY_RANDOM_START_HOUR, 9)
        val endHour = inputData.getInt(KEY_RANDOM_END_HOUR, 21)
        WorkManagerNotificationScheduler(appContext)
            .enqueueRandomOneTime(slot, startHour, endHour, forceNextDay = true)
    }
}
