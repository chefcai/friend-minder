package com.example.friendminder.ui.groups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact
import com.example.friendminder.databinding.FragmentGroupDetailBinding
import com.example.friendminder.ui.common.EdgeToEdgeHeader
import com.example.friendminder.ui.contactdetail.ContactDetailFragment
import com.example.friendminder.utils.ServiceLocator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Members of one group (FRM-56, SCREENS-PHASE2.md §3 "Group Detail"). */
class GroupDetailFragment : Fragment() {

    private var _binding: FragmentGroupDetailBinding? = null
    private val binding get() = _binding!!

    private val groupId: String by lazy { requireArguments().getString(ARG_GROUP_ID)!! }
    private lateinit var adapter: GroupMemberAdapter

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

        binding.backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.headerActionButton.setOnClickListener {
            GroupEditDialogFragment.newInstance(groupId).show(childFragmentManager, "group_edit")
        }
        EdgeToEdgeHeader.applyHeaderInsets(binding.headerContainer, binding.statusBarSpacer)

        adapter = GroupMemberAdapter(
            photoLoader = ServiceLocator.contactPhotoLoader,
            scope = viewLifecycleOwner.lifecycleScope,
            onRowClicked = { contact -> openContactDetail(contact) },
            onRemoveClicked = { contact -> removeMember(contact) }
        )
        binding.memberRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.memberRecyclerView.adapter = adapter

        binding.addContactButton.setOnClickListener {
            AddContactToGroupDialogFragment.newInstance(groupId).show(childFragmentManager, "add_to_group")
        }

        childFragmentManager.setFragmentResultListener(AddContactToGroupDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, _ ->
            refresh()
        }
        childFragmentManager.setFragmentResultListener(GroupEditDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, _ ->
            refresh()
        }
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

    private fun removeMember(contact: Contact) {
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.groupService.removeContactFromGroup(contact.id, groupId)
            refresh()
            Snackbar.make(binding.root, R.string.toast_removed_from_group, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        ServiceLocator.groupService.assignContactToGroup(contact.id, groupId)
                        refresh()
                    }
                }
                .show()
        }
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            val group = ServiceLocator.contactGroupRepository.getGroup(groupId)
            if (group == null) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
                return@launch
            }
            binding.headerTitle.text = group.name

            val members = ServiceLocator.groupService.getContactsInGroup(groupId).sortedBy { it.name.lowercase() }
            binding.emptyStateText.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
            binding.memberRecyclerView.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
            adapter.submitList(members)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_GROUP_ID = "arg_group_id"

        fun newInstance(groupId: String): GroupDetailFragment = GroupDetailFragment().apply {
            arguments = Bundle().apply { putString(ARG_GROUP_ID, groupId) }
        }
    }
}
