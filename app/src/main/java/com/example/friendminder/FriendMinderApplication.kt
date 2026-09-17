package com.example.friendminder

import android.app.Application
import com.example.friendminder.data.storage.BirthdayWorkScheduler
import com.example.friendminder.data.storage.SchemaVersion
import com.example.friendminder.notifications.NotificationHelper
import com.example.friendminder.utils.ServiceLocator

class FriendMinderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationHelper.ensureChannel(this)
        // FRM-35: daily birthday/special-date check. Always on in Phase 2 — no settings toggle yet.
        BirthdayWorkScheduler(this).ensureScheduled()
        SchemaVersion.markCurrent(this, SchemaVersion.PHASE_2)
    }
}
