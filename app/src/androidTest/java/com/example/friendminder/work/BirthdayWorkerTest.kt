package com.example.friendminder.work

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.ContactMethod
import com.example.friendminder.notifications.NotificationHelper
import com.example.friendminder.utils.ServiceLocator
import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test for FRM-47: [BirthdayWorker.doWork] posts a correctly
 * templated notification for a special date due today.
 *
 * Uses a CUSTOM-sourced special date (via [ServiceLocator.birthdayService]'s
 * `addCustomSpecialDate`) rather than a CONTACTS-sourced one: this device/
 * emulator has no seeded contacts, and [BirthdayWorker.doWork] still calls
 * `refreshBirthdaysFromContacts()` first regardless (finding nothing, which
 * is correct — it must never touch CUSTOM dates). That leaves
 * `getDueToday()` to pick up the CUSTOM date we set up directly, exercising
 * the same "find what's due, post one notification per contact" path a real
 * birthday would take.
 */
@RunWith(AndroidJUnit4::class)
class BirthdayWorkerTest {

    // POST_NOTIFICATIONS is a runtime permission from API 33 on; this is a
    // no-op (permission already granted) on lower API levels.
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("friend_minder_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("friend_minder_special_dates", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("friend_minder_contact_methods", Context.MODE_PRIVATE).edit().clear().commit()

        ServiceLocator.init(context)
        NotificationHelper.ensureChannel(context)
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
    }

    @Test
    fun doWork_postsATemplatedNotification_forASpecialDateDueToday() = runBlocking {
        val contact = Contact(id = "e2e-birthday-contact", name = "Alex Testerson", phoneNumber = "555-0100")
        ServiceLocator.friendListRepository.addFriend(contact)

        val today = Calendar.getInstance()
        val dueDate = ServiceLocator.birthdayService.addCustomSpecialDate(
            contactId = contact.id,
            label = "Birthday",
            month = today.get(Calendar.MONTH) + 1, // Calendar.MONTH is 0-indexed; SpecialDate.month is 1-12
            day = today.get(Calendar.DAY_OF_MONTH)
        )

        val worker = TestListenableWorkerBuilder<BirthdayWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val expectedNotificationId = (contact.id + "_" + dueDate.id).hashCode()
        val posted = notificationManager
            ?.activeNotifications
            ?.firstOrNull { it.id == expectedNotificationId }

        assertNotNull(
            "expected a notification (id=$expectedNotificationId) for the special date due today",
            posted
        )
        // Full display name in the title (GH #79 - disambiguates same-first-name contacts),
        // but the festive birthday message body still greets by first name only
        // (NotificationHelper.postSpecialDateReminder), not the generic special-date body
        // used for non-birthday labels.
        assertEquals(
            "Alex Testerson",
            posted!!.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()
        )
        assertEquals(
            context.getString(com.example.friendminder.R.string.format_birthday_message, "Alex"),
            posted.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
        )
    }

    @Test
    fun doWork_postsNoNotification_whenNoSpecialDateIsDueToday() = runBlocking {
        val contact = Contact(id = "e2e-no-birthday-contact", name = "Sam Nodate", phoneNumber = "555-0101")
        ServiceLocator.friendListRepository.addFriend(contact)

        // 200 days from today, comfortably outside "due today" regardless of the current date.
        val future = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 200) }
        ServiceLocator.birthdayService.addCustomSpecialDate(
            contactId = contact.id,
            label = "Birthday",
            month = future.get(Calendar.MONTH) + 1,
            day = future.get(Calendar.DAY_OF_MONTH)
        )

        val worker = TestListenableWorkerBuilder<BirthdayWorker>(context).build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        assertEquals(0, notificationManager?.activeNotifications?.size ?: -1)
    }

    /**
     * FRM-186: the birthday/special-date notification's action label - and by extension the
     * Intent its PendingIntent fires (CallLaunchActivity vs SmsLaunchActivity) - switches with
     * the contact's stored preference. The label is the black-box-observable part of that
     * choice via NotificationManager; the two Activities' own Intent-building is unit-covered
     * separately (ContactActionLauncherTest).
     */
    @Test
    fun doWork_postsACallAction_whenContactPrefersCall() = runBlocking {
        val contact = Contact(id = "e2e-call-pref-contact", name = "Cam Caller", phoneNumber = "555-0102")
        ServiceLocator.friendListRepository.addFriend(contact)
        ServiceLocator.contactMethodRepository.setMethod(contact.id, ContactMethod.CALL)

        val today = Calendar.getInstance()
        val dueDate = ServiceLocator.birthdayService.addCustomSpecialDate(
            contactId = contact.id,
            label = "Birthday",
            month = today.get(Calendar.MONTH) + 1,
            day = today.get(Calendar.DAY_OF_MONTH)
        )

        val worker = TestListenableWorkerBuilder<BirthdayWorker>(context).build()
        worker.doWork()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val expectedNotificationId = (contact.id + "_" + dueDate.id).hashCode()
        val posted = notificationManager?.activeNotifications?.firstOrNull { it.id == expectedNotificationId }

        assertNotNull("expected a notification for the special date due today", posted)
        val actionTitle = posted!!.notification.actions?.firstOrNull()?.title?.toString()
        assertEquals(
            context.getString(com.example.friendminder.R.string.format_notif_action_call, "Cam Caller"),
            actionTitle
        )
    }

    @Test
    fun doWork_postsATextAction_whenContactHasNoStoredPreference() = runBlocking {
        // Regression guard: a contact who predates FRM-183, or simply never touched the
        // method selector, must keep getting the original SMS action - getEffectiveMethod's
        // default, not a behavior change for everyone else.
        val contact = Contact(id = "e2e-no-pref-contact", name = "Sam Texter", phoneNumber = "555-0103")
        ServiceLocator.friendListRepository.addFriend(contact)

        val today = Calendar.getInstance()
        val dueDate = ServiceLocator.birthdayService.addCustomSpecialDate(
            contactId = contact.id,
            label = "Birthday",
            month = today.get(Calendar.MONTH) + 1,
            day = today.get(Calendar.DAY_OF_MONTH)
        )

        val worker = TestListenableWorkerBuilder<BirthdayWorker>(context).build()
        worker.doWork()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val expectedNotificationId = (contact.id + "_" + dueDate.id).hashCode()
        val posted = notificationManager?.activeNotifications?.firstOrNull { it.id == expectedNotificationId }

        assertNotNull("expected a notification for the special date due today", posted)
        val actionTitle = posted!!.notification.actions?.firstOrNull()?.title?.toString()
        assertEquals(
            context.getString(com.example.friendminder.R.string.format_notif_action_text, "Sam Texter"),
            actionTitle
        )
    }
}
