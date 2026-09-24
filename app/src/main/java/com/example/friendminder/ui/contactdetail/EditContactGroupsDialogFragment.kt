package com.example.friendminder.ui.contactdetail

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
 * Toggle which groups one contact belongs to (PRD §9.3 step 3;
 * SCREENS-PHASE2.md §4 "Groups" chip row's "+ Add"; also the destination
 * of FRM-102's add-contact flow Step 2 "Groups" row, SCREENS-PHASE3.md
 * §9.4). Reuses `dialog_add_contact_to_group.xml` — same shape (title, a
 * checkbox per candidate, one filled Save) just with groups as the
 * candidates instead of contacts, so it doesn't need its own near-identical
 * layout. Rebuilt to SCREENS-PHASE3.md §8.1's dialog chrome by FRM-102 -
 * see [applyPhaseThreeSheetChrome]'s kdoc for why this stays multi-select
 * rather than adopting §8.4's single-value tap-to-dismiss shape.
 */
class EditContactGroupsDialogFragment : FmBottomSheet() {

    private var _binding: DialogAddContactToGroupBinding? = null
    private val binding get() = _binding!!

    private val contactId: String by lazy { requireArguments().getString(ARG_CONTACT_ID)!! }
    private val checkboxesByGroup = mutableMapOf<ContactGroup, CheckBox>()

    // FRM-174 (DL-2): on the shared FmBottomSheet, with its own title
    // (the shared layout used to hard-code "Add contact to group" here too).
    override fun sheetTitle(): CharSequence = getString(R.string.title_contact_groups_sheet)

    override fun primaryLabel(): CharSequence = getString(R.string.action_save)

    override fun onPrimaryClick() = applyChanges()

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
            loadGroups()
        }

        loadGroups()
    }

    private fun loadGroups() {
        viewLifecycleOwner.lifecycleScope.launch {
            val allGroups = ServiceLocator.groupService.getGroups().sortedBy { it.name.lowercase() }
            val currentGroupIds = ServiceLocator.groupService.getGroupsForContact(contactId).map { it.id }.toSet()

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
                    isChecked = group.id in currentGroupIds
                    minHeight = (MIN_ROW_HEIGHT_DP * resources.displayMetrics.density).toInt()
                }
                checkboxesByGroup[group] = checkbox
                binding.candidateContainer.addView(checkbox)
            }
        }
    }

    private fun applyChanges() {
        sheet.sheetPrimaryButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            checkboxesByGroup.forEach { (group, checkbox) ->
                if (checkbox.isChecked) {
                    ServiceLocator.groupService.assignContactToGroup(contactId, group.id)
                } else {
                    ServiceLocator.groupService.removeContactFromGroup(contactId, group.id)
                }
            }
            setFragmentResult(RESULT_KEY, bundleOf())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "edit_contact_groups_result"
        private const val ARG_CONTACT_ID = "arg_contact_id"
        private const val MIN_ROW_HEIGHT_DP = 48
        private const val GROUP_EDIT_TAG = "edit_contact_groups_new_group"

        fun newInstance(contactId: String): EditContactGroupsDialogFragment =
            EditContactGroupsDialogFragment().apply {
                arguments = bundleOf(ARG_CONTACT_ID to contactId)
            }
    }
}
