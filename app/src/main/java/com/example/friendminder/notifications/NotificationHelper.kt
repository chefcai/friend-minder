package com.example.friendminder.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact

private const val CHANNEL_ID = "daily_reminders"

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
        ensureChannel(context)

        val firstName = contact.name.trim().substringBefore(' ').ifBlank { contact.name }

        val smsIntent = SmsLaunchActivity.intentFor(context, contact.phoneNumber, messageTemplate)
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

        NotificationManagerCompat.from(context).notify(contact.id.hashCode(), notification)
    }
}
