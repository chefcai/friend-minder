package com.example.friendminder.data.storage

import android.content.Context
import com.example.friendminder.data.models.SpecialDate
import com.example.friendminder.data.models.SpecialDateSource
import com.example.friendminder.data.room.AppDatabase
import com.example.friendminder.data.room.SpecialDateDao
import com.example.friendminder.data.room.toEntity
import com.example.friendminder.data.room.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room-backed [SpecialDateRepository] (FRM-81), replacing
 * [SharedPrefsSpecialDateRepository] as the production implementation.
 * [replaceContactsSourced] is a single `@Transaction` DAO call instead of
 * the old "filter CUSTOM, write custom + birthdays" read-modify-write.
 */
class RoomSpecialDateRepository(context: Context) : SpecialDateRepository {

    private val dao: SpecialDateDao = AppDatabase.getInstance(context).specialDateDao()

    override suspend fun getAll(): List<SpecialDate> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toModel() }
    }

    override suspend fun getForContact(contactId: String): List<SpecialDate> = withContext(Dispatchers.IO) {
        dao.getForContact(contactId).map { it.toModel() }
    }

    override suspend fun upsert(specialDate: SpecialDate) = withContext(Dispatchers.IO) {
        dao.upsert(specialDate.toEntity())
    }

    override suspend fun delete(specialDateId: String) = withContext(Dispatchers.IO) {
        dao.deleteById(specialDateId)
    }

    override suspend fun replaceContactsSourced(birthdays: List<SpecialDate>) = withContext(Dispatchers.IO) {
        dao.replaceContactsSourced(SpecialDateSource.CONTACTS.name, birthdays.map { it.toEntity() })
    }
}
