package com.example.friendminder

import android.app.Application
import com.example.friendminder.data.storage.BirthdayWorkScheduler
import com.example.friendminder.data.storage.RoomMigration
import com.example.friendminder.data.storage.SchemaVersion
import com.example.friendminder.notifications.NotificationHelper
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FriendMinderApplication : Application() {

    // Fire-and-forget scope for startup work that needs a suspend settings
    // read (FRM-54) - Application has no lifecycle-scoped coroutine builder
    // of its own. SupervisorJob so a failure here can't take down anything
    // else that might ever share this scope.
    private val applicationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // FRM-81: one-time copy of OutreachLog/ContactGroup/SpecialDate data from
        // SharedPreferences+JSON into Room. Deliberately blocking (runBlocking, not
        // applicationScope.launch) - ServiceLocator wires those three repositories
        // straight to their Room-backed implementations below, so this must finish
        // before any Fragment/Worker can read from them, unlike applyBirthdayCheckSetting()
        // below, which is safe to run fire-and-forget since nothing needs birthdays
        // ready the instant the app launches.
        runBlocking { RoomMigration.migrateIfNeeded(this@FriendMinderApplication) }
        ServiceLocator.init(this)
        NotificationHelper.ensureChannel(this)
        // FRM-35: daily birthday/special-date check, now gated behind a
        // Phase 2 settings toggle (FRM-54, default on) instead of always-on.
        // Runs every launch, same as the old unconditional call, so toggling
        // it off in Advanced settings also takes effect on the next launch
        // even if the in-place cancel() from the settings screen were ever
        // missed (e.g. process death before the toggle's own write completes).
        applicationScope.launch { applyBirthdayCheckSetting() }
        // Guarded (not an unconditional overwrite) so this can never downgrade a
        // version RoomMigration has already advanced past PHASE_2 (e.g. to
        // ROOM_MIGRATION_V1) back down to 2 on a later launch.
        if (SchemaVersion.current(this) < SchemaVersion.PHASE_2) {
            SchemaVersion.markCurrent(this, SchemaVersion.PHASE_2)
        }
    }

    private suspend fun applyBirthdayCheckSetting() {
        val scheduler = BirthdayWorkScheduler(this)
        if (ServiceLocator.settingsRepository.isBirthdayCheckEnabled()) {
            scheduler.ensureScheduled()
        } else {
            scheduler.cancel()
        }
    }
}
