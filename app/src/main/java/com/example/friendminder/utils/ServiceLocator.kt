package com.example.friendminder.utils

import android.content.Context
import com.example.friendminder.data.contacts.ContactPhotoLoader
import com.example.friendminder.data.storage.ContactGroupRepository
import com.example.friendminder.data.storage.ContactMethodRepository
import com.example.friendminder.data.storage.CooldownRepository
import com.example.friendminder.data.storage.FriendListRepository
import com.example.friendminder.data.storage.NotificationScheduler
import com.example.friendminder.data.storage.OutreachLogRepository
import com.example.friendminder.data.storage.ReminderFrequencyRepository
import com.example.friendminder.data.storage.RoomContactGroupRepository
import com.example.friendminder.data.storage.RoomOutreachLogRepository
import com.example.friendminder.data.storage.RoomSpecialDateRepository
import com.example.friendminder.data.storage.SettingsRepository
import com.example.friendminder.data.storage.SharedPrefsCooldownRepository
import com.example.friendminder.data.storage.SharedPrefsContactMethodRepository
import com.example.friendminder.data.storage.SharedPrefsFriendListRepository
import com.example.friendminder.data.storage.SharedPrefsReminderFrequencyRepository
import com.example.friendminder.data.storage.SharedPrefsSettingsRepository
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

    /** Shared across every screen that renders contact avatars (FRM-39), so the in-memory photo cache isn't rebuilt per-fragment. */
    val contactPhotoLoader: ContactPhotoLoader by lazy {
        ContactPhotoLoader()
    }

    // --- Phase 2 (FRM-30..35) ---

    // FRM-81: Room-backed as of the partial migration (OutreachLog, ContactGroup/
    // membership, SpecialDate) - see RoomMigration and ARCHITECTURE-PHASE2.md.
    // Settings/Cooldown/ReminderFrequency/StatisticsCache/FriendList below stay on
    // SharedPreferences+JSON; this is the only file that needed to change for the swap.
    val contactGroupRepository: ContactGroupRepository by lazy {
        RoomContactGroupRepository(requireContext())
    }

    val reminderFrequencyRepository: ReminderFrequencyRepository by lazy {
        SharedPrefsReminderFrequencyRepository(requireContext())
    }

    val specialDateRepository: SpecialDateRepository by lazy {
        RoomSpecialDateRepository(requireContext())
    }

    val contactMethodRepository: ContactMethodRepository by lazy {
        SharedPrefsContactMethodRepository(requireContext())
    }

    val outreachLogRepository: OutreachLogRepository by lazy {
        RoomOutreachLogRepository(requireContext())
    }

    val statisticsCacheRepository: StatisticsCacheRepository by lazy {
        SharedPrefsStatisticsCacheRepository(requireContext())
    }

    val groupService: GroupService by lazy {
        // GH #121/FRM-97: needs reminderFrequencyRepository + settingsRepository too,
        // so getEffectiveInterval() can resolve contact/group/global precedence in
        // one place instead of SuggestionWorker and DefaultStatisticsService each
        // re-deriving contact-vs-global and silently ignoring groups.
        DefaultGroupService(
            contactGroupRepository,
            friendListRepository,
            reminderFrequencyRepository,
            settingsRepository
        )
    }

    val outreachLogService: OutreachLogService by lazy {
        DefaultOutreachLogService(outreachLogRepository)
    }

    val statisticsService: StatisticsService by lazy {
        // GH #121/FRM-97: streak calculation now resolves the interval via
        // groupService.getEffectiveInterval() (contact/group/global precedence),
        // replacing the old reminderFrequencyRepository-vs-settingsRepository-only
        // fallback that never accounted for a contact's groups.
        DefaultStatisticsService(
            friendListRepository = friendListRepository,
            outreachLogService = outreachLogService,
            cooldownRepository = cooldownRepository,
            groupService = groupService,
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
