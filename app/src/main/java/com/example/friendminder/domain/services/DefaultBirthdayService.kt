package com.example.friendminder.domain.services

import android.content.Context
import com.example.friendminder.data.contacts.ContactsLoader
import com.example.friendminder.data.models.SpecialDate
import com.example.friendminder.data.models.SpecialDateSource
import com.example.friendminder.data.storage.FriendListRepository
import com.example.friendminder.data.storage.SpecialDateRepository
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

class DefaultBirthdayService(
    private val context: Context,
    private val friendListRepository: FriendListRepository,
    private val specialDateRepository: SpecialDateRepository
) : BirthdayService {

    override suspend fun refreshBirthdaysFromContacts() {
        val friends = friendListRepository.getFriendList()
        val birthdaysByContact = ContactsLoader.loadBirthdays(context, friends.map { it.id }.toSet())
        val existingContactsSourced = specialDateRepository.getAll()
            .filter { it.source == SpecialDateSource.CONTACTS }
            .associateBy { it.contactId }

        val refreshed = birthdaysByContact.map { (contactId, monthDay) ->
            val (month, day) = monthDay
            val existing = existingContactsSourced[contactId]
            SpecialDate(
                id = existing?.id ?: UUID.randomUUID().toString(),
                contactId = contactId,
                label = "Birthday",
                month = month,
                day = day,
                reminderDaysBefore = existing?.reminderDaysBefore ?: 0,
                source = SpecialDateSource.CONTACTS
            )
        }
        specialDateRepository.replaceContactsSourced(refreshed)
    }

    override suspend fun getUpcomingDates(withinDays: Int): List<UpcomingDate> {
        val today = Calendar.getInstance()
        return specialDateRepository.getAll().mapNotNull { date ->
            val daysUntil = daysUntilNextOccurrence(today, date.month, date.day)
            if (daysUntil in 0..withinDays) {
                UpcomingDate(date.contactId, date.label, date.month, date.day, daysUntil)
            } else {
                null
            }
        }.sortedBy { it.daysUntil }
    }

    override suspend fun getSpecialDatesForContact(contactId: String): List<SpecialDate> =
        specialDateRepository.getForContact(contactId)

    override suspend fun addCustomSpecialDate(
        contactId: String,
        label: String,
        month: Int,
        day: Int,
        reminderDaysBefore: Int
    ): SpecialDate {
        require(month in 1..12) { "month must be 1-12" }
        require(day in 1..31) { "day must be 1-31" }
        val date = SpecialDate(
            id = UUID.randomUUID().toString(),
            contactId = contactId,
            label = label,
            month = month,
            day = day,
            reminderDaysBefore = reminderDaysBefore,
            source = SpecialDateSource.CUSTOM
        )
        specialDateRepository.upsert(date)
        return date
    }

    override suspend fun removeSpecialDate(specialDateId: String) =
        specialDateRepository.delete(specialDateId)

    override suspend fun getDueToday(): List<SpecialDate> {
        val today = Calendar.getInstance()
        return specialDateRepository.getAll().filter { date ->
            daysUntilNextOccurrence(today, date.month, date.day) == -date.reminderDaysBefore
        }
    }

    /**
     * Days from [today] (0 = today) to the next occurrence of [month]/[day],
     * rolling over to next year if that date already passed this year.
     */
    private fun daysUntilNextOccurrence(today: Calendar, month: Int, day: Int): Int {
        val todayStart = startOfDay(today)
        val target = startOfDay(today)
        target.set(Calendar.MONTH, month - 1)
        target.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(target.getActualMaximum(Calendar.DAY_OF_MONTH)))
        if (target.before(todayStart)) {
            target.add(Calendar.YEAR, 1)
        }
        val diffMillis = target.timeInMillis - todayStart.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
    }

    private fun startOfDay(source: Calendar): Calendar =
        (source.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
