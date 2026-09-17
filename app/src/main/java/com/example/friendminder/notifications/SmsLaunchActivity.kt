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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.friendminder.R

/**
 * PRD §16 Q1 (LOCKED): "implement one-tap send... if SEND_SMS permission
 * allows direct send, implement it; otherwise, require manual send
 * confirmation." SEND_SMS was declared in the manifest but, until now, never
 * requested at runtime, so the app always fell through to the "otherwise"
 * branch (ACTION_SENDTO pre-fill in the user's own SMS app). This now
 * requests SEND_SMS on first use and sends directly once granted
 * (chefcai/friend-minder#29), falling back to the pre-fill flow if the user
 * denies the permission, there's no message body to send, or the direct
 * send itself fails for any reason.
 */
class SmsLaunchActivity : Activity() {

    private var phoneNumber: String? = null
    private var message: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
        message = intent.getStringExtra(EXTRA_MESSAGE)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            sendDirectly()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.SEND_SMS), REQUEST_CODE_SEND_SMS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_SEND_SMS) return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            sendDirectly()
        } else {
            launchSmsAppFallback()
        }
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
        private const val REQUEST_CODE_SEND_SMS = 1001

        fun intentFor(context: Context, phoneNumber: String, message: String): Intent =
            Intent(context, SmsLaunchActivity::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_MESSAGE, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
