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
import com.example.friendminder.utils.ServiceLocator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Toggle which groups one contact belongs to (PRD §9.3 step 3;
 * SCREENS-PHASE2.md §4 "Groups" chip row's "+ Add"). Reuses
 * `dialog_add_contact_to_group.xml` — same shape (title + close, a checkbox
 * per candidate, Cancel/Save) just with groups as the candidates instead of
 * contacts, so it doesn't need its own near-identical layout.
 */
class EditContactGroupsDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogAddContactToGroupBinding? = null
    private val binding get() = _binding!!

    private val contactId: String by lazy { requireArguments().getString(ARG_CONTACT_ID)!! }
    private val checkboxesByGroup = mutableMapOf<ContactGroup, CheckBox>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddContactToGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.closeButton.setOnClickListener { dismiss() }
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.addButton.text = getString(R.string.action_save)
        binding.addButton.setOnClickListener { applyChanges() }

        viewLifecycleOwner.lifecycleScope.launch {
            val allGroups = ServiceLocator.groupService.getGroups().sortedBy { it.name.lowercase() }
            val currentGroupIds = ServiceLocator.groupService.getGroupsForContact(contactId).map { it.id }.toSet()

            binding.emptyText.visibility = if (allGroups.isEmpty()) View.VISIBLE else View.GONE
            binding.addButton.visibility = if (allGroups.isEmpty()) View.GONE else View.VISIBLE
            if (allGroups.isEmpty()) {
                binding.emptyText.text = getString(R.string.label_no_groups_to_add)
            }

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
        binding.addButton.isEnabled = false
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

        fun newInstance(contactId: String): EditContactGroupsDialogFragment =
            EditContactGroupsDialogFragment().apply {
                arguments = bundleOf(ARG_CONTACT_ID to contactId)
            }
    }
}
