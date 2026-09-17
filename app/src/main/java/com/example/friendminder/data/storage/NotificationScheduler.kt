package com.example.friendminder.data.storage

/**
 * Owns WorkManager scheduling for the daily reminder(s). Implementations are
 * expected to enqueue [com.example.friendminder.work.SuggestionWorker] via a
 * unique periodic work request so re-scheduling replaces rather than
 * duplicates pending work, and so schedules survive reboot (PRD §10).
 */
interface NotificationScheduler {
    suspend fun scheduleDaily(hour: Int, minute: Int, contactsPerDay: Int)

    suspend fun scheduleWithRandomTime(startHour: Int, endHour: Int, contactsPerDay: Int)

    suspend fun cancelSchedule()

    suspend fun isScheduled(): Boolean
}
