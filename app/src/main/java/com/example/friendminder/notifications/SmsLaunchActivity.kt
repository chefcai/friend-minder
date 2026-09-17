package com.example.friendminder.notifications

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.example.friendminder.R

class SmsLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
        val message = intent.getStringExtra(EXTRA_MESSAGE)

        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("sms:$phoneNumber")
            if (!message.isNullOrBlank()) putExtra("sms_body", message)
        }

        try {
            startActivity(smsIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.toast_no_sms_app, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        private const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        private const val EXTRA_MESSAGE = "extra_message"

        fun intentFor(context: Context, phoneNumber: String, message: String): Intent =
            Intent(context, SmsLaunchActivity::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_MESSAGE, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
