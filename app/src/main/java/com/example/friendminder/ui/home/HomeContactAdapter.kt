package com.example.friendminder.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.friendminder.R
import com.example.friendminder.data.contacts.ContactPhotoLoader
import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.ContactMethod
import com.example.friendminder.databinding.ItemHomeContactBinding
import com.example.friendminder.ui.common.AvatarBinder
import com.example.friendminder.ui.common.launchContactAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Home's flat, alphabetical contact list (FRM-99, SCREENS-PHASE3.md
 * §1.3/§1.5). No search, no swipe. Row long-press enters bulk-removal
 * selection mode (FRM-112, §10.2 - amending §1.3's original "Row
 * long-press: nothing in v1" per Cai's proposal); tap either opens Contact
 * Detail or toggles selection, and [HomeContactRow.isSelectionMode] is
 * what tells this adapter which - see [HomeFragment] for the actual mode
 * state and the branching logic behind [onRowClicked]/[onRowLongPressed].
 * Every row also carries a custom "Select" accessibility action
 * regardless of mode (§10.5), since long-press has no TalkBack-reachable
 * equivalent.
 */
class HomeContactAdapter(
    private val photoLoader: ContactPhotoLoader,
    private val scope: CoroutineScope,
    private val onRowClicked: (Contact) -> Unit,
    private val onRowLongPressed: (Contact) -> Unit
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
        private var currentRow: HomeContactRow? = null

        init {
            // Registered once per ViewHolder (not per bind, to avoid piling
            // up duplicate custom actions across rebinds) and reads
            // currentRow at invocation time rather than closing over the
            // row passed to bind() - see §10.5.
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

        fun bind(row: HomeContactRow, position: Int) {
            currentRow = row
            // §1.3: "Divider between rows only - not after the last row, and
            // not between the header and the first row." A top divider hidden
            // on position 0 satisfies both halves of that at once.
            binding.topDivider.visibility = if (position == 0) View.GONE else View.VISIBLE

            binding.contactName.text = row.contact.name
            binding.lastTouch.text = row.lastTouchText

            bindBadge(row)
            bindAvatar(row.contact)
            bindQuickActions(row)

            binding.root.setOnClickListener { onRowClicked(row.contact) }
            binding.root.setOnLongClickListener { onRowLongPressed(row.contact); true }
        }

        private fun bindBadge(row: HomeContactRow) {
            val context = binding.root.context
            if (row.isSelectionMode) {
                bindSelectionControl(row)
                return
            }
            binding.selectionCheck.visibility = View.GONE
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

        // FRM-112 (§10.3): the same 28dp slot, reusing
        // bg_status_badge_ring/filled and ic_check verbatim from
        // item_add_contact_candidate.xml's selectionControl - "nothing
        // resizes, the control is the one already specced for FRM-102".
        private fun bindSelectionControl(row: HomeContactRow) {
            val context = binding.root.context
            binding.statusBadgeNumeral.visibility = View.GONE
            binding.statusBadge.background = androidx.core.content.ContextCompat.getDrawable(
                context,
                if (row.isSelected) R.drawable.bg_status_badge_filled else R.drawable.bg_status_badge_ring
            )
            binding.selectionCheck.visibility = if (row.isSelected) View.VISIBLE else View.GONE
            binding.statusBadge.contentDescription = context.getString(
                if (row.isSelected) R.string.content_desc_candidate_selected else R.string.content_desc_candidate_not_selected
            )
        }

        // FRM-185: hidden entirely in selection mode (row.isSelectionMode) -
        // a tap here must never double as a row-select tap, and hiding
        // (rather than merely disabling) also gives the row's tap-to-select
        // target the full row width back, matching how the badge slot
        // itself switches to the selection check in that mode (bindBadge).
        // Single adaptive icon (superseded the always-both-icons design
        // after a too-crowded preview, see item_home_contact.xml's
        // comment): glyph, content description and click target all swap
        // together based on row.preferredMethod, so exactly one action is
        // ever offered from Home - reaching the non-preferred method still
        // requires Contact Detail (FRM-184).
        private fun bindQuickActions(row: HomeContactRow) {
            if (row.isSelectionMode) {
                binding.quickActionButton.visibility = View.GONE
                return
            }
            val context = binding.root.context
            binding.quickActionButton.visibility = View.VISIBLE

            val iconRes: Int
            val contentDescRes: Int
            when (row.preferredMethod) {
                ContactMethod.SMS -> {
                    iconRes = R.drawable.ic_sms_24
                    contentDescRes = R.string.format_content_desc_quick_action_sms
                }
                ContactMethod.CALL -> {
                    iconRes = R.drawable.ic_call_24
                    contentDescRes = R.string.format_content_desc_quick_action_call
                }
            }
            binding.quickActionButton.setImageResource(iconRes)
            binding.quickActionButton.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(context, R.color.fm_primary)
            )
            binding.quickActionButton.contentDescription =
                context.getString(contentDescRes, row.contact.name)

            binding.quickActionButton.setOnClickListener {
                launchContactAction(context, row.preferredMethod, row.contact.phoneNumber)
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

/**
 * Pre-computed row data - display string and streak, not raw stats, so the
 * adapter never touches ServiceLocator. [isSelectionMode]/[isSelected]
 * (FRM-112, §10.3) default to normal-mode values so nothing else that
 * constructs a HomeContactRow needs to change.
 */
data class HomeContactRow(
    val contact: Contact,
    val streak: Int,
    val lastTouchText: String,
    val preferredMethod: ContactMethod,
    val isSelectionMode: Boolean = false,
    val isSelected: Boolean = false
)
