package com.example.friendminder.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.friendminder.data.models.OutreachLog
import com.example.friendminder.data.models.OutreachType

/**
 * Room-backed storage for [OutreachLog] (FRM-81). Deliberately not
 * foreign-keyed to a `ContactEntity` — [com.example.friendminder.data.models.Contact]
 * itself stays on SharedPreferences/JSON (see FRM-81's decision doc), so
 * `contactId` here is just a plain indexed string, matching how the old
 * `SharedPrefsOutreachLogRepository` treated it.
 */
@Entity(tableName = "outreach_logs")
data class OutreachLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(index = true) val contactId: String,
    val timestamp: Long,
    val type: String,
    val note: String?
)

fun OutreachLogEntity.toModel() = OutreachLog(
    id = id,
    contactId = contactId,
    timestamp = timestamp,
    type = OutreachType.valueOf(type),
    note = note
)

fun OutreachLog.toEntity() = OutreachLogEntity(
    id = id,
    contactId = contactId,
    timestamp = timestamp,
    type = type.name,
    note = note
)
