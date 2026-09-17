package com.example.friendminder.utils

import android.content.Context
import com.example.friendminder.data.storage.CooldownRepository
import com.example.friendminder.data.storage.FriendListRepository
import com.example.friendminder.data.storage.NotificationScheduler
import com.example.friendminder.data.storage.SettingsRepository
import com.example.friendminder.data.storage.SharedPrefsCooldownRepository
import com.example.friendminder.data.storage.SharedPrefsFriendListRepository
import com.example.friendminder.data.storage.SharedPrefsSettingsRepository
import com.example.friendminder.data.storage.WorkManagerNotificationScheduler

/**
 * Deliberately minimal manual service locator — no Hilt/Dagger/Koin. Keeps
 * the dependency graph tiny and easy to audit, in line with the F-Droid
 * philosophy of a small, inspectable dependency tree (PRD §11).
 *
 * MainActivity, Fragments, and SuggestionWorker all read repositories from
 * here rather than constructing SharedPrefs*Repository directly, so swapping
 * an implementation (e.g. to Room) later only touches this file.
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

    private fun requireContext(): Context =
        checkNotNull(applicationContext) {
            "ServiceLocator.init(context) must be called before use — see FriendMinderApplication.onCreate()"
        }
}
