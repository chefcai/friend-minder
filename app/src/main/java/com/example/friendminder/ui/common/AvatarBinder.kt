package com.example.friendminder.ui.common

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.friendminder.data.contacts.ContactPhotoLoader
import com.example.friendminder.data.models.Contact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shared photo-or-initials avatar binding, factored out of
 * [com.example.friendminder.ui.friendlist.ContactAdapter] (FRM-39/40) so the
 * Phase 2 screens that also render contact avatars — GroupMemberAdapter,
 * Dashboard's streak/neglected rows, ContactDetailFragment's header — don't
 * each reimplement the async-load + [AvatarPalette] fallback dance.
 *
 * Callers are responsible for cancelling the returned [Job] when their view
 * is recycled/destroyed (same contract as ContactAdapter's own
 * `cancelPendingPhotoLoad`).
 */
object AvatarBinder {

    fun bind(
        photoView: ImageView,
        initialsView: TextView,
        contact: Contact,
        photoLoader: ContactPhotoLoader,
        scope: CoroutineScope
    ): Job? {
        showInitialsFallback(photoView, initialsView, contact)

        val photoUri = contact.photoUri ?: return null
        return scope.launch {
            val bitmap = photoLoader.load(photoView.context, contact.id, photoUri)
            if (bitmap != null) {
                photoView.setImageBitmap(bitmap)
                photoView.visibility = View.VISIBLE
                initialsView.visibility = View.GONE
            }
        }
    }

    private fun showInitialsFallback(
        photoView: ImageView,
        initialsView: TextView,
        contact: Contact
    ) {
        photoView.visibility = View.GONE
        initialsView.visibility = View.VISIBLE
        initialsView.text = contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        val avatarColor = AvatarPalette.colorFor(photoView.context, contact.id)
        val textColor = AvatarPalette.initialsTextColorFor(photoView.context, contact.id)

        initialsView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(avatarColor)
        }
        initialsView.setTextColor(textColor)
    }
}
