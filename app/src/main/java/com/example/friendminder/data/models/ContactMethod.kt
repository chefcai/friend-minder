package com.example.friendminder.data.models

/**
 * A contact's preferred outreach channel for quick-action buttons and
 * automated notifications (FRM-183).
 *
 * [SMS] is the PRD-stated default used whenever no preference has been
 * stored yet — see [com.example.friendminder.data.storage.ContactMethodRepository]
 * for how "never set" is resolved to this default without conflating the
 * two states.
 */
enum class ContactMethod {
    SMS,
    CALL
}
