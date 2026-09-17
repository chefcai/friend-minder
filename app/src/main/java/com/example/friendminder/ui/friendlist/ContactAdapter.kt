package com.example.friendminder.ui.friendlist

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.friendminder.R
import com.example.friendminder.data.contacts.ContactPhotoLoader
import com.example.friendminder.databinding.ItemContactBinding
import com.example.friendminder.ui.common.AvatarPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * FRM-39/40/41: async contact photo loading (with [ContactPhotoLoader]'s
 * memory cache) falling back to a per-contact [AvatarPalette] initials
 * circle, plus a one-time staggered fade-in on first bind per Designer's
 * Confluence "Phase 2 UI Specs" §4 ("List fade-in: 300ms, staggered 20ms,
 * first 8 items").
 *
 * [scope] should be the hosting fragment's `viewLifecycleOwner.lifecycleScope`
 * so in-flight photo decodes are cancelled with the view, not leaked past it.
 */
class ContactAdapter(
    private val photoLoader: ContactPhotoLoader,
    private val scope: CoroutineScope,
    private val onRowClicked: (PickerItem) -> Unit
) : ListAdapter<PickerItem, ContactAdapter.ViewHolder>(DIFF) {

    /** Contact ids that have already played their fade-in, so re-binds (search filter, checkbox toggle) don't replay it. */
    private val alreadyAnimated = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelPendingPhotoLoad()
    }

    inner class ViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var photoLoadJob: Job? = null

        fun cancelPendingPhotoLoad() {
            photoLoadJob?.cancel()
            photoLoadJob = null
        }

        fun bind(item: PickerItem, position: Int) {
            // Guards against a stale ViewPropertyAnimator from a fast-scrolled-away
            // previous binding still running on this recycled row.
            binding.contactRow.animate().cancel()
            binding.contactName.text = item.contact.name

            val restingAlpha = if (item.isMissing) MISSING_CONTACT_ALPHA else 1f
            if (item.isMissing) {
                binding.contactCheckbox.visibility = View.GONE
                binding.contactNotFound.visibility = View.VISIBLE
            } else {
                binding.contactCheckbox.visibility = View.VISIBLE
                binding.contactNotFound.visibility = View.GONE
            }
            binding.contactCheckbox.isChecked = item.isSelected && !item.isMissing

            bindAvatar(item)
            binding.contactRow.setOnClickListener { onRowClicked(item) }

            playFadeInIfNeeded(item.contact.id, position, restingAlpha)
        }

        private fun bindAvatar(item: PickerItem) {
            cancelPendingPhotoLoad()

            if (item.isMissing) {
                binding.contactPhoto.visibility = View.GONE
                binding.contactInitial.visibility = View.VISIBLE
                binding.contactInitial.text = initialFor(item)
                binding.contactInitial.background =
                    ContextCompat.getDrawable(binding.root.context, R.drawable.bg_avatar_missing)
                return
            }

            // Show the initials fallback immediately (deterministic color, no flash of
            // empty view) while the photo — if any — decodes asynchronously.
            showInitialsFallback(item)

            val photoUri = item.contact.photoUri ?: return
            val contactId = item.contact.id
            photoLoadJob = scope.launch {
                val bitmap = photoLoader.load(binding.root.context, contactId, photoUri)
                if (bitmap != null) {
                    binding.contactPhoto.setImageBitmap(bitmap)
                    binding.contactPhoto.visibility = View.VISIBLE
                    binding.contactInitial.visibility = View.GONE
                }
                // On failure, the initials fallback already shown above stays as-is.
            }
        }

        private fun showInitialsFallback(item: PickerItem) {
            binding.contactPhoto.visibility = View.GONE
            binding.contactInitial.visibility = View.VISIBLE
            binding.contactInitial.text = initialFor(item)

            val isDarkTheme = (binding.root.context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val avatarColor = AvatarPalette.colorFor(binding.root.context, item.contact.id, isDarkTheme)
            val textColor = AvatarPalette.initialsTextColorFor(binding.root.context, item.contact.id, isDarkTheme)

            binding.contactInitial.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(avatarColor)
            }
            binding.contactInitial.setTextColor(textColor)
        }

        private fun initialFor(item: PickerItem): String =
            item.contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        private fun playFadeInIfNeeded(contactId: String, position: Int, restingAlpha: Float) {
            if (!alreadyAnimated.add(contactId)) {
                binding.contactRow.alpha = restingAlpha
                return
            }
            if (!ValueAnimator.areAnimatorsEnabled()) {
                binding.contactRow.alpha = restingAlpha // respects system "remove animations" setting
                return
            }

            val staggerDelay = (minOf(position, STAGGER_ITEM_COUNT - 1) * STAGGER_DELAY_MS).toLong()
            binding.contactRow.alpha = 0f
            binding.contactRow.translationY = FADE_IN_TRANSLATION_DP * binding.root.resources.displayMetrics.density
            binding.contactRow.animate()
                .alpha(restingAlpha)
                .translationY(0f)
                .setStartDelay(staggerDelay)
                .setDuration(FADE_IN_DURATION_MS)
                .start()
        }
    }

    companion object {
        private const val FADE_IN_DURATION_MS = 300L
        private const val STAGGER_DELAY_MS = 20L
        private const val STAGGER_ITEM_COUNT = 8
        private const val FADE_IN_TRANSLATION_DP = 8f
        private const val MISSING_CONTACT_ALPHA = 0.6f

        private val DIFF = object : DiffUtil.ItemCallback<PickerItem>() {
            override fun areItemsTheSame(oldItem: PickerItem, newItem: PickerItem) =
                oldItem.contact.id == newItem.contact.id

            override fun areContentsTheSame(oldItem: PickerItem, newItem: PickerItem) =
                oldItem == newItem
        }
    }
}
