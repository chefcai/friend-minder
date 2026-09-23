package com.example.friendminder.domain.services

import com.example.friendminder.data.models.OutreachLog
import com.example.friendminder.data.models.OutreachType
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FRM-169 / OH-1: Overall History's "contacts reached this month" counts
 * distinct tracked contacts in the current calendar month (device time
 * zone), not outreach events.
 */
class ContactsReachedThisMonthTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val tracked = setOf("a", "b", "c")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun log(contactId: String, timestamp: Long) =
        OutreachLog(id = "$contactId@$timestamp", contactId = contactId, timestamp = timestamp, type = OutreachType.SMS)

    private val now = at(2026, 9, 23, 12, 0)

    private fun count(logs: List<OutreachLog>, trackedIds: Set<String> = tracked, nowMillis: Long = now) =
        StatisticsCalculator.distinctContactsReachedInMonth(logs, trackedIds, nowMillis, zone)

    @Test
    fun `three outreaches from one contact count as one`() {
        val logs = listOf(
            log("a", at(2026, 9, 2, 9, 0)),
            log("a", at(2026, 9, 10, 18, 30)),
            log("a", at(2026, 9, 22, 7, 15))
        )
        assertEquals(1, count(logs))
    }

    @Test
    fun `distinct contacts are each counted once`() {
        val logs = listOf(
            log("a", at(2026, 9, 2, 9, 0)),
            log("b", at(2026, 9, 3, 9, 0)),
            log("a", at(2026, 9, 4, 9, 0)),
            log("c", at(2026, 9, 5, 9, 0))
        )
        assertEquals(3, count(logs))
    }

    @Test
    fun `23-59 on the last day of the previous month is excluded`() {
        assertEquals(0, count(listOf(log("a", at(2026, 8, 31, 23, 59)))))
    }

    @Test
    fun `00-00 on the first of the month is included`() {
        assertEquals(1, count(listOf(log("a", at(2026, 9, 1, 0, 0)))))
    }

    @Test
    fun `00-00 on the first of next month is excluded`() {
        assertEquals(0, count(listOf(log("a", at(2026, 10, 1, 0, 0)))))
    }

    @Test
    fun `month boundary follows the device zone, not UTC`() {
        // 2026-09-01 02:00 UTC is still 2026-08-31 22:00 in New York.
        val utcEarlySeptember = java.time.Instant.parse("2026-09-01T02:00:00Z").toEpochMilli()
        assertEquals(0, count(listOf(log("a", utcEarlySeptember))))
    }

    @Test
    fun `untracked contacts are excluded`() {
        val logs = listOf(
            log("a", at(2026, 9, 2, 9, 0)),
            log("removed", at(2026, 9, 3, 9, 0)),
            log("removed", at(2026, 9, 4, 9, 0))
        )
        assertEquals(1, count(logs))
    }

    @Test
    fun `no logs gives zero`() {
        assertEquals(0, count(emptyList()))
    }

    @Test
    fun `startOfMonthMillis is local midnight on the first`() {
        assertEquals(at(2026, 9, 1, 0, 0), StatisticsCalculator.startOfMonthMillis(now, zone))
    }
}
