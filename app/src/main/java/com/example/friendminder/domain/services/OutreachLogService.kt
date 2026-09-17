package com.example.friendminder.domain.services

import com.example.friendminder.data.models.OutreachLog
import com.example.friendminder.data.models.OutreachType

/**
 * Architect <-> Designer/Publisher contract for manual + auto outreach
 * logging (PRD §6.4; FRM-36/FRM-37). [logOutreach] is also the integration
 * point for the existing SMS flow: Publisher should call it from
 * [com.example.friendminder.notifications.SmsLaunchActivity] with
 * `type = OutreachType.SMS` whenever a reminder actually results in a sent
 * text, so `getLastContacted` reflects real outreach rather than only the
 * cooldown bookkeeping timestamp in
 * [com.example.friendminder.data.storage.CooldownRepository].
 *
 * Example usage:
 * ```
 * outreachLogService.logOutreach(contactId = "42", type = OutreachType.IN_PERSON, note = "Coffee at Brew Haven")
 * val history = outreachLogService.getHistory("42") // newest first
 * val lastContacted: Long? = outreachLogService.getLastContacted("42")
 * ```
 */
interface OutreachLogService {
    suspend fun logOutreach(
        contactId: String,
        type: OutreachType,
        timestamp: Long = System.currentTimeMillis(),
        note: String? = null
    ): OutreachLog

    /** Newest-first history for one contact. */
    suspend fun getHistory(contactId: String): List<OutreachLog>

    /** Most recent log timestamp for [contactId], or null if never logged. */
    suspend fun getLastContacted(contactId: String): Long?

    suspend fun deleteLog(logId: String)

    /** Count of logs across all contacts with a timestamp >= [sinceMillis] (PRD §6.5 "Monthly Outreach"). */
    suspend fun countSince(sinceMillis: Long): Int
}
