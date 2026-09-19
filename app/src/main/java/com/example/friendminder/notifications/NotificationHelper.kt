package com.example.friendminder.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.SpecialDate

private const val CHANNEL_ID = "daily_reminders"
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

        val firstName = contact.name.trim().substringBefore(' ').ifBlank { contact.name }
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(firstName)
            .setContentText(context.getString(R.string.notif_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.format_notif_action_text, firstName), pendingIntent)
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

        val firstName = contact.name.trim().substringBefore(' ').ifBlank { contact.name }
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(firstName)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.format_notif_action_text, firstName), pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
