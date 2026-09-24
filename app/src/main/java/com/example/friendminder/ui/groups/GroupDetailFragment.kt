package com.example.friendminder.ui.groups

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.ContactGroup
import com.example.friendminder.databinding.FragmentGroupDetailBinding
import com.example.friendminder.ui.common.EdgeToEdgeHeader
import com.example.friendminder.ui.common.ValuePickerDialogFragment
import com.example.friendminder.ui.common.withLivePhotoUris
import com.example.friendminder.ui.contactdetail.ContactDetailFragment
import com.example.friendminder.ui.home.HomeLastTouchFormatter
import com.example.friendminder.utils.ServiceLocator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Members of one group (FRM-56, SCREENS-PHASE2.md §3 "Group Detail"). */
class GroupDetailFragment : Fragment() {

    private var _binding: FragmentGroupDetailBinding? = null
    private val binding get() = _binding!!

    private val groupId: String by lazy { requireArguments().getString(ARG_GROUP_ID)!! }
    private lateinit var adapter: GroupMemberAdapter
    private var currentGroup: ContactGroup? = null

    // FRM-167 (GD-1): selection mode for removing members, same model as
    // Home (SCREENS Section 10) - the mode is on while the set is non-empty.
    private val selectedIds = linkedSetOf<String>()
    private val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    private var members: List<Contact> = emptyList()
    private var lastTouchById: Map<String, String> = emptyMap()
    private lateinit var selectionBackCallback: OnBackPressedCallback

    // Set by Undo: once the restored rows are committed, scroll so the
    // first restored member is on screen. Otherwise a member re-inserted
    // above the first visible row lands just off-screen.
    private var scrollToAfterCommit: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // FRM-167: in selection mode the leading icon is the mode's X
        // (Section 10.2: leave via the X, system back, or deselecting the
        // last member).
        val exitSelection = {
            selectedIds.clear()
            updateSelectionModeUi()
            renderRows()
        }
        binding.backButton.setOnClickListener {
            if (isSelectionMode) exitSelection() else requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        selectionBackCallback = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, enabled = false) {
            exitSelection()
        }
        ViewCompat.setAccessibilityLiveRegion(binding.headerTitle, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        binding.headerActionButton.setOnClickListener {
            GroupEditDialogFragment.newInstance(groupId).show(childFragmentManager, "group_edit")
        }
        EdgeToEdgeHeader.applyHeaderInsets(binding.headerContainer, binding.statusBarSpacer)

        adapter = GroupMemberAdapter(
            photoLoader = ServiceLocator.contactPhotoLoader,
            scope = viewLifecycleOwner.lifecycleScope,
            onRowClicked = { contact -> if (isSelectionMode) toggleSelection(contact.id) else openContactDetail(contact) },
            onRowLongPressed = { contact -> toggleSelection(contact.id) }
        )
        binding.memberRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.memberRecyclerView.adapter = adapter

        val openAddContactDialog = {
            AddContactToGroupDialogFragment.newInstance(groupId).show(childFragmentManager, "add_to_group")
        }
        binding.addContactFab.setOnClickListener {
            if (isSelectionMode) removeSelected() else openAddContactDialog()
        }
        // FRM-119 follow-up: this empty-state button is the "own button"
        // the FAB defers to per FRM-103's spec - same destination as the
        // FAB itself, just reachable when the FAB is hidden.
        binding.addMemberButton.setOnClickListener { openAddContactDialog() }
        binding.checkInFrequencyRow.setOnClickListener { showFrequencyPicker() }

        childFragmentManager.setFragmentResultListener(AddContactToGroupDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, _ ->
            refresh()
        }
        childFragmentManager.setFragmentResultListener(GroupEditDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, _ ->
            refresh()
        }
        childFragmentManager.setFragmentResultListener(FREQUENCY_PICKER_REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            val picked = bundle.getInt(ValuePickerDialogFragment.RESULT_VALUE)
            viewLifecycleOwner.lifecycleScope.launch {
                // NO_OVERRIDE_SENTINEL (0) is this row's own convention for
                // "clear the override" - GroupService.setReminderFrequency
                // itself only ever sees null, never 0 (see the sentinel's
                // own kdoc in strings.xml).
                ServiceLocator.groupService.setReminderFrequency(
                    groupId, if (picked == NO_OVERRIDE_SENTINEL) null else picked
                )
                refresh()
            }
        }
        // GH #117: Contact Detail's own "Stop tracking" FAB pops back here
        // after removing a member - same pop-back-stack-with-result
        // pattern as this fragment's own AddContactToGroupDialogFragment/
        // GroupEditDialogFragment listeners above, just registered on
        // parentFragmentManager instead of childFragmentManager since
        // Contact Detail is a sibling replacement, not a child dialog (see
        // ContactDetailFragment.performStopTracking()'s kdoc). No nav-bar
        // timing concern here (unlike HomeFragment's own listener for the
        // same result) since Group Detail hides the bottom nav itself and
        // this Snackbar isn't anchored to it.
        parentFragmentManager.setFragmentResultListener(
            ContactDetailFragment.CONTACT_REMOVED_RESULT_KEY, viewLifecycleOwner
        ) { _, bundle ->
            val removedName = bundle.getString(ContactDetailFragment.RESULT_REMOVED_CONTACT_NAME).orEmpty()
            refresh()
            Snackbar.make(
                binding.root, getString(R.string.format_stop_tracking_removed_snackbar, removedName), Snackbar.LENGTH_LONG
            ).show()
        }
    }

