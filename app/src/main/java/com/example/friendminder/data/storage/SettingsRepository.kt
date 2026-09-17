package com.example.friendminder.data.storage

/**
 * User-configurable reminder settings (PRD §9 "Settings Configuration").
 *
 * Reminder time is either a single fixed time-of-day OR a random window;
 * [isRandomTimeEnabled] decides which of [getReminderTime] / [getRandomTimeRange]
 * the scheduler should use.
 */
interface SettingsRepository {
    /** (hour, minute) in 24h device-local time, or null if not yet configured. */
    suspend fun getReminderTime(): Pair<Int, Int>?
    suspend fun setReminderTime(hour: Int, minute: Int)

    suspend fun isRandomTimeEnabled(): Boolean
    suspend fun setRandomTimeEnabled(enabled: Boolean)

    /** (startHour, endHour) in 24h device-local time. */
    suspend fun getRandomTimeRange(): Pair<Int, Int>?
    suspend fun setRandomTimeRange(start: Int, end: Int)

    /** Number of separate reminder notifications per day (PRD default range 1-5). */
    suspend fun getContactsPerDay(): Int
    suspend fun setContactsPerDay(count: Int)

    /** Optional pre-fill text for the SMS body (PRD Q7 default: "Hey, How's it going?"). */
    suspend fun getMessageTemplate(): String
    suspend fun setMessageTemplate(template: String)

    /** Days before a contact can be re-suggested (PRD Q2 default: 3). */
    suspend fun getCooldownDays(): Int
    suspend fun setCooldownDays(days: Int)
}
