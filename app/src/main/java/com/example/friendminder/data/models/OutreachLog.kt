package com.example.friendminder.data.models

/** How a logged outreach happened (PRD §6.4, §7). */
enum class OutreachType {
    SMS, CALL, IN_PERSON, VIDEO, OTHER
}

/**
 * A single recorded instance of reaching out to a contact — either
 * auto-logged when the app's own reminder flow results in a sent SMS, or
 * manually entered by the user for outreach that happened outside the app
 * (PRD §6.4). [OutreachLogService][com.example.friendminder.domain.services.OutreachLogService]
 * is the intended write/read path; see its KDoc for the SMS auto-logging
 * integration point.
 */
data class OutreachLog(
    val id: String,
    val contactId: String,
    val timestamp: Long,
    val type: OutreachType,
    val note: String? = null
)