    // GH #121: unlike ContactDetailFragment's frequency picker (which
    // canonicalizes "equals the global default" down to "no override" so a
    // contact keeps tracking future default changes), a group's own
    // interval is deliberately NOT canonicalized that way here - a group
    // explicitly set to the same number as today's global default should
    // stay pinned at that number if the global default later changes,
    // which is the whole point of setting a per-group interval rather than
    // leaving the group unset. That's why this needs an explicit
    // "no override" option in the list rather than reusing the
    // equals-default trick.
    private fun showFrequencyPicker() {
        val group = currentGroup ?: return
        val presetOptions = FREQUENCY_OPTIONS.map { days ->
            days to when (days) {
                FREQUENCY_OPTION_7 -> getString(R.string.cooldown_option_7)
                FREQUENCY_OPTION_14 -> getString(R.string.cooldown_option_14)
                else -> getString(R.string.cooldown_option_3)
            }
        }
        ValuePickerDialogFragment.newInstance(
            requestKey = FREQUENCY_PICKER_REQUEST_KEY,
            title = getString(R.string.label_group_checkin_frequency),
            options = listOf(NO_OVERRIDE_SENTINEL to getString(R.string.label_group_frequency_no_override)) + presetOptions,
            selectedValue = group.reminderFrequencyDays ?: NO_OVERRIDE_SENTINEL,
            customEntry = ValuePickerDialogFragment.CustomEntryOptions()
        ).show(childFragmentManager, "group_frequency_picker")
    }

    override fun onResume() {
        super.onResume()
        EdgeToEdgeHeader.applyEdgeToEdgeHeader(this)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        EdgeToEdgeHeader.restoreStandardStatusBar(this)
    }

    private fun openContactDetail(contact: Contact) {
        parentFragmentManager.commit {
            replace(R.id.nav_host_container, ContactDetailFragment.newInstance(contact.id))
            addToBackStack(null)
        }
    }

    private fun toggleSelection(contactId: String) {
        if (!selectedIds.remove(contactId)) selectedIds.add(contactId)
        updateSelectionModeUi()
        renderRows()
    }

