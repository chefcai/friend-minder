package com.example.friendminder.ui.groups

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.friendminder.R
import com.example.friendminder.data.models.ContactGroup
import com.example.friendminder.databinding.ItemGroupBinding

/** One row per group (FRM-56), member count resolved ahead of time by [GroupsFragment] into [GroupRow]. */
data class GroupRow(val group: ContactGroup, val memberCount: Int)

class GroupAdapter(
    private val onClick: (ContactGroup) -> Unit,
    private val onLongClick: (ContactGroup) -> Unit
) : ListAdapter<GroupRow, GroupAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: GroupRow) {
            binding.groupName.text = row.group.name
            binding.groupMemberCount.text = binding.root.resources.getQuantityString(
                R.plurals.format_group_member_count, row.memberCount, row.memberCount
            )
            binding.groupColorDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(row.group.color)
                // FRM-117: "fm_group_color_7 (Mist) is pale enough to need
                // the same hairline the light avatars get" - mirrors
                // AvatarBinder's isLightFill() stroke treatment, but group
                // colors are a raw stored Int rather than a palette index,
                // so this compares directly against the one color Designer
                // called out rather than reusing AvatarPalette's lookup.
                if (row.group.color == mistColor(binding.root.context)) {
                    val hairlineWidthPx = (binding.root.context.resources.displayMetrics.density).toInt()
                        .coerceAtLeast(1)
                    setStroke(hairlineWidthPx, ContextCompat.getColor(binding.root.context, R.color.fm_divider))
                }
            }
            binding.root.setOnClickListener { onClick(row.group) }
            binding.root.setOnLongClickListener { onLongClick(row.group); true }
        }

        private fun mistColor(context: android.content.Context): Int =
            ContextCompat.getColor(context, R.color.fm_group_color_7)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<GroupRow>() {
            override fun areItemsTheSame(oldItem: GroupRow, newItem: GroupRow) = oldItem.group.id == newItem.group.id
            override fun areContentsTheSame(oldItem: GroupRow, newItem: GroupRow) = oldItem == newItem
        }
    }
}
