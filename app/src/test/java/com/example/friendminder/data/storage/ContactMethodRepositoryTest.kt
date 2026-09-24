package com.example.friendminder.data.storage

import com.example.friendminder.data.models.ContactMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FRM-183: covers [ContactMethodRepository.getEffectiveMethod]'s
 * never-set-defaults-to-SMS contract against a hand-rolled fake (no mocking
 * library in this project, by design - see ServiceLocator's doc).
 * SharedPrefsContactMethodRepository itself needs a real Context, so its
 * persistence/CRUD behavior is covered by the androidTest instead.
 */
class ContactMethodRepositoryTest {

    private class FakeContactMethodRepository : ContactMethodRepository {
        val stored = mutableMapOf<String, ContactMethod>()
        override suspend fun getMethod(contactId: String): ContactMethod? = stored[contactId]
        override suspend fun setMethod(contactId: String, method: ContactMethod) {
            stored[contactId] = method
        }
        override suspend fun clearMethod(contactId: String) {
            stored.remove(contactId)
        }
    }

    private val repo = FakeContactMethodRepository()

    @Test
    fun neverSetReturnsNullFromGetMethod() = runBlocking {
        assertNull(repo.getMethod("c1"))
    }

    @Test
    fun neverSetDefaultsToSmsViaGetEffectiveMethod() = runBlocking {
        assertEquals(ContactMethod.SMS, repo.getEffectiveMethod("c1"))
    }

    @Test
    fun explicitSmsIsDistinguishableFromNeverSetAtTheRawLevel() = runBlocking {
        // Both read back as ContactMethod.SMS through getEffectiveMethod, but
        // getMethod must still tell them apart - that's the whole reason
        // getEffectiveMethod exists instead of the repository defaulting internally.
        repo.setMethod("c1", ContactMethod.SMS)

        assertEquals(ContactMethod.SMS, repo.getMethod("c1"))
        assertEquals(ContactMethod.SMS, repo.getEffectiveMethod("c1"))
        assertNull(repo.getMethod("c2"))
    }

    @Test
    fun explicitCallPreferenceIsReturnedAsIs() = runBlocking {
        repo.setMethod("c1", ContactMethod.CALL)

        assertEquals(ContactMethod.CALL, repo.getMethod("c1"))
        assertEquals(ContactMethod.CALL, repo.getEffectiveMethod("c1"))
    }

    @Test
    fun clearingRestoresTheNeverSetDefault() = runBlocking {
        repo.setMethod("c1", ContactMethod.CALL)

        repo.clearMethod("c1")

        assertNull(repo.getMethod("c1"))
        assertEquals(ContactMethod.SMS, repo.getEffectiveMethod("c1"))
    }

    @Test
    fun preferencesAreIndependentPerContact() = runBlocking {
        repo.setMethod("c1", ContactMethod.CALL)
        repo.setMethod("c2", ContactMethod.SMS)

        assertEquals(ContactMethod.CALL, repo.getEffectiveMethod("c1"))
        assertEquals(ContactMethod.SMS, repo.getEffectiveMethod("c2"))
        assertNull(repo.getMethod("c3"))
    }
}
