package com.example.friendminder.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.friendminder.data.contacts.ContactsLoader
import com.example.friendminder.data.models.Contact

/**
 * GH #116: [com.example.friendminder.data.storage.FriendListRepository]'s
 * persisted snapshot never carries a contact's photo (see
 * [ContactsLoader.loadPhotoUris]'s kdoc), so every screen that renders an
 * already-tracked friend's avatar - Home, Contact Detail, Overall History,
 * Group Detail's member list - needs this same live-lookup-and-merge step
 * before handing its contacts to [AvatarBinder]. Factored out once rather
 * than reimplemented at each of those four call sites.
 *
 * Returns [friends] unchanged (all still `photoUri = null`, which
 * [AvatarBinder] already treats as "show initials") if `READ_CONTACTS`
 * isn't currently granted - the app requires that permission to add a
 * friend in the first place, so this is a defensive fallback for a
 * since-revoked permission, not the expected path. Matches GH #116's own
 * "degrade silently to initials if the permission has been revoked".
 */
suspend fun withLivePhotoUris(context: Context, friends: List<Contact>): List<Contact> {
    if (friends.isEmpty()) return friends
    val hasContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasContactsPermission) return friends

    val photoUris = ContactsLoader.loadPhotoUris(context, friends.mapTo(mutableSetOf()) { it.id })
    return friends.map { contact -> photoUris[contact.id]?.let { contact.copy(photoUri = it) } ?: contact }
}
