package com.example.friendminder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Daily background job that will eventually: pick a random, non-cooled-down
 * contact from the Friend List and post the reminder notification (FRM-9,
 * FRM-10). Stubbed to a no-op success so the WorkManager plumbing (scheduling,
 * reboot survival, unique work replacement) can be proven out and exercised
 * by Publisher before the suggestion logic itself is implemented.
 *
 * Note: implemented as [CoroutineWorker] rather than the plain [androidx.work.Worker]
 * sketched in the original ticket, since our repository interfaces are all
 * `suspend fun` — CoroutineWorker.doWork() is the WorkManager API that
 * actually supports suspend functions.
 */
class SuggestionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_CONTACTS_PER_DAY = "contacts_per_day"
    }

    override suspend fun doWork(): Result {
        // TODO(FRM-9): select a random contact (respecting cooldown, FRM-10)
        // and post the reminder notification with the SMS Intent action
        // (FRM-11, FRM-13).
        return Result.success()
    }
}
