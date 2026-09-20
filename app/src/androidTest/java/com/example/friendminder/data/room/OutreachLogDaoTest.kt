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

/** FRM-81: verifies [OutreachLogDao] basic CRUD and per-contact filtering. */
@RunWith(AndroidJUnit4::class)
class OutreachLogDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: OutreachLogDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.outreachLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getForContactFiltersByContactId() = runBlocking {
        dao.insert(OutreachLogEntity("log-1", "contact-1", 1000L, "SMS", null))
        dao.insert(OutreachLogEntity("log-2", "contact-2", 2000L, "CALL", "checked in"))

        val forContact1 = dao.getForContact("contact-1")
        assertEquals(1, forContact1.size)
        assertEquals("log-1", forContact1.first().id)
    }

    @Test
    fun deleteByIdRemovesOnlyThatLog() = runBlocking {
        dao.insertAll(
            listOf(
                OutreachLogEntity("log-1", "contact-1", 1000L, "SMS", null),
                OutreachLogEntity("log-2", "contact-1", 2000L, "CALL", null)
            )
        )

        dao.deleteById("log-1")

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertTrue(all.none { it.id == "log-1" })
    }
}
