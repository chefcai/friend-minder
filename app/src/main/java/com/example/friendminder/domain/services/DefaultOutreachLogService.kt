package com.example.friendminder.domain.services

import com.example.friendminder.data.models.OutreachLog
import com.example.friendminder.data.models.OutreachType
import com.example.friendminder.data.storage.OutreachLogRepository
import java.util.UUID

class DefaultOutreachLogService(
    private val outreachLogRepository: OutreachLogRepository
) : OutreachLogService {

    override suspend fun logOutreach(
        contactId: String,
        type: OutreachType,
        timestamp: Long,
        note: String?
    ): OutreachLog {
        val log = OutreachLog(
            id = UUID.randomUUID().toString(),
            contactId = contactId,
            timestamp = timestamp,
            type = type,
            note = note
        )
        outreachLogRepository.add(log)
        return log
    }

    override suspend fun getHistory(contactId: String): List<OutreachLog> =
        outreachLogRepository.getForContact(contactId).sortedByDescending { it.timestamp }

    override suspend fun getLastContacted(contactId: String): Long? =
        getHistory(contactId).firstOrNull()?.timestamp

    override suspend fun deleteLog(logId: String) = outreachLogRepository.delete(logId)

    override suspend fun countSince(sinceMillis: Long): Int =
        outreachLogRepository.getAll().count { it.timestamp >= sinceMillis }
}
