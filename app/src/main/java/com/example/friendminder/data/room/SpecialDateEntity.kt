package com.example.friendminder.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.friendminder.data.models.SpecialDate
import com.example.friendminder.data.models.SpecialDateSource

/** Room-backed storage for [SpecialDate] (FRM-81). */
@Entity(tableName = "special_dates")
data class SpecialDateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(index = true) val contactId: String,
    val label: String,
    val month: Int,
    val day: Int,
    val reminderDaysBefore: Int,
    @ColumnInfo(index = true) val source: String
)

fun SpecialDateEntity.toModel() = SpecialDate(
    id = id,
    contactId = contactId,
    label = label,
    month = month,
    day = day,
    reminderDaysBefore = reminderDaysBefore,
    source = SpecialDateSource.valueOf(source)
)

fun SpecialDate.toEntity() = SpecialDateEntity(
    id = id,
    contactId = contactId,
    label = label,
    month = month,
    day = day,
    reminderDaysBefore = reminderDaysBefore,
    source = source.name
)
