package com.example.friendminder.notifications

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.example.friendminder.R
import com.example.friendminder.data.models.ContactMethod
import com.example.friendminder.data.models.OutreachType
import com.example.friendminder.ui.common.contactActionIntent
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * FRM-186: the Call counterpart to [SmsLaunchActivity], used when a
 * contact's stored preference (FRM-183) is [ContactMethod.CALL]. Reuses
 * [contactActionIntent] (FRM-185) for the actual dialer Intent, so the
 * notification path and Home's/Contact Detail's quick actions all build the
 * exact same Intent shape from one place rather than three slightly
 * different copies of it.
 *
 * Deliberately does NOT place the call - contactActionIntent's CALL branch
 * is Intent.ACTION_DIAL, which opens the dialer pre-filled and leaves the
 * tap-to-call action to the person, needing no CALL_PHONE permission at
 * all. Auto-dial is an explicit non-goal here (Cai, FRM-186: "that may be
 * an advanced feature added later"), the same way direct SMS sending is a
 * separate, explicit opt-in from Advanced Settings rather than the default
 * (see SmsLaunchActivity's kdoc).
 */
class CallLaunchActivity : Activity() {

    private var phoneNumber: String? = null
    private var contactId: String? = null
    private var notificationId: Int = NO_NOTIFICATION_ID
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
        contactId = intent.getStringExtra(EXTRA_CONTACT_ID)
        notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_NOTIFICATION_ID)

        // Same rationale as SmsLaunchActivity: both the notification's content tap and its
        // action button share this one PendingIntent, and NotificationCompat.setAutoCancel()
        // is unreliable for an action-button PendingIntent that launches an activity into a
        // new task (chefcai/friend-minder#35) - cancelling explicitly here doesn't depend on
        // that platform behavior.
        dismissSourceNotification()

        activityScope.launch {
            launchDialer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun dismissSourceNotification() {
        if (notificationId == NO_NOTIFICATION_ID) return
        NotificationManagerCompat.from(this).cancel(notificationId)
    }

    private suspend fun launchDialer() {
        val number = phoneNumber
        if (number.isNullOrBlank()) {
            finish()
            return
        }
        try {
            startActivity(contactActionIntent(ContactMethod.CALL, number))
            // No completion signal once the dialer takes over - we can't confirm the person
            // actually tapped call - but opening the dialer from this action is itself a clear
            // expression of intent to reach this person, same optimistic-logging rationale
            // SmsLaunchActivity documents for its own fallback path.
            recordOutreach()
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app can handle dialing $number", e)
            Toast.makeText(this, R.string.toast_no_dialer_app, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private suspend fun recordOutreach() {
        val id = contactId ?: return
        ServiceLocator.outreachLogService.logOutreach(id, OutreachType.CALL)
        ServiceLocator.statisticsService.invalidate(id)
    }

    companion object {
        private const val TAG = "CallLaunchActivity"
        private const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        private const val EXTRA_CONTACT_ID = "extra_contact_id"
        private const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val NO_NOTIFICATION_ID = -1

        fun intentFor(
            context: Context,
            phoneNumber: String,
            contactId: String,
            notificationId: Int = NO_NOTIFICATION_ID
        ): Intent =
            Intent(context, CallLaunchActivity::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_CONTACT_ID, contactId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
