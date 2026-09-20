package com.example.friendminder.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SpecialDateDao {
    @Query("SELECT * FROM special_dates")
    suspend fun getAll(): List<SpecialDateEntity>

    @Query("SELECT * FROM special_dates WHERE contactId = :contactId")
    suspend fun getForContact(contactId: String): List<SpecialDateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(specialDate: SpecialDateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(specialDates: List<SpecialDateEntity>)

    @Query("DELETE FROM special_dates WHERE id = :specialDateId")
    suspend fun deleteById(specialDateId: String)

    @Query("DELETE FROM special_dates WHERE source = :source")
    suspend fun deleteBySource(source: String)

    /**
     * Atomically replaces every CONTACTS-sourced row with [birthdays],
     * leaving CUSTOM rows untouched — matches
     * [com.example.friendminder.data.storage.SpecialDateRepository.replaceContactsSourced]'s
     * delete-then-insert contract from the old SharedPreferences implementation.
     */
    @Transaction
    suspend fun replaceContactsSourced(source: String, birthdays: List<SpecialDateEntity>) {
        deleteBySource(source)
        insertAll(birthdays)
    }
}
