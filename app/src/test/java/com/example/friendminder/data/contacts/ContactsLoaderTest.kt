package com.example.friendminder.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FRM-63: "birthday not calculating properly" traced to ContactsContract
 * Event.START_DATE arriving in shapes beyond the two the docs promise.
 * These lock in every raw format we now know to expect from real devices.
 */
class ContactsLoaderTest {

    @Test
    fun `no-year dashed format`() {
        assertEquals(3 to 15, ContactsLoader.parseMonthDay("--03-15"))
    }

    @Test
    fun `full date with real year`() {
        assertEquals(12 to 25, ContactsLoader.parseMonthDay("1990-12-25"))
    }

    @Test
    fun `full date with Google's no-year placeholder`() {
        assertEquals(7 to 4, ContactsLoader.parseMonthDay("1700-07-04"))
    }

    @Test
    fun `bare MM-dd with no year and no leading dashes`() {
        assertEquals(9 to 19, ContactsLoader.parseMonthDay("09-19"))
    }

    @Test
    fun `full datetime with time and offset suffix`() {
        assertEquals(5 to 12, ContactsLoader.parseMonthDay("2000-05-12T00:00:00.000-0400"))
    }

    @Test
    fun `no-year dashed format with time suffix`() {
        assertEquals(2 to 28, ContactsLoader.parseMonthDay("--02-28T00:00:00.000Z"))
    }

    @Test
    fun `out-of-range month or day is rejected`() {
        assertNull(ContactsLoader.parseMonthDay("--13-01"))
        assertNull(ContactsLoader.parseMonthDay("--01-32"))
    }

    @Test
    fun `unrecognized formats return null rather than guessing`() {
        assertNull(ContactsLoader.parseMonthDay(""))
        assertNull(ContactsLoader.parseMonthDay("not-a-date"))
        assertNull(ContactsLoader.parseMonthDay("07/04/1990"))
    }
}
