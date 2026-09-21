package com.example.friendminder.ui.common

import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.friendminder.R
import com.example.friendminder.data.contacts.ContactPhotoLoader
import com.example.friendminder.data.models.Contact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shared photo-or-initials avatar binding, factored out of the old Phase 2
 * friend-list picker's `ContactAdapter` (FRM-39/40, retired on FRM-102) so
 * the other screens that also render contact avatars —
 * [com.example.friendminder.ui.home.HomeContactAdapter],
 * [com.example.friendminder.ui.addcontacts.AddContactCandidateAdapter],
 * GroupMemberAdapter, ContactDetailFragment's header — don't each
 * reimplement the async-load + [AvatarPalette] fallback dance.
 *
 * Callers are responsible for cancelling the returned [Job] when their view
 * is recycled/destroyed (same contract as those adapters' own
 * `cancelPendingPhotoLoad`).
 *
 * GH #116: a loaded photo is centre-cropped into a true circle via a custom
 * [ViewOutlineProvider] (deliberately not [ViewOutlineProvider.BACKGROUND] -
 * [AddContactsStep2Fragment][com.example.friendminder.ui.addcontacts.AddContactsStep2Fragment]
 * builds its avatar `ImageView`s in code with no background at all, and
 * that default provider clips to an *empty* outline when there's no
 * background to read a shape from, which would hide the photo entirely
 * rather than leave it square), and always takes the same 1dp hairline the
 * initials fallback already had for its light fills - set as [photoView]'s
 * *foreground* (bg_avatar_photo_ring.xml), not background, since an opaque
 * centerCrop bitmap fully covers the view's background and would hide a
 * stroke drawn there.
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
                photoView.foreground = ContextCompat.getDrawable(
                    photoView.context, R.drawable.bg_avatar_photo_ring
                )
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
        // GH #116: the oval outline provider and clipToOutline stay set
        // regardless of which state ends up showing - it's a no-op while
        // photoView is GONE/showing no image, and it's what makes bind()'s
        // centerCrop bitmap render as a circle rather than a square once a
        // photo loads. foreground is cleared here too: a recycled
        // RecyclerView ImageView can carry over the previous contact's
        // photo ring otherwise.
        photoView.outlineProvider = circularOutlineProvider
        photoView.clipToOutline = true
        photoView.foreground = null
        photoView.visibility = View.GONE
        initialsView.visibility = View.VISIBLE
        initialsView.text = contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        val paletteIndex = AvatarPalette.indexFor(contact.id)
        val avatarColor = AvatarPalette.colorFor(photoView.context, contact.id)
        val textColor = AvatarPalette.initialsTextColorFor(photoView.context, contact.id)

        initialsView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(avatarColor)
            // FRM-127 (DESIGN-SYSTEM-PHASE3.md SS2.6/SS4.3): the four light
            // fills (Sky, Seafoam, Sand, Rose) dissolve into the white row
            // with no edge, so they alone take a 1dp fm_divider hairline.
            // GradientDrawable draws a stroke inset from the drawable's
            // existing bounds rather than adding to them, so the circle's
            // overall diameter is unchanged whether or not this stroke is
            // present - rows stay aligned either way.
            if (AvatarPalette.isLightFill(paletteIndex)) {
                val hairlineWidthPx = (photoView.context.resources.displayMetrics.density).toInt()
                    .coerceAtLeast(1)
                setStroke(hairlineWidthPx, ContextCompat.getColor(photoView.context, R.color.fm_divider))
            }
        }
        initialsView.setTextColor(textColor)
    }

    // GH #116: view-bounds-based oval, independent of whatever (if
    // anything) photoView's own background happens to be - see the class
    // kdoc for why ViewOutlineProvider.BACKGROUND isn't safe here.
    private val circularOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
}
