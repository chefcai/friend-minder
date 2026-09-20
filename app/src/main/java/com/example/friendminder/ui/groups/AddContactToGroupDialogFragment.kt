package com.example.friendminder.ui.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact
import com.example.friendminder.databinding.DialogAddContactToGroupBinding
import com.example.friendminder.utils.ServiceLocator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Multi-select picker of the user's Friend List contacts not already in a
 * group (FRM-56, GroupDetailFragment's "+ Add contact to group"). Renders
 * a checkbox per candidate straight into a container `LinearLayout` — the
 * same bounded-list-as-inflated-rows approach as Dashboard's streak/
 * neglected/upcoming sections, since a user's Friend List is small — rather
 * than a full RecyclerView adapter for what's effectively a one-shot form.
 */
class AddContactToGroupDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogAddContactToGroupBinding? = null
    private val binding get() = _binding!!

    private val groupId: String by lazy { requireArguments().getString(ARG_GROUP_ID)!! }
    private val checkboxesByContact = mutableMapOf<Contact, CheckBox>()

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
        binding.addButton.setOnClickListener { addSelected() }

        // GH #72: this dialog's layout is shared with
        // EditContactGroupsDialogFragment (which lets a contact be added to a
        // *new* group, so "+ New Group" belongs there), but this fragment is
        // reached from a specific group's detail screen to add existing
        // friends to *that* group. "+ New Group" here had no click listener
        // at all - tapping it silently did nothing, which is what got
        // reported as groups not saving. Creating an unrelated group
        // mid-"add contacts to this group" flow isn't meaningful UX, so
        // rather than invent a "create and immediately add" behavior, hide
        // the button in this context instead of leaving it dead.
        binding.createGroupButton.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val allFriends = ServiceLocator.friendListRepository.getFriendList()
            val existingMemberIds = ServiceLocator.contactGroupRepository.getContactIdsInGroup(groupId)
            val candidates = allFriends.filterNot { it.id in existingMemberIds }.sortedBy { it.name.lowercase() }

            binding.emptyText.visibility = if (candidates.isEmpty()) View.VISIBLE else View.GONE
            binding.addButton.visibility = if (candidates.isEmpty()) View.GONE else View.VISIBLE

            candidates.forEach { contact ->
                val checkbox = CheckBox(requireContext()).apply {
                    text = contact.name
                    minHeight = (MIN_ROW_HEIGHT_DP * resources.displayMetrics.density).toInt()
                }
                checkboxesByContact[contact] = checkbox
                binding.candidateContainer.addView(checkbox)
            }
        }
    }

    private fun addSelected() {
        val selected = checkboxesByContact.filterValues { it.isChecked }.keys
        binding.addButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            selected.forEach { contact ->
                ServiceLocator.groupService.assignContactToGroup(contact.id, groupId)
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
        const val RESULT_KEY = "add_contact_to_group_result"
        private const val ARG_GROUP_ID = "arg_group_id"
        private const val MIN_ROW_HEIGHT_DP = 48

        fun newInstance(groupId: String): AddContactToGroupDialogFragment =
            AddContactToGroupDialogFragment().apply {
                arguments = bundleOf(ARG_GROUP_ID to groupId)
            }
    }
}
