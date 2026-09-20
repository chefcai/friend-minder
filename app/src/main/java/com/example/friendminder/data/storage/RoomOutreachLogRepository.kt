package com.example.friendminder.data.storage

import android.content.Context
import com.example.friendminder.data.models.OutreachLog
import com.example.friendminder.data.room.AppDatabase
import com.example.friendminder.data.room.OutreachLogDao
import com.example.friendminder.data.room.toEntity
import com.example.friendminder.data.room.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room-backed [OutreachLogRepository] (FRM-81), replacing
 * [SharedPrefsOutreachLogRepository] as the production implementation. Kept
 * behind the unchanged [OutreachLogRepository] interface, so every consumer
 * (services, workers) is unaffected by this swap - see FRM-81's decision doc.
 */
class RoomOutreachLogRepository(context: Context) : OutreachLogRepository {

    private val dao: OutreachLogDao = AppDatabase.getInstance(context).outreachLogDao()

    override suspend fun getAll(): List<OutreachLog> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toModel() }
    }

    override suspend fun getForContact(contactId: String): List<OutreachLog> = withContext(Dispatchers.IO) {
        dao.getForContact(contactId).map { it.toModel() }
    }

    override suspend fun add(log: OutreachLog) = withContext(Dispatchers.IO) {
        dao.insert(log.toEntity())
    }

    override suspend fun delete(logId: String) = withContext(Dispatchers.IO) {
        dao.deleteById(logId)
    }
}
