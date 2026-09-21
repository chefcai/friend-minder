package com.example.friendminder.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.friendminder.R
import com.example.friendminder.data.contacts.ContactPhotoLoader
import com.example.friendminder.data.models.Contact
import com.example.friendminder.databinding.ItemHomeContactBinding
import com.example.friendminder.ui.common.AvatarBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Home's flat, alphabetical contact list (FRM-99, SCREENS-PHASE3.md
 * §1.3/§1.5). No search, no long-press, no swipe - row tap is the only
 * interaction (§1.3: "Row long-press: nothing in v1. No context menu, no
 * swipe actions.").
 */
class HomeContactAdapter(
    private val photoLoader: ContactPhotoLoader,
    private val scope: CoroutineScope,
    private val onRowClicked: (Contact) -> Unit
) : ListAdapter<HomeContactRow, HomeContactAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHomeContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelPendingPhotoLoad()
    }

    inner class ViewHolder(private val binding: ItemHomeContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var photoLoadJob: Job? = null

        fun cancelPendingPhotoLoad() {
            photoLoadJob?.cancel()
            photoLoadJob = null
        }

        fun bind(row: HomeContactRow, position: Int) {
            // §1.3: "Divider between rows only - not after the last row, and
            // not between the header and the first row." A top divider hidden
            // on position 0 satisfies both halves of that at once.
            binding.topDivider.visibility = if (position == 0) View.GONE else View.VISIBLE

            binding.contactName.text = row.contact.name
            binding.lastTouch.text = row.lastTouchText

            bindBadge(row)
            bindAvatar(row.contact)

            binding.root.setOnClickListener { onRowClicked(row.contact) }
        }

        private fun bindBadge(row: HomeContactRow) {
            val context = binding.root.context
            if (row.streak > 0) {
                binding.statusBadge.background =
                    androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_status_badge_filled)
                binding.statusBadgeNumeral.visibility = View.VISIBLE
                binding.statusBadgeNumeral.text = if (row.streak > MAX_DISPLAYED_STREAK) {
                    context.getString(R.string.format_streak_badge_overflow)
                } else {
                    row.streak.toString()
                }
                binding.statusBadge.contentDescription =
                    context.resources.getQuantityString(R.plurals.format_home_streak_description, row.streak, row.streak)
            } else {
                binding.statusBadge.background =
                    androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_status_badge_ring)
                binding.statusBadgeNumeral.visibility = View.GONE
                binding.statusBadge.contentDescription =
                    context.getString(R.string.format_home_no_streak_description, row.lastTouchText)
            }
        }

        private fun bindAvatar(contact: Contact) {
            cancelPendingPhotoLoad()
            photoLoadJob = AvatarBinder.bind(
                binding.contactPhoto, binding.contactInitial, contact, photoLoader, scope
            )
        }
    }

    companion object {
        private const val MAX_DISPLAYED_STREAK = 9

        private val DIFF = object : DiffUtil.ItemCallback<HomeContactRow>() {
            override fun areItemsTheSame(oldItem: HomeContactRow, newItem: HomeContactRow) =
                oldItem.contact.id == newItem.contact.id

            override fun areContentsTheSame(oldItem: HomeContactRow, newItem: HomeContactRow) =
                oldItem == newItem
        }
    }
}

/** Pre-computed row data - display string and streak, not raw stats, so the adapter never touches ServiceLocator. */
data class HomeContactRow(
    val contact: Contact,
    val streak: Int,
    val lastTouchText: String
)
