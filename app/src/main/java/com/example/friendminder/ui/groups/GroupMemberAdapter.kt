package com.example.friendminder.ui.groups

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.friendminder.data.contacts.ContactPhotoLoader
import com.example.friendminder.data.models.Contact
import com.example.friendminder.databinding.ItemGroupMemberBinding
import com.example.friendminder.ui.common.AvatarBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Members of one group's detail screen (FRM-56); unbounded list, so unlike
 * Dashboard's bounded sections this is a real RecyclerView adapter.
 */
class GroupMemberAdapter(
    private val photoLoader: ContactPhotoLoader,
    private val scope: CoroutineScope,
    private val onRowClicked: (Contact) -> Unit,
    private val onRemoveClicked: (Contact) -> Unit
) : ListAdapter<Contact, GroupMemberAdapter.ViewHolder>(DIFF) {

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

        fun cancelPendingPhotoLoad() {
            photoLoadJob?.cancel()
            photoLoadJob = null
        }

        fun bind(contact: Contact) {
            cancelPendingPhotoLoad()
            binding.memberName.text = contact.name
            photoLoadJob = AvatarBinder.bind(binding.contactPhoto, binding.contactInitial, contact, photoLoader, scope)
            binding.root.setOnClickListener { onRowClicked(contact) }
            binding.removeButton.setOnClickListener { onRemoveClicked(contact) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(oldItem: Contact, newItem: Contact) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Contact, newItem: Contact) = oldItem == newItem
        }
    }
}
