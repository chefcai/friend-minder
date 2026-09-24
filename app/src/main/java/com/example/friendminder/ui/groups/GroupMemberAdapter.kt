package com.example.friendminder.ui.groups

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.friendminder.R
import com.example.friendminder.data.contacts.ContactPhotoLoader
import com.example.friendminder.data.models.Contact
import com.example.friendminder.databinding.ItemGroupMemberBinding
import com.example.friendminder.ui.common.AvatarBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/** One member row plus the selection state stamped on it (FRM-167). */
data class GroupMemberRow(
    val contact: Contact,
    val isSelectionMode: Boolean = false,
    val isSelected: Boolean = false
)

/**
 * Members of one group's detail screen (FRM-56); unbounded list, so unlike
 * Dashboard's bounded sections this is a real RecyclerView adapter.
 *
 * FRM-167 (GD-1): no per-row remove button. Long-press (or the "Select"
 * accessibility action) enters selection mode, same pattern as Home
 * (SCREENS Section 10); tap then toggles instead of opening the contact.
 */
class GroupMemberAdapter(
    private val photoLoader: ContactPhotoLoader,
    private val scope: CoroutineScope,
    private val onRowClicked: (Contact) -> Unit,
    private val onRowLongPressed: (Contact) -> Unit
) : ListAdapter<GroupMemberRow, GroupMemberAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelPendingPhotoLoad()
    }

    inner class ViewHolder(private val binding: ItemGroupMemberBinding) : RecyclerView.ViewHolder(binding.root) {
        private var photoLoadJob: Job? = null
        private var currentRow: GroupMemberRow? = null

        init {
            // Section 10.5: selection mode is reachable without a long-press.
            // Reads currentRow at invocation time, as Home does.
            ViewCompat.addAccessibilityAction(
                binding.root,
                binding.root.context.getString(R.string.content_desc_row_select_action)
            ) { _, _ ->
                currentRow?.let { onRowLongPressed(it.contact) }
                true
            }
        }

        fun cancelPendingPhotoLoad() {
            photoLoadJob?.cancel()
            photoLoadJob = null
        }

        fun bind(row: GroupMemberRow) {
            currentRow = row
            val contact = row.contact
            cancelPendingPhotoLoad()
            binding.memberName.text = contact.name
            photoLoadJob = AvatarBinder.bind(binding.contactPhoto, binding.contactInitial, contact, photoLoader, scope)
            binding.root.setOnClickListener { onRowClicked(contact) }
            binding.root.setOnLongClickListener { onRowLongPressed(contact); true }
            bindSelection(row)
        }

        private fun bindSelection(row: GroupMemberRow) {
            val context = binding.root.context
            if (!row.isSelectionMode) {
                binding.selectionSlot.visibility = View.GONE
                return
            }
            binding.selectionSlot.visibility = View.VISIBLE
            binding.selectionSlot.background = ContextCompat.getDrawable(
                context,
                if (row.isSelected) R.drawable.bg_status_badge_filled else R.drawable.bg_status_badge_ring
            )
            binding.selectionCheck.visibility = if (row.isSelected) View.VISIBLE else View.GONE
            binding.selectionSlot.contentDescription = context.getString(
                if (row.isSelected) R.string.content_desc_candidate_selected else R.string.content_desc_candidate_not_selected
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<GroupMemberRow>() {
            override fun areItemsTheSame(oldItem: GroupMemberRow, newItem: GroupMemberRow) =
                oldItem.contact.id == newItem.contact.id
            override fun areContentsTheSame(oldItem: GroupMemberRow, newItem: GroupMemberRow) = oldItem == newItem
        }
    }
}
