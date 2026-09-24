package com.example.friendminder.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.friendminder.data.models.ContactMethod

/**
 * FRM-185: Home's quick-action buttons open the phone's own SMS or dialer
 * app with the number pre-filled - "no in-app dialogs or prompts" is the
 * ticket's own requirement, so there is nothing else for this app to do
 * once the Intent is sent.
 *
 * [ContactMethod.CALL] uses [Intent.ACTION_DIAL], not `ACTION_CALL` - the
 * ticket's own recommendation ("to avoid permissions"): `ACTION_DIAL` opens
 * the dialer pre-filled and lets the person tap to place the call
 * themselves, needing no `CALL_PHONE` runtime permission at all, unlike
 * `ACTION_CALL`.
 */
fun contactActionIntent(method: ContactMethod, phoneNumber: String): Intent = when (method) {
    ContactMethod.SMS -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phoneNumber, null))
    ContactMethod.CALL -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phoneNumber, null))
}

/** Builds and launches [contactActionIntent] - split out so the Intent's shape is testable without starting an Activity. */
fun launchContactAction(context: Context, method: ContactMethod, phoneNumber: String) {
    context.startActivity(contactActionIntent(method, phoneNumber))
}
