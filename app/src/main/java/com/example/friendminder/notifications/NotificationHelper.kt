package com.example.friendminder.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.friendminder.data.models.Contact

private const val DAILY_REMINDER_CHANNEL_ID = "daily_reminder"

/**
 * Posts the daily "reach out to X" reminder notification (FRM-9) with a
 * one-tap action that opens the SMS composer pre-filled with
 * [Contact.phoneNumber] and the configured message template.
 *
 * The SMS intent here is a minimal, working baseline so FRM-9 is
 * independently testable end-to-end — FRM-11 (SMS Intent pre-fill) and
 * FRM-13 (notification action wiring) are Publisher's tickets and may
 * refactor/relocate this into a shared utility; see the FRM-9 Jira comment.
 */
object NotificationHelper {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(DAILY_REMINDER_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            DAILY_REMINDER_CHANNEL_ID,
            "Daily friend reminder",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Reminds you to reach out to someone from your Friend List"
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts the reminder for [contact]. Returns false (and posts nothing) if
     * POST_NOTIFICATIONS isn't granted on API 33+, so callers can decide
     * whether to still record the suggestion (FRM-10) or retry later.
     */
    fun postSuggestion(
        context: Context,
        contact: Contact,
        messageTemplate: String,
        notificationId: Int
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val smsIntent = buildSmsIntent(contact.phoneNumber, messageTemplate)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val firstName = contact.name.substringBefore(' ').ifBlank { contact.name }
        val largeIcon = contact.photoUri?.let { loadContactPhoto(context, it) }

        val builder = NotificationCompat.Builder(context, DAILY_REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Reach out to ${contact.name}")
            .setContentText("Tap to send a message")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Text $firstName", pendingIntent)

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            true
        }.getOrDefault(false)
    }

    private fun buildSmsIntent(phoneNumber: String, message: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("sms:$phoneNumber")).apply {
            putExtra("sms_body", message)
        }

    private fun loadContactPhoto(context: Context, photoUri: String): Bitmap? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
}
