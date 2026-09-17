package com.example.friendminder.ui.friendlist

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.friendminder.R
import com.example.friendminder.databinding.ItemContactBinding

class ContactAdapter(
    private val onRowClicked: (PickerItem) -> Unit
) : ListAdapter<PickerItem, ContactAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemContactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PickerItem) {
            binding.contactName.text = item.contact.name

            if (item.isMissing) {
                binding.contactCheckbox.visibility = View.GONE
                binding.contactNotFound.visibility = View.VISIBLE
                binding.contactRow.alpha = 0.6f
            } else {
                binding.contactCheckbox.visibility = View.VISIBLE
                binding.contactNotFound.visibility = View.GONE
                binding.contactRow.alpha = 1f
            }
            binding.contactCheckbox.isChecked = item.isSelected && !item.isMissing

            val photoUri = item.contact.photoUri
            val photoLoaded = !item.isMissing && photoUri != null && runCatching {
                binding.contactPhoto.setImageURI(Uri.parse(photoUri))
            }.isSuccess && binding.contactPhoto.drawable != null

            if (photoLoaded) {
                binding.contactPhoto.visibility = View.VISIBLE
                binding.contactInitial.visibility = View.GONE
            } else {
                binding.contactPhoto.visibility = View.GONE
                binding.contactInitial.visibility = View.VISIBLE
                binding.contactInitial.text =
                    item.contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                binding.contactInitial.background = ContextCompat.getDrawable(
                    binding.root.context,
                    if (item.isMissing) R.drawable.bg_avatar_missing else R.drawable.bg_avatar_placeholder
                )
            }

            binding.contactRow.setOnClickListener { onRowClicked(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PickerItem>() {
            override fun areItemsTheSame(oldItem: PickerItem, newItem: PickerItem) =
                oldItem.contact.id == newItem.contact.id

            override fun areContentsTheSame(oldItem: PickerItem, newItem: PickerItem) =
                oldItem == newItem
        }
    }
}
