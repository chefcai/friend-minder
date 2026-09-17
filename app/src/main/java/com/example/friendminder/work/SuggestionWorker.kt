package com.example.friendminder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.friendminder.notifications.NotificationHelper
import com.example.friendminder.utils.ServiceLocator

/**
 * Daily (or test-triggered, see HomeFragment's test button) background job:
 * picks a random, non-cooled-down contact from the Friend List and posts the
 * reminder notification (FRM-9, FRM-10; wired to FRM-11/FRM-13 through
 * NotificationHelper).
 *
 * Implemented as [CoroutineWorker] (inherited from the FRM-3 scaffold) since
 * the repository interfaces are all `suspend fun`.
 */
class SuggestionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_CONTACTS_PER_DAY = "contacts_per_day"
    }

    override suspend fun doWork(): Result {
        val friendListRepo = ServiceLocator.friendListRepository
        val cooldownRepo = ServiceLocator.cooldownRepository
        val settingsRepo = ServiceLocator.settingsRepository

        val friends = friendListRepo.getFriendList()
        if (friends.isEmpty()) return Result.success() // nothing to suggest (Designer spec §4.1)

        val cooldownDays = settingsRepo.getCooldownDays()
        val eligible = friends.filterNot { cooldownRepo.isOnCooldown(it.id, cooldownDays) }

        // If every contact is on cooldown, fall back to the least-recently-
        // suggested one rather than blocking (PRD §10 "All contacts on cooldown").
        val chosen = if (eligible.isNotEmpty()) {
            eligible.random()
        } else {
            friends.minByOrNull { cooldownRepo.getLastSuggestion(it.id) ?: 0L } ?: friends.first()
        }

        cooldownRepo.setLastSuggestion(chosen.id, System.currentTimeMillis())

        NotificationHelper.postReminder(applicationContext, chosen, settingsRepo.getMessageTemplate())

        return Result.success()
    }
}
