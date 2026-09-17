package com.example.friendminder.data.storage

import com.example.friendminder.data.models.SpecialDate

/** Persists [SpecialDate]s — both ContactsContract-derived birthdays and user-entered custom dates (PRD §6.3, §7; FRM-33/FRM-34). */
interface SpecialDateRepository {
    suspend fun getAll(): List<SpecialDate>
    suspend fun getForContact(contactId: String): List<SpecialDate>
    suspend fun upsert(specialDate: SpecialDate)
    suspend fun delete(specialDateId: String)

    /**
     * Replaces every existing CONTACTS-sourced entry with [birthdays] in one
     * atomic write, leaving CUSTOM entries untouched. Used by
     * [com.example.friendminder.domain.services.BirthdayService] to refresh
     * from ContactsContract without accumulating duplicates or clobbering
     * user-entered anniversaries.
     */
    suspend fun replaceContactsSourced(birthdays: List<SpecialDate>)
}
