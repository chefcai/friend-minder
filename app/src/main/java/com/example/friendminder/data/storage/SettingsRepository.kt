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

    /** Whether reminders should include a pre-filled SMS body at all ("Include a message" checkbox). */
    suspend fun isMessageEnabled(): Boolean
    suspend fun setMessageEnabled(enabled: Boolean)

    /**
     * Pool of pre-fill/send SMS body templates (PRD Q7 default seed:
     * "Hey, How's it going?"); [SuggestionWorker] picks one at random per
     * reminder when [isMessageEnabled] is true (chefcai/friend-minder#30) so
     * recipients don't see the exact same text every time. Always
     * non-empty — falls back to the built-in defaults if the user clears
     * every template.
     */
    suspend fun getMessageTemplates(): List<String>
    suspend fun setMessageTemplates(templates: List<String>)

    /** Days before a contact can be re-suggested (PRD Q2 default: 3). */
    suspend fun getCooldownDays(): Int
    suspend fun setCooldownDays(days: Int)
}
