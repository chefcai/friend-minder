package com.example.friendminder.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact
import com.example.friendminder.databinding.DialogMissingContactsBinding
import com.example.friendminder.databinding.ItemGroupMemberBinding
import com.example.friendminder.ui.common.AvatarBinder
import com.example.friendminder.utils.ServiceLocator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Names the friend-list entries whose device contact no longer resolves,
 * and lets the user remove each one directly (GH #89). Previously the
 * banner this dialog is opened from only ever said "N contacts ... no
 * longer exist" with no way to tell which ones or do anything about it
 * short of manually diffing the friend list against the Contacts app.
 *
 * Only [Contact.id]s are passed in (a plain String array survives a Bundle
 * without [Contact] needing to implement Parcelable, which nothing else in
 * this codebase does either) - the fragment re-reads the friend list itself
 * to resolve names, same source [HomeFragment.refresh] used to compute the
 * missing count in the first place. Re-reading also means a contact that
 * reappears (e.g. undo-delete on the device) between the banner rendering
 * and the dialog opening is simply not in the row list, instead of showing
 * a stale entry the removal button would silently no-op on.
 *
 * Reuses item_group_member.xml's name+remove-button row shape (already
 * built for exactly this "row with one destructive action" case) rather
 * than a new layout, and AvatarBinder's initials fallback for the avatar -
 * a deleted contact's [Contact.photoUri], if it had one, will simply fail
 * to resolve in [com.example.friendminder.data.contacts.ContactPhotoLoader.load]
 * and leave the initials shown, so no special-casing is needed there.
 */
class MissingContactsDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogMissingContactsBinding? = null
    private val binding get() = _binding!!

    private val missingContactIds: Set<String> by lazy {
        requireArguments().getStringArrayList(ARG_MISSING_CONTACT_IDS)?.toSet() ?: emptySet()
    }
    private var missingContacts: List<Contact> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMissingContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.closeButton.setOnClickListener { dismiss() }
        binding.closeDialogButton.setOnClickListener { dismiss() }
        loadAndRenderRows()
    }

    private fun loadAndRenderRows() {
        viewLifecycleOwner.lifecycleScope.launch {
            missingContacts = ServiceLocator.friendListRepository.getFriendList()
                .filter { it.id in missingContactIds }
            if (missingContacts.isEmpty()) {
                dismiss()
            } else {
                renderRows()
            }
        }
    }

    /**
     * Rebuilds the row list from scratch after every removal instead of
     * diffing, same as [com.example.friendminder.ui.contactdetail
     * .EditContactGroupsDialogFragment]'s candidate list - this list is
     * expected to be small (bounded by how many friends have been added at
     * all), so a RecyclerView/ListAdapter would be more machinery than the
     * problem calls for.
     */
    private fun renderRows() {
        binding.missingContactContainer.removeAllViews()
        missingContacts.forEach { contact ->
            val rowBinding = ItemGroupMemberBinding.inflate(
                LayoutInflater.from(requireContext()),
                binding.missingContactContainer,
                false
            )
            rowBinding.memberName.text = contact.name
            rowBinding.root.isClickable = false
            rowBinding.root.isFocusable = false
            AvatarBinder.bind(
                rowBinding.contactPhoto,
                rowBinding.contactInitial,
                contact,
                ServiceLocator.contactPhotoLoader,
                viewLifecycleOwner.lifecycleScope
            )
            rowBinding.removeButton.contentDescription =
                getString(R.string.format_action_remove_missing_contact, contact.name)
            rowBinding.removeButton.setOnClickListener { removeContact(contact) }
            binding.missingContactContainer.addView(rowBinding.root)
        }
    }

    private fun removeContact(contact: Contact) {
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.friendListRepository.removeFriend(contact.id)
            missingContacts = missingContacts.filterNot { it.id == contact.id }
            setFragmentResult(RESULT_KEY, bundleOf())
            if (missingContacts.isEmpty()) {
                dismiss()
            } else {
                renderRows()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "missing_contacts_result"
        private const val ARG_MISSING_CONTACT_IDS = "arg_missing_contact_ids"

        fun newInstance(missingContactIds: Collection<String>): MissingContactsDialogFragment =
            MissingContactsDialogFragment().apply {
                arguments = bundleOf(ARG_MISSING_CONTACT_IDS to ArrayList(missingContactIds))
            }
    }
}
