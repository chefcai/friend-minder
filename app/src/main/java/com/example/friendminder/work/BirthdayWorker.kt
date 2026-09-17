package com.example.friendminder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.friendminder.notifications.NotificationHelper
import com.example.friendminder.utils.ServiceLocator

/**
 * Daily WorkManager job (FRM-35, PRD §8.2): refreshes birthday data from
 * ContactsContract, then posts a templated notification for every special
 * date due today — birthdays and custom dates alike, see
 * [com.example.friendminder.domain.services.BirthdayService.getDueToday].
 */
class BirthdayWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val birthdayService = ServiceLocator.birthdayService
        val friendListRepo = ServiceLocator.friendListRepository

        birthdayService.refreshBirthdaysFromContacts()

        val friendsById = friendListRepo.getFriendList().associateBy { it.id }
        for (specialDate in birthdayService.getDueToday()) {
            val contact = friendsById[specialDate.contactId] ?: continue
            NotificationHelper.postSpecialDateReminder(applicationContext, contact, specialDate)
        }

        return Result.success()
    }
}
