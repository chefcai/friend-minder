package com.example.friendminder.utils

import android.content.Context
import com.example.friendminder.data.storage.ContactGroupRepository
import com.example.friendminder.data.storage.CooldownRepository
import com.example.friendminder.data.storage.FriendListRepository
import com.example.friendminder.data.storage.NotificationScheduler
import com.example.friendminder.data.storage.OutreachLogRepository
import com.example.friendminder.data.storage.ReminderFrequencyRepository
import com.example.friendminder.data.storage.SettingsRepository
import com.example.friendminder.data.storage.SharedPrefsContactGroupRepository
import com.example.friendminder.data.storage.SharedPrefsCooldownRepository
import com.example.friendminder.data.storage.SharedPrefsFriendListRepository
import com.example.friendminder.data.storage.SharedPrefsOutreachLogRepository
import com.example.friendminder.data.storage.SharedPrefsReminderFrequencyRepository
import com.example.friendminder.data.storage.SharedPrefsSettingsRepository
import com.example.friendminder.data.storage.SharedPrefsSpecialDateRepository
import com.example.friendminder.data.storage.SharedPrefsStatisticsCacheRepository
import com.example.friendminder.data.storage.SpecialDateRepository
import com.example.friendminder.data.storage.StatisticsCacheRepository
import com.example.friendminder.data.storage.WorkManagerNotificationScheduler
import com.example.friendminder.domain.services.BirthdayService
import com.example.friendminder.domain.services.DefaultBirthdayService
import com.example.friendminder.domain.services.DefaultGroupService
import com.example.friendminder.domain.services.DefaultOutreachLogService
import com.example.friendminder.domain.services.DefaultStatisticsService
import com.example.friendminder.domain.services.GroupService
import com.example.friendminder.domain.services.OutreachLogService
import com.example.friendminder.domain.services.StatisticsService

/**
 * Deliberately minimal manual service locator — no Hilt/Dagger/Koin. Keeps
 * the dependency graph tiny and easy to audit, in line with the F-Droid
 * philosophy of a small, inspectable dependency tree (PRD §11).
 *
 * MainActivity, Fragments, and SuggestionWorker/BirthdayWorker all read
 * repositories and services from here rather than constructing
 * implementations directly, so swapping one out later only touches this
 * file. Phase 2 (FRM-30..35) follows the same pattern the MVP established.
 */
object ServiceLocator {

    @Volatile
    private var applicationContext: Context? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    val friendListRepository: FriendListRepository by lazy {
        SharedPrefsFriendListRepository(requireContext())
    }

    val cooldownRepository: CooldownRepository by lazy {
        SharedPrefsCooldownRepository(requireContext())
    }

    val settingsRepository: SettingsRepository by lazy {
        SharedPrefsSettingsRepository(requireContext())
    }

    val notificationScheduler: NotificationScheduler by lazy {
        WorkManagerNotificationScheduler(requireContext())
    }

    // --- Phase 2 (FRM-30..35) ---

    val contactGroupRepository: ContactGroupRepository by lazy {
        SharedPrefsContactGroupRepository(requireContext())
    }

    val reminderFrequencyRepository: ReminderFrequencyRepository by lazy {
        SharedPrefsReminderFrequencyRepository(requireContext())
    }

    val specialDateRepository: SpecialDateRepository by lazy {
        SharedPrefsSpecialDateRepository(requireContext())
    }

    val outreachLogRepository: OutreachLogRepository by lazy {
        SharedPrefsOutreachLogRepository(requireContext())
    }

    val statisticsCacheRepository: StatisticsCacheRepository by lazy {
        SharedPrefsStatisticsCacheRepository(requireContext())
    }

    val groupService: GroupService by lazy {
        DefaultGroupService(contactGroupRepository, friendListRepository)
    }

    val outreachLogService: OutreachLogService by lazy {
        DefaultOutreachLogService(outreachLogRepository)
    }

    val statisticsService: StatisticsService by lazy {
        DefaultStatisticsService(
            friendListRepository = friendListRepository,
            outreachLogService = outreachLogService,
            cooldownRepository = cooldownRepository,
            reminderFrequencyRepository = reminderFrequencyRepository,
            settingsRepository = settingsRepository,
            cacheRepository = statisticsCacheRepository
        )
    }

    val birthdayService: BirthdayService by lazy {
        DefaultBirthdayService(requireContext(), friendListRepository, specialDateRepository)
    }

    private fun requireContext(): Context =
        checkNotNull(applicationContext) {
            "ServiceLocator.init(context) must be called before use — see FriendMinderApplication.onCreate()"
        }
}
