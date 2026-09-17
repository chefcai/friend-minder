package com.example.friendminder.domain.services

import com.example.friendminder.data.models.SpecialDate

/** A [SpecialDate] annotated with how many days remain until its next occurrence — see [BirthdayService.getUpcomingDates]. */
data class UpcomingDate(
    val contactId: String,
    val label: String,
    val month: Int,
    val day: Int,
    val daysUntil: Int
)

/**
 * Architect <-> Designer/Publisher contract for birthdays and custom special
 * dates (PRD §6.3; FRM-33/FRM-34/FRM-35).
 *
 * Example usage:
 * ```
 * birthdayService.refreshBirthdaysFromContacts() // after Friend List changes, and daily via BirthdayWorker
 * val upcoming = birthdayService.getUpcomingDates(withinDays = 14) // PRD §6.3 "next 14 days" widget
 * birthdayService.addCustomSpecialDate(contactId = "42", label = "Anniversary", month = 6, day = 12)
 * ```
 */
interface BirthdayService {
    /** Re-queries ContactsContract for Friend List contacts' birthdays and reconciles them into storage. Never touches CUSTOM dates. */
    suspend fun refreshBirthdaysFromContacts()

    /** Every special date (birthdays + custom) landing within the next [withinDays] days, soonest first. */
    suspend fun getUpcomingDates(withinDays: Int): List<UpcomingDate>

    suspend fun getSpecialDatesForContact(contactId: String): List<SpecialDate>

    suspend fun addCustomSpecialDate(
        contactId: String,
        label: String,
        month: Int,
        day: Int,
        reminderDaysBefore: Int = 0
    ): SpecialDate

    suspend fun removeSpecialDate(specialDateId: String)

    /**
     * Special dates whose reminder should fire today, accounting for
     * `reminderDaysBefore` — used by [com.example.friendminder.work.BirthdayWorker].
     */
    suspend fun getDueToday(): List<SpecialDate>
}
