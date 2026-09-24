package com.example.friendminder.data.storage

import com.example.friendminder.data.models.ContactMethod

/**
 * Per-contact preferred outreach method — SMS or Call (FRM-183). `null`
 * means "no preference stored yet"; callers resolve that to
 * [ContactMethod.SMS] (the PRD-stated default) via [getEffectiveMethod]
 * rather than this repository silently substituting a default, so "never
 * set" and "explicitly set to SMS" stay distinguishable.
 *
 * Its own tiny store, not a field on [com.example.friendminder.data.models.Contact] —
 * see [com.example.friendminder.data.models.ContactGroup]'s doc for why Phase 2
 * per-contact data stays out of the MVP Contact/FriendList JSON blob. Same
 * shape as [ReminderFrequencyRepository].
 */
interface ContactMethodRepository {
    /** Stored preference for [contactId], or null if never set. */
    suspend fun getMethod(contactId: String): ContactMethod?
    suspend fun setMethod(contactId: String, method: ContactMethod)
    suspend fun clearMethod(contactId: String)
}

/**
 * [contactId]'s preference, defaulted to [ContactMethod.SMS] (FRM-183 AC1/AC5)
 * when none has been stored. The single place UI (quick actions, the method
 * selector, notification/action wiring) should read from, so the default
 * isn't re-implemented at each call site.
 */
suspend fun ContactMethodRepository.getEffectiveMethod(contactId: String): ContactMethod =
    getMethod(contactId) ?: ContactMethod.SMS
