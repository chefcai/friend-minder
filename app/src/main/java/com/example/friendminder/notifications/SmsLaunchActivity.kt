package com.example.friendminder.notifications

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.friendminder.R
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * PRD §16 Q1 (LOCKED): "implement one-tap send... if SEND_SMS permission
 * allows direct send, implement it; otherwise, require manual send
 * confirmation." Direct sending is available once granted, but per
 * chefcai/friend-minder#38 the SEND_SMS permission itself is no longer
 * requested from here automatically — that felt like an unexplained,
 * out-of-context system dialog triggered by a simple notification tap.
 * Instead, direct sending is an explicit opt-in the user turns on from the
 * Advanced settings screen (see AdvancedSettingsFragment), which is where
 * SEND_SMS actually gets requested, with an explanation shown first. This
 * Activity only checks whether that opt-in AND the permission are both
 * already in place; if not, it falls back to the pre-fill flow, same as if
 * the user had denied permission (chefcai/friend-minder#29 baseline).
 */
class SmsLaunchActivity : Activity() {

    private var phoneNumber: String? = null
    private var message: String? = null
    private var notificationId: Int = NO_NOTIFICATION_ID
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
        message = intent.getStringExtra(EXTRA_MESSAGE)
        notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NO_NOTIFICATION_ID)

        // Dismiss the source notification unconditionally, up front. Both the
        // notification's content tap and its "Text <Name>" action use this
        // same PendingIntent, and NotificationCompat.setAutoCancel() is
        // unreliable for action-button-triggered PendingIntents that launch
        // an activity into a new task (this one does) - it can leave the
        // notification sitting in the shade after the user has already acted
        // on it (chefcai/friend-minder#35). Cancelling here doesn't depend on
        // that platform behavior, and covers both the direct-send and
        // fallback-to-SMS-app paths below.
        dismissSourceNotification()

        activityScope.launch {
            val canSendDirectly = ServiceLocator.settingsRepository.isDirectSendEnabled() &&
                ContextCompat.checkSelfPermission(this@SmsLaunchActivity, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
            if (canSendDirectly) {
                sendDirectly()
            } else {
                launchSmsAppFallback()
            }
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

    private fun sendDirectly() {
        val number = phoneNumber
        val body = message
        if (number.isNullOrBlank() || body.isNullOrBlank()) {
            // Nothing worth auto-sending silently — let the user compose it
            // themselves in their SMS app, same as the pre-FRM-29 behavior.
            launchSmsAppFallback()
            return
        }
        try {
            // getSystemService(SmsManager::class.java) needs API 31+ (minSdk here is 26);
            // SmsManager.getDefault() is deprecated but still the only option below that.
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(number, null, body, null, null)
            }
            Toast.makeText(this, R.string.toast_sms_sent, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            // SmsManager can throw IllegalArgumentException (malformed number)
            // or a SecurityException in rare OEM/permission-revocation edge
            // cases; don't silently drop the user's message on the floor.
            Log.w(TAG, "Direct send to $number failed, falling back to SMS app", e)
            launchSmsAppFallback()
        }
    }

    private fun launchSmsAppFallback() {
        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("sms:$phoneNumber")
            if (!message.isNullOrBlank()) putExtra("sms_body", message)
        }
        try {
            startActivity(smsIntent)
        } catch (e: ActivityNotFoundException) {
            // Log the original exception — the catch is scoped to "no SMS app installed", but a
            // malformed sms: URI or other cause would otherwise be silently swallowed.
            Log.w(TAG, "No app can handle $smsIntent", e)
            Toast.makeText(this, R.string.toast_no_sms_app, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        private const val TAG = "SmsLaunchActivity"
        private const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        private const val EXTRA_MESSAGE = "extra_message"
        private const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val NO_NOTIFICATION_ID = -1

        fun intentFor(
            context: Context,
            phoneNumber: String,
            message: String,
            notificationId: Int = NO_NOTIFICATION_ID
        ): Intent =
            Intent(context, SmsLaunchActivity::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
