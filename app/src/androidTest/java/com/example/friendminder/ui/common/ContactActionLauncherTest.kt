package com.example.friendminder.ui.common

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.friendminder.data.models.ContactMethod
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FRM-185: [contactActionIntent]'s Intent shape. Instrumented (not plain
 * JUnit) because [android.net.Uri]/[android.content.Intent] need a real
 * Android runtime - same reason [SharedPrefsContactMethodRepositoryTest]
 * (FRM-183) is an androidTest rather than a unit test.
 */
@RunWith(AndroidJUnit4::class)
class ContactActionLauncherTest {

    @Test
    fun smsUsesActionSendtoWithSmstoScheme() {
        val intent = contactActionIntent(ContactMethod.SMS, "555-0100")

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:555-0100", intent.data.toString())
    }

    @Test
    fun callUsesActionDialWithTelScheme() {
        // FRM-185's own spec: ACTION_DIAL (not ACTION_CALL), so no CALL_PHONE
        // permission is needed - see contactActionIntent's kdoc.
        val intent = contactActionIntent(ContactMethod.CALL, "555-0100")

        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:555-0100", intent.data.toString())
    }

    @Test
    fun differentPhoneNumbersProduceDifferentData() {
        val a = contactActionIntent(ContactMethod.CALL, "555-0100")
        val b = contactActionIntent(ContactMethod.CALL, "555-0199")

        assertEquals("tel:555-0100", a.data.toString())
        assertEquals("tel:555-0199", b.data.toString())
    }
}
