package com.example.friendminder.data.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FRM-81: verifies [SpecialDateDao], in particular [SpecialDateDao.replaceContactsSourced]'s atomic delete-then-insert. */
@RunWith(AndroidJUnit4::class)
class SpecialDateDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SpecialDateDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.specialDateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun replaceContactsSourcedLeavesCustomEntriesUntouched() = runBlocking {
        dao.upsert(SpecialDateEntity("custom-1", "contact-1", "Anniversary", 6, 15, 0, "CUSTOM"))
        dao.upsert(SpecialDateEntity("bday-old", "contact-1", "Birthday", 1, 1, 0, "CONTACTS"))

        dao.replaceContactsSourced(
            "CONTACTS",
            listOf(SpecialDateEntity("bday-new", "contact-1", "Birthday", 3, 3, 0, "CONTACTS"))
        )

        val all = dao.getForContact("contact-1")
        assertEquals(2, all.size)
        assertTrue(all.any { it.id == "custom-1" })
        assertTrue(all.any { it.id == "bday-new" })
        assertTrue(all.none { it.id == "bday-old" })
    }

    @Test
    fun deleteByIdRemovesOnlyThatEntry() = runBlocking {
        dao.upsert(SpecialDateEntity("d1", "contact-1", "A", 1, 1, 0, "CUSTOM"))
        dao.upsert(SpecialDateEntity("d2", "contact-1", "B", 2, 2, 0, "CUSTOM"))

        dao.deleteById("d1")

        val remaining = dao.getForContact("contact-1")
        assertEquals(1, remaining.size)
        assertEquals("d2", remaining.first().id)
    }
}
