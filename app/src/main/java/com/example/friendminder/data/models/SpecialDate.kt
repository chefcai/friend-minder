package com.example.friendminder.data.models

/** Where a [SpecialDate] originated (PRD §6.3). */
enum class SpecialDateSource {
    /** Derived from the device's ContactsContract birthday event; refreshed by BirthdayService. */
    CONTACTS,

    /** Entered directly by the user (anniversaries, etc.). */
    CUSTOM
}

/**
 * A yearly-recurring milestone tied to a contact (PRD §6.3, §7; PRD Q7
 * locked this to yearly-recurring only — one-off dates are deferred to v3).
 * Deliberately no year field: birthdays sourced from ContactsContract are
 * stored as month/day only, matching the PRD's privacy note in §6.3, and
 * custom dates recur yearly anyway so a year would never be used.
 *
 * @param reminderDaysBefore 0 = remind on the day; a negative number is how
 *   many days before (e.g. -7 = remind 7 days prior), matching PRD §7's
 *   `SpecialDate.reminderDaysBefore` convention.
 */
data class SpecialDate(
    val id: String,
    val contactId: String,
    val label: String,
    val month: Int,
    val day: Int,
    val reminderDaysBefore: Int = 0,
    val source: SpecialDateSource = SpecialDateSource.CUSTOM
)
