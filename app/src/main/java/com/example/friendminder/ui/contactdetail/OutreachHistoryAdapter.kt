package com.example.friendminder.ui.contactdetail

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.friendminder.R
import com.example.friendminder.data.models.OutreachLog
import com.example.friendminder.data.models.OutreachType
import com.example.friendminder.databinding.ItemOutreachLogBinding

/** Newest-first outreach history for one contact (FRM-57, "History" tab). Unbounded, so a real RecyclerView adapter. */
class OutreachHistoryAdapter : ListAdapter<OutreachLog, OutreachHistoryAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOutreachLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemOutreachLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: OutreachLog) {
            val context = binding.root.context
            binding.outreachTypeLabel.text = typeLabel(context, log.type)
            binding.outreachTimestamp.text = DateUtils.getRelativeTimeSpanString(
                log.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            binding.outreachNote.text = log.note
            binding.outreachNote.visibility = if (log.note.isNullOrBlank()) View.GONE else View.VISIBLE
        }

        private fun typeLabel(context: android.content.Context, type: OutreachType): String = when (type) {
            OutreachType.SMS -> context.getString(R.string.outreach_type_sms)
            OutreachType.CALL -> context.getString(R.string.outreach_type_call)
            OutreachType.IN_PERSON -> context.getString(R.string.outreach_type_in_person)
            OutreachType.VIDEO -> context.getString(R.string.outreach_type_video)
            OutreachType.OTHER -> context.getString(R.string.outreach_type_other)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<OutreachLog>() {
            override fun areItemsTheSame(oldItem: OutreachLog, newItem: OutreachLog) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: OutreachLog, newItem: OutreachLog) = oldItem == newItem
        }
    }
}
