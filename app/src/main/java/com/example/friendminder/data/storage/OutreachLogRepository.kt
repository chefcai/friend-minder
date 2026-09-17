package com.example.friendminder.data.storage

import com.example.friendminder.data.models.OutreachLog

/** Append-mostly store of [OutreachLog] entries (PRD §6.4, §7; FRM-36 storage half). */
interface OutreachLogRepository {
    suspend fun getAll(): List<OutreachLog>
    suspend fun getForContact(contactId: String): List<OutreachLog>
    suspend fun add(log: OutreachLog)
    suspend fun delete(logId: String)
}
