package com.example.friendminder.ui.common

import android.telephony.PhoneNumberUtils
import java.util.Locale

/**
 * FRM-129: a raw `ContactsContract` phone number is shown to the user
 * as-is everywhere except the add-contact candidate list, which renders it
 * through this formatter for readability. [PhoneNumberUtils.formatNumber]
 * already falls back to returning its input unchanged whenever it can't
 * confidently format it - malformed or partial source data reads as
 * exactly that, rather than being reshaped into something that looks like
 * a valid number it isn't (FRM-129's own worry: "a bare 639-37 looks like
 * a display bug even when the data is the problem").
 *
 * Deliberately NOT used anywhere a number is dialed, texted, or otherwise
 * acted on ([com.example.friendminder.notifications.SmsLaunchActivity],
 * [com.example.friendminder.ui.addcontacts.ContactBundleCodec]) - only the
 * raw value from [com.example.friendminder.data.contacts.ContactsLoader]
 * is safe to hand to the platform there.
 */
object PhoneNumberDisplay {

    fun format(rawNumber: String): String =
        PhoneNumberUtils.formatNumber(rawNumber, Locale.getDefault().country) ?: rawNumber
}
