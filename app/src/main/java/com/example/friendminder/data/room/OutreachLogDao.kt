package com.example.friendminder.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OutreachLogDao {
    @Query("SELECT * FROM outreach_logs")
    suspend fun getAll(): List<OutreachLogEntity>

    @Query("SELECT * FROM outreach_logs WHERE contactId = :contactId")
    suspend fun getForContact(contactId: String): List<OutreachLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: OutreachLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<OutreachLogEntity>)

    @Query("DELETE FROM outreach_logs WHERE id = :logId")
    suspend fun deleteById(logId: String)
}
