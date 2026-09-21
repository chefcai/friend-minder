package com.example.friendminder.ui.addcontacts

import android.os.Bundle
import androidx.core.os.bundleOf
import com.example.friendminder.data.models.Contact

/**
 * Packs/unpacks a [List] of [Contact] into a [Bundle] as parallel String
 * lists, so Step 1 can hand its selection to
 * [AddContactsStep2Fragment] as fragment arguments. [Contact] isn't
 * [android.os.Parcelable] and this project has no kotlin-parcelize plugin
 * dependency (see build.gradle.kts - deliberately minimal dependency tree,
 * per MainActivity's kdoc); parallel arrays need nothing extra and Bundle
 * already supports them natively. [Contact.photoUri] is nullable, so it's
 * encoded as [NULL_PHOTO_URI_SENTINEL] rather than storing a shorter list
 * that would misalign the parallel indices.
 */
object ContactBundleCodec {
    private const val KEY_IDS = "codec_contact_ids"
    private const val KEY_NAMES = "codec_contact_names"
    private const val KEY_PHONE_NUMBERS = "codec_contact_phone_numbers"
    private const val KEY_PHOTO_URIS = "codec_contact_photo_uris"
    private const val NULL_PHOTO_URI_SENTINEL = ""

    fun encode(contacts: List<Contact>): Bundle = bundleOf(
        KEY_IDS to ArrayList(contacts.map { it.id }),
        KEY_NAMES to ArrayList(contacts.map { it.name }),
        KEY_PHONE_NUMBERS to ArrayList(contacts.map { it.phoneNumber }),
        KEY_PHOTO_URIS to ArrayList(contacts.map { it.photoUri ?: NULL_PHOTO_URI_SENTINEL })
    )

    fun decode(bundle: Bundle): List<Contact> {
        val ids = bundle.getStringArrayList(KEY_IDS).orEmpty()
        val names = bundle.getStringArrayList(KEY_NAMES).orEmpty()
        val phoneNumbers = bundle.getStringArrayList(KEY_PHONE_NUMBERS).orEmpty()
        val photoUris = bundle.getStringArrayList(KEY_PHOTO_URIS).orEmpty()
        return ids.indices.map { index ->
            Contact(
                id = ids[index],
                name = names[index],
                phoneNumber = phoneNumbers[index],
                photoUri = photoUris.getOrNull(index)?.takeIf { it != NULL_PHOTO_URI_SENTINEL }
            )
        }
    }
}
