package com.example.friendminder.ui.addcontacts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.data.models.ContactGroup
import com.example.friendminder.databinding.DialogAddContactToGroupBinding
import com.example.friendminder.ui.common.FmBottomSheet
import com.example.friendminder.ui.groups.GroupEditDialogFragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Add-contact flow, Step 2 "Groups" row (FRM-102, SCREENS-PHASE3.md §9.4):
 * "a bottom-sheet multi-select of existing groups, plus 'New group'."
 *
 * Deliberately separate from
 * [com.example.friendminder.ui.contactdetail.EditContactGroupsDialogFragment],
 * which this otherwise closely resembles (same shared layout, same
 * checkbox-per-group shape): that class toggles membership for one *real,
 * already-persisted* contact, writing straight to
 * [com.example.friendminder.domain.services.GroupService] as soon as
 * Save is tapped. This one has no contact yet -
 * Step 2 runs before any of the selected people are added - so it only
 * holds a set of group ids in memory and hands it back via
 * [RESULT_KEY]/[RESULT_GROUP_IDS]; [AddContactsStep2Fragment] applies it to
 * each newly-added contact only once "Add N people" actually creates them.
 * Creating a *new* group here still persists immediately via
 * [GroupEditDialogFragment] - a group is a standalone entity independent of
 * which contacts end up in it.
 */
class BulkGroupPickerDialogFragment : FmBottomSheet() {

    private var _binding: DialogAddContactToGroupBinding? = null
    private val binding get() = _binding!!

    private val preselectedGroupIds: Set<String> by lazy {
        requireArguments().getStringArrayList(ARG_SELECTED_GROUP_IDS)?.toSet().orEmpty()
    }
    private val checkboxesByGroup = mutableMapOf<ContactGroup, CheckBox>()

    // FRM-174 (DL-2): on the shared FmBottomSheet.
    override fun sheetTitle(): CharSequence = getString(R.string.label_groups_header)

    override fun primaryLabel(): CharSequence = getString(R.string.action_save)

    override fun onPrimaryClick() = applySelection()

    override fun onCreateSheetContent(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?): View {
        _binding = DialogAddContactToGroupBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.createGroupButton.setOnClickListener {
            GroupEditDialogFragment.newInstance().show(childFragmentManager, GROUP_EDIT_TAG)
        }
        childFragmentManager.setFragmentResultListener(GroupEditDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, _ ->
            loadGroups(currentlyChecked())
        }

        loadGroups(preselectedGroupIds)
    }

    private fun currentlyChecked(): Set<String> =
        checkboxesByGroup.filterValues { it.isChecked }.keys.map { it.id }.toSet()

    private fun loadGroups(checkedGroupIds: Set<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val allGroups = ServiceLocator.groupService.getGroups().sortedBy { it.name.lowercase() }

            binding.emptyText.visibility = if (allGroups.isEmpty()) View.VISIBLE else View.GONE
            setPrimaryVisible(allGroups.isNotEmpty())
            binding.candidateScroll.visibility = if (allGroups.isEmpty()) View.GONE else View.VISIBLE
            if (allGroups.isEmpty()) {
                binding.emptyText.text = getString(R.string.label_no_groups_to_add)
            }

            checkboxesByGroup.clear()
            binding.candidateContainer.removeAllViews()
            allGroups.forEach { group ->
                val checkbox = CheckBox(requireContext()).apply {
                    text = group.name
                    isChecked = group.id in checkedGroupIds
                    minHeight = (MIN_ROW_HEIGHT_DP * resources.displayMetrics.density).toInt()
                }
                checkboxesByGroup[group] = checkbox
                binding.candidateContainer.addView(checkbox)
            }
        }
    }

    private fun applySelection() {
        setFragmentResult(RESULT_KEY, bundleOf(RESULT_GROUP_IDS to ArrayList(currentlyChecked())))
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "bulk_group_picker_result"
        const val RESULT_GROUP_IDS = "result_group_ids"
        private const val ARG_SELECTED_GROUP_IDS = "arg_selected_group_ids"
        private const val MIN_ROW_HEIGHT_DP = 48
        private const val GROUP_EDIT_TAG = "bulk_group_picker_new_group"

        fun newInstance(selectedGroupIds: Set<String>): BulkGroupPickerDialogFragment =
            BulkGroupPickerDialogFragment().apply {
                arguments = bundleOf(ARG_SELECTED_GROUP_IDS to ArrayList(selectedGroupIds))
            }
    }
}
