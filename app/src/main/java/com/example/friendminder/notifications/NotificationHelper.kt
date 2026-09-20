package com.example.friendminder.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.friendminder.MainActivity
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.SpecialDate

private const val CHANNEL_ID = "daily_reminders"
private const val TEST_CHANNEL_ID = "test_notifications"
private const val TEST_NOTIFICATION_ID = -1
private const val TAG = "NotificationHelper"

object NotificationHelper {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    /**
     * FRM-78: a separate channel from [CHANNEL_ID], at default (not high)
     * importance, so a user can mute test notifications independently
     * without silencing real reminders - and so the channel name itself
     * labels a test notification in the shade, on top of the "[Test] "
     * title prefix.
     */
    fun ensureTestChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TEST_CHANNEL_ID,
                context.getString(R.string.notif_test_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_test_channel_desc)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    /**
     * FRM-78: "Send a test notification now" (HomeFragment) used to fire the
     * real [com.example.friendminder.work.SuggestionWorker], producing a
     * notification indistinguishable from a genuine reminder - same contact
     * name, same SMS quick-action - and tapping that action created a real
     * outreach log entry for a contact the app never actually suggested.
     * This posts a diagnostic-only notification instead: no contact
     * identity anywhere, no SMS action (tapping it opens Settings, via
     * [MainActivity]'s EXTRA_OPEN_SETTINGS handling), and its own
     * low-importance channel so it can't be mistaken for, or inflate a
     * user's count of, real reminders.
     */
    fun postTestNotification(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Skipping test notification: POST_NOTIFICATIONS not granted")
            return
        }

        ensureTestChannel(context)

        val openSettingsIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_SETTINGS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            TEST_NOTIFICATION_ID,
            openSettingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // FRM-78 a11y requirement: the test notification's content description must
        // carry the "[Test]" marker, not just the visual title - satisfied here since
        // the title itself (read by TalkBack as the notification's announced text) is
        // "[Test] Friend-Minder" rather than a bare "Friend-Minder".
        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_friend_minder)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
    }

    fun postReminder(context: Context, contact: Contact, messageTemplate: String) {
        // postReminder runs off SuggestionWorker (a background WorkManager job) with no UI to
        // prompt from - the in-app request/rationale flow lives in SettingsFragment. If the
        // user hasn't granted POST_NOTIFICATIONS (or revoked it since), just skip this
        // reminder rather than let notify() throw a SecurityException (FRM-18).
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Skipping reminder for contact ${contact.id}: POST_NOTIFICATIONS not granted")
            return
        }

        ensureChannel(context)

        // Full display name (GH #79): a first-name-only title/action label is ambiguous
        // whenever the user has two or more contacts who share a first name.
        val displayName = contact.name.trim().ifBlank { contact.name }
        val notificationId = contact.id.hashCode()

        // Passing our own notificationId through lets SmsLaunchActivity explicitly cancel this
        // notification itself rather than relying solely on setAutoCancel() below, which is
        // unreliable for action-button PendingIntents that launch an activity into a new task
        // (chefcai/friend-minder#35).
        val smsIntent = SmsLaunchActivity.intentFor(context, contact.phoneNumber, messageTemplate, contact.id, notificationId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            contact.id.hashCode(),
            smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_friend_minder)
            .setContentTitle(displayName)
            .setContentText(context.getString(R.string.notif_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.format_notif_action_text, displayName), pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
/**
     * Posts a birthday/special-date reminder (PRD §6.3, FRM-35). Reuses the
     * same tap-to-send flow as [postReminder] via [SmsLaunchActivity],
     * pre-filled with a festive template for birthdays and a generic one for
     * custom dates (anniversaries, etc.).
     */
    fun postSpecialDateReminder(context: Context, contact: Contact, specialDate: SpecialDate) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Skipping special date reminder for contact ${contact.id}: POST_NOTIFICATIONS not granted")
            return
        }

        ensureChannel(context)

        // First name only for the friendly in-message greeting ("Happy birthday, Mark!") -
        // but the full display name for the title/action label, same disambiguation fix as
        // postReminder above (GH #79).
        val firstName = contact.name.trim().substringBefore(' ').ifBlank { contact.name }
        val displayName = contact.name.trim().ifBlank { contact.name }
        // Namespaced with the SpecialDate id (rather than reusing postReminder's
        // contact.id.hashCode()) so a birthday reminder never collides with, or gets
        // silently replaced by, a same-day daily suggestion for the same contact.
        val notificationId = (contact.id + "_" + specialDate.id).hashCode()
        val isBirthday = specialDate.label.equals("Birthday", ignoreCase = true)
        val message = if (isBirthday) {
            context.getString(R.string.format_birthday_message, firstName)
        } else {
            context.getString(R.string.format_special_date_body, specialDate.label)
        }

        val smsIntent = SmsLaunchActivity.intentFor(context, contact.phoneNumber, message, contact.id, notificationId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_friend_minder)
            .setContentTitle(displayName)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.format_notif_action_text, displayName), pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
