package com.example.friendminder.ui.addcontacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.friendminder.R
import com.example.friendminder.data.contacts.ContactPhotoLoader
import com.example.friendminder.data.models.Contact
import com.example.friendminder.databinding.ItemAddContactCandidateBinding
import com.example.friendminder.ui.common.AvatarBinder
import com.example.friendminder.ui.common.PhoneNumberDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Add-contact flow, Step 1 candidate list (FRM-102, SCREENS-PHASE3.md
 * §9.2). Untracked device contacts only - already-tracked people never
 * reach this adapter (filtered before [submitList] by
 * [AddContactsStep1Fragment]).
 */
class AddContactCandidateAdapter(
    private val photoLoader: ContactPhotoLoader,
    private val scope: CoroutineScope,
    private val onRowClicked: (Contact) -> Unit
) : ListAdapter<AddContactCandidateRow, AddContactCandidateAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAddContactCandidateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelPendingPhotoLoad()
    }

    inner class ViewHolder(private val binding: ItemAddContactCandidateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var photoLoadJob: Job? = null

        fun cancelPendingPhotoLoad() {
            photoLoadJob?.cancel()
            photoLoadJob = null
        }

        fun bind(row: AddContactCandidateRow, position: Int) {
            binding.topDivider.visibility = if (position == 0) View.GONE else View.VISIBLE

            binding.candidateName.text = row.contact.name
            // §9.2: "'3 numbers' when the contact has several" - shown until
            // the row is selected, at which point the resolved single number
            // (chosen via the multi-number sheet, or the contact's only
            // number) is more useful than the count that got it there.
            binding.candidatePhone.text = if (row.phoneNumberCount > 1 && !row.isSelected) {
                binding.root.context.resources.getQuantityString(
                    R.plurals.format_candidate_phone_number_count, row.phoneNumberCount, row.phoneNumberCount
                )
            } else {
                // FRM-129: format for readability; falls back to the raw value
                // whenever it can't be confidently formatted (see PhoneNumberDisplay).
                PhoneNumberDisplay.format(row.contact.phoneNumber)
            }

            bindSelectionControl(row)
            bindAvatar(row.contact)

            binding.root.setOnClickListener { onRowClicked(row.contact) }
        }

        private fun bindSelectionControl(row: AddContactCandidateRow) {
            val context = binding.root.context
            binding.selectionControl.background = ContextCompat.getDrawable(
                context,
                if (row.isSelected) R.drawable.bg_status_badge_filled else R.drawable.bg_status_badge_ring
            )
            binding.selectionCheck.visibility = if (row.isSelected) View.VISIBLE else View.GONE
            binding.selectionControl.contentDescription = context.getString(
                if (row.isSelected) R.string.content_desc_candidate_selected else R.string.content_desc_candidate_not_selected
            )
        }

        private fun bindAvatar(contact: Contact) {
            cancelPendingPhotoLoad()
            photoLoadJob = AvatarBinder.bind(
                binding.contactPhoto, binding.contactInitial, contact, photoLoader, scope
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AddContactCandidateRow>() {
            override fun areItemsTheSame(oldItem: AddContactCandidateRow, newItem: AddContactCandidateRow) =
                oldItem.contact.id == newItem.contact.id

            override fun areContentsTheSame(oldItem: AddContactCandidateRow, newItem: AddContactCandidateRow) =
                oldItem == newItem
        }
    }
}

/** Pre-computed row data, mirroring HomeContactRow's shape (display data only, no ServiceLocator access from the adapter). */
data class AddContactCandidateRow(
    val contact: Contact,
    val isSelected: Boolean,
    val phoneNumberCount: Int
)
