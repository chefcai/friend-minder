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

    /**
     * Explicit user opt-in for [SmsLaunchActivity] to send SMS directly
     * (skipping the recipient's own SMS app) instead of always falling back
     * to the ACTION_SENDTO pre-fill flow. Defaults to `false` — direct
     * sending is off until the user turns it on from the Advanced settings
     * screen (chefcai/friend-minder#38). Deliberately separate from the
     * SEND_SMS OS permission grant itself: Android has no API to un-grant a
     * permission the app already holds, so this flag is what makes the
     * toggle meaningfully reversible even after permission was granted once.
     */
    suspend fun isDirectSendEnabled(): Boolean
    suspend fun setDirectSendEnabled(enabled: Boolean)

    /**
     * Whether the daily birthday/special-date check ([BirthdayWorker], FRM-35)
     * should be scheduled at all. Defaults to `true` - Phase 2 originally
     * scheduled this unconditionally from [FriendMinderApplication.onCreate],
     * so existing installs keep getting birthday reminders unless they
     * explicitly turn this off from Advanced settings (FRM-54).
     */
    suspend fun isBirthdayCheckEnabled(): Boolean
    suspend fun setBirthdayCheckEnabled(enabled: Boolean)
}