    /**
     * FRM-167: header, leading icon, edit action and FAB swap together.
     * The FAB stays fm_primary in both modes - leaving a group is
     * non-destructive (the contact is still tracked), unlike Home's
     * bulk stop-tracking, which is red.
     */
    private fun updateSelectionModeUi() {
        selectionBackCallback.isEnabled = isSelectionMode
        val fab = binding.addContactFab
        fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.fm_primary))
        if (isSelectionMode) {
            binding.backButton.setImageResource(R.drawable.ic_close)
            binding.backButton.contentDescription = getString(R.string.content_desc_close_selection_mode)
            binding.headerTitle.text = getString(R.string.format_selected_count, selectedIds.size)
            binding.headerActionButton.visibility = View.GONE
            fab.setImageResource(R.drawable.ic_person_remove)
            fab.contentDescription = resources.getQuantityString(
                R.plurals.content_desc_remove_members_fab, selectedIds.size, selectedIds.size
            )
        } else {
            binding.backButton.setImageResource(R.drawable.ic_arrow_back)
            binding.backButton.contentDescription = getString(R.string.content_desc_back)
            binding.headerTitle.text = currentGroup?.name.orEmpty()
            binding.headerActionButton.visibility = View.VISIBLE
            fab.setImageResource(R.drawable.ic_add)
            fab.contentDescription = getString(R.string.action_add_contact_to_group)
        }
    }

    /**
     * FRM-167: removal is immediate, then a 4-second "N removed" snackbar
     * with Undo (PHASE-3-LESSONS decision) re-adds the same members. The
     * snackbar is anchored to the FAB (or the bottom nav when the FAB is
     * hidden on the empty state), so it sits above both instead of over
     * them and the Undo tap can't land on either.
     */
    private fun removeSelected() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        selectedIds.clear()
        updateSelectionModeUi()
        viewLifecycleOwner.lifecycleScope.launch {
            val groupService = ServiceLocator.groupService
            ids.forEach { groupService.removeContactFromGroup(it, groupId) }
            reload()
            val anchor = binding.addContactFab.takeIf { it.visibility == View.VISIBLE }
                ?: requireActivity().findViewById<View>(R.id.bottomNav)?.takeIf { it.visibility == View.VISIBLE }
            Snackbar.make(
                binding.root,
                resources.getQuantityString(R.plurals.format_members_removed_snackbar, ids.size, ids.size),
                UNDO_SNACKBAR_DURATION_MS
            ).setAction(R.string.action_undo) {
                viewLifecycleOwner.lifecycleScope.launch {
                    ids.forEach { groupService.assignContactToGroup(it, groupId) }
                    scrollToAfterCommit = ids.first()
                    reload()
                }
            }.setAnchorView(anchor).show()
        }
    }

    private fun renderRows() {
        adapter.submitList(
            members.map {
                GroupMemberRow(
                    it,
                    lastTouchText = lastTouchById[it.id].orEmpty(),
                    isSelectionMode = isSelectionMode,
                    isSelected = it.id in selectedIds
                )
            }
        ) {
            val target = scrollToAfterCommit ?: return@submitList
            scrollToAfterCommit = null
            val index = members.indexOfFirst { it.id == target }
            if (index >= 0 && _binding != null) binding.memberRecyclerView.scrollToPosition(index)
        }
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch { reload() }
    }

    // FRM-167: suspend form of refresh() so removeSelected can pick its
    // snackbar anchor after the FAB's visibility is settled.
    private suspend fun reload() {
        val group = ServiceLocator.contactGroupRepository.getGroup(groupId)
        if (group == null) {
            requireActivity().onBackPressedDispatcher.onBackPressed()
            return
        }
        currentGroup = group
        renderFrequencyValue(group)

        // GH #116: enrich with each member's live device-contact
        // photo before building rows - see withLivePhotoUris' kdoc.
        members = withLivePhotoUris(
            requireContext(), ServiceLocator.groupService.getContactsInGroup(groupId)
        ).sortedBy { it.name.lowercase() }
        // FRM-168 (GD-2): the same last-touch line Home shows under the name.
        val context = requireContext()
        lastTouchById = members.associate { contact ->
            val stats = ServiceLocator.statisticsService.getStatistics(contact.id)
            contact.id to HomeLastTouchFormatter.format(context, stats.lastContacted)
        }
        if (_binding == null) return
        binding.emptyStateContainer.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
        binding.memberRecyclerView.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
        // FRM-119: FAB is hidden on the empty state in favour of that
        // state's own affordance (emptyStateContainer's
        // addMemberButton), same convention as GroupsFragment's
        // addGroupFab/createFirstGroupButton.
        binding.addContactFab.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
        // Drop selected ids that are no longer members (removed elsewhere).
        selectedIds.retainAll(members.map { it.id }.toSet())
        updateSelectionModeUi()
        renderRows()
    }

    private fun renderFrequencyValue(group: ContactGroup) {
        val days = group.reminderFrequencyDays
        binding.groupFrequencyValue.text = when {
            days == null -> getString(R.string.label_group_frequency_no_override)
            days == FREQUENCY_OPTION_3 -> getString(R.string.cooldown_option_3)
            days == FREQUENCY_OPTION_7 -> getString(R.string.cooldown_option_7)
            days == FREQUENCY_OPTION_14 -> getString(R.string.cooldown_option_14)
            else -> resources.getQuantityString(R.plurals.format_days_option, days, days)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_GROUP_ID = "arg_group_id"

        // GH #121: sentinel this row uses in the shared picker's value list
        // to mean "clear the override" - GroupService.setReminderFrequency
        // itself only ever sees this translated to null (see the click
        // listener above). 0 is safe as a sentinel because
        // GroupService.setReminderFrequency requires 1..30 for any real
        // value.
        private const val NO_OVERRIDE_SENTINEL = 0
        private const val FREQUENCY_OPTION_3 = 3
        private const val FREQUENCY_OPTION_7 = 7
        private const val FREQUENCY_OPTION_14 = 14
        private val FREQUENCY_OPTIONS = intArrayOf(FREQUENCY_OPTION_3, FREQUENCY_OPTION_7, FREQUENCY_OPTION_14)
        private const val FREQUENCY_PICKER_REQUEST_KEY = "group_detail_frequency_picker"
        private const val UNDO_SNACKBAR_DURATION_MS = 4000

        fun newInstance(groupId: String): GroupDetailFragment = GroupDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_GROUP_ID, groupId) }
        }
    }
}
