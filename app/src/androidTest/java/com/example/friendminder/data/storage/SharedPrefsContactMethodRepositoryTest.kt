package com.example.friendminder.data.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.friendminder.data.models.ContactMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** CRUD/persistence tests for FRM-183 against real SharedPreferences on a device/emulator. */
@RunWith(AndroidJUnit4::class)
class SharedPrefsContactMethodRepositoryTest {

    private lateinit var repository: SharedPrefsContactMethodRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("friend_minder_contact_methods", Context.MODE_PRIVATE)
            .edit().clear().commit()
        repository = SharedPrefsContactMethodRepository(context)
    }

    @Test
    fun neverSetReturnsNull() = runBlocking {
        assertNull(repository.getMethod("c1"))
    }

    @Test
    fun setThenGetRoundTrips() = runBlocking {
        repository.setMethod("c1", ContactMethod.CALL)

        assertEquals(ContactMethod.CALL, repository.getMethod("c1"))
    }

    @Test
    fun settingAgainOverwritesThePreviousValue() = runBlocking {
        repository.setMethod("c1", ContactMethod.CALL)
        repository.setMethod("c1", ContactMethod.SMS)

        assertEquals(ContactMethod.SMS, repository.getMethod("c1"))
    }

    @Test
    fun clearRemovesTheStoredPreference() = runBlocking {
        repository.setMethod("c1", ContactMethod.CALL)

        repository.clearMethod("c1")

        assertNull(repository.getMethod("c1"))
    }

    @Test
    fun preferencesArePersistedAcrossRepositoryInstances() = runBlocking {
        repository.setMethod("c1", ContactMethod.CALL)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val reopened = SharedPrefsContactMethodRepository(context)

        assertEquals(ContactMethod.CALL, reopened.getMethod("c1"))
    }

    @Test
    fun contactsAreIndependent() = runBlocking {
        repository.setMethod("c1", ContactMethod.CALL)
        repository.setMethod("c2", ContactMethod.SMS)

        assertEquals(ContactMethod.CALL, repository.getMethod("c1"))
        assertEquals(ContactMethod.SMS, repository.getMethod("c2"))
        assertNull(repository.getMethod("c3"))
    }
}
