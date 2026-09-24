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
import com.example.friendminder.data.models.ContactGroup
import com.example.friendminder.databinding.DialogAddContactToGroupBinding
import com.example.friendminder.databinding.ItemGroupAddMemberCandidateBinding
import com.example.friendminder.ui.common.FmBottomSheet
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Multi-select picker of the user's Friend List contacts not already in a
 * group (FRM-56, GroupDetailFragment's "+ Add contact to group"). Renders
 * a row per candidate straight into a container `LinearLayout` — the same
 * bounded-list-as-inflated-rows approach used by the other dialogs sharing
 * this layout — rather than a full RecyclerView adapter for what's
 * effectively a one-shot form.
 *
 * Picks up FRM-102's SCREENS-PHASE3.md §8.1 chrome fix for free, since it
 * shares `dialog_add_contact_to_group.xml` with
 * [com.example.friendminder.ui.contactdetail.EditContactGroupsDialogFragment]
 * (that class's rebuild target this pass) - the old ✕/Cancel/scrim
 * three-way-out was the same bug in both. The rest of Groups/Group Detail
 * is unaffected and stays FRM-103 scope.
 *
 * FRM-127 (GH #95 / SCREENS-PHASE3 §5.3): each row now also surfaces the
 * candidate's existing membership in *other* groups, so whoever is adding
 * them can see it before deciding — groups stay many-to-many (Option 1 from
 * GH #95's decision thread), this is a visibility improvement only. Only
 * this dialog needs it: EditContactGroupsDialogFragment (the other caller
 * of this shared layout) toggles one contact's own groups, where Contact
 * Detail's GROUPS chips already show the same information above it.
 */
class AddContactToGroupDialogFragment : FmBottomSheet() {

    private var _binding: DialogAddContactToGroupBinding? = null
    private val binding get() = _binding!!

    private val groupId: String by lazy { requireArguments().getString(ARG_GROUP_ID)!! }
    private val checkboxesByContact = mutableMapOf<Contact, CheckBox>()

    // FRM-174 (DL-2): on the shared FmBottomSheet - title and the single
    // full-width primary come from the base.
    override fun sheetTitle(): CharSequence = getString(R.string.action_add_contact_to_group)

    override fun primaryLabel(): CharSequence = getString(R.string.action_save)

    override fun onPrimaryClick() = addSelected()

    override fun onCreateSheetContent(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?): View {
        _binding = DialogAddContactToGroupBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            setPrimaryVisible(candidates.isNotEmpty())
            binding.candidateScroll.visibility = if (candidates.isEmpty()) View.GONE else View.VISIBLE

            candidates.forEach { contact ->
                val otherGroups = ServiceLocator.groupService.getGroupsForContact(contact.id)
                    .filterNot { it.id == groupId }
                    .sortedBy { it.name.lowercase() }
                addCandidateRow(contact, otherGroups)
            }
        }
    }

    private fun addCandidateRow(contact: Contact, otherGroups: List<ContactGroup>) {
        val rowBinding = ItemGroupAddMemberCandidateBinding.inflate(
            layoutInflater,
            binding.candidateContainer,
            false
        )
        rowBinding.candidateName.text = contact.name

        val membershipLabel = membershipLabel(otherGroups)
        if (membershipLabel != null) {
            rowBinding.candidateMembership.text = membershipLabel
            rowBinding.candidateMembership.visibility = View.VISIBLE
        }

        rowBinding.root.setOnClickListener {
            rowBinding.candidateCheckbox.isChecked = !rowBinding.candidateCheckbox.isChecked
        }

        checkboxesByContact[contact] = rowBinding.candidateCheckbox
        binding.candidateContainer.addView(rowBinding.root)
    }

    /**
     * SCREENS-PHASE3 §5.3's three-state format: omitted for no other
     * groups, "In X" for one, "In X and Y" for two, "In X +N" for three or
     * more (N = total - 1) rather than listing every name and breaking the
     * row's single-line secondary text.
     */
    private fun membershipLabel(otherGroups: List<ContactGroup>): String? = when (otherGroups.size) {
        0 -> null
        1 -> getString(R.string.label_already_in_one, otherGroups[0].name)
        2 -> getString(R.string.label_already_in_two, otherGroups[0].name, otherGroups[1].name)
        else -> getString(R.string.label_already_in_many, otherGroups[0].name, otherGroups.size - 1)
    }

    private fun addSelected() {
        val selected = checkboxesByContact.filterValues { it.isChecked }.keys
        sheet.sheetPrimaryButton.isEnabled = false
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

        fun newInstance(groupId: String): AddContactToGroupDialogFragment =
            AddContactToGroupDialogFragment().apply {
                arguments = bundleOf(ARG_GROUP_ID to groupId)
            }
    }
}
