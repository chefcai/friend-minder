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
import com.example.friendminder.data.models.ContactGroup
import com.example.friendminder.databinding.FragmentGroupsBinding
import com.example.friendminder.ui.common.EdgeToEdgeHeader
import com.example.friendminder.utils.ServiceLocator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * FRM-56: the Groups tab (PRD §6.1; SCREENS-PHASE2.md §3). No separate
 * GroupsViewModel — see [com.example.friendminder.ui.home.HomeFragment]'s
 * kdoc for why every screen in this app keeps state in the Fragment
 * itself, matching the rest of the app (formerly explained on the now-
 * deleted DashboardFragment's kdoc, FRM-101).
 */
class GroupsFragment : Fragment() {

    private var _binding: FragmentGroupsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: GroupAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.addGroupFab.setOnClickListener { openEditDialog(null) }
        EdgeToEdgeHeader.applyHeaderInsets(binding.headerContainer, binding.statusBarSpacer)

        adapter = GroupAdapter(
            onClick = { group -> openGroupDetail(group) },
            onLongClick = { group -> showGroupOptions(group) }
        )
        binding.groupRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.groupRecyclerView.adapter = adapter

        binding.createFirstGroupButton.setOnClickListener { openEditDialog(null) }

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

    private fun openEditDialog(groupId: String?) {
        GroupEditDialogFragment.newInstance(groupId).show(childFragmentManager, "group_edit")
    }

    private fun openGroupDetail(group: ContactGroup) {
        parentFragmentManager.commit {
            replace(R.id.nav_host_container, GroupDetailFragment.newInstance(group.id))
            addToBackStack(null)
        }
    }

    private fun showGroupOptions(group: ContactGroup) {
        val options = arrayOf(getString(R.string.title_edit_group), getString(R.string.action_delete_group))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(group.name)
            .setItems(options) { _, which ->
                if (which == 0) openEditDialog(group.id) else confirmDelete(group)
            }
            .show()
    }

    private fun confirmDelete(group: ContactGroup) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_delete_group)
            .setMessage(getString(R.string.format_confirm_delete_group, group.name))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    ServiceLocator.groupService.deleteGroup(group.id)
                    refresh()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            val groups = ServiceLocator.groupService.getGroups()
            val rows = groups.map { group ->
                GroupRow(group, ServiceLocator.groupService.getMemberCount(group.id))
            }.sortedBy { it.group.name.lowercase() }

            binding.emptyStateGroup.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            binding.groupRecyclerView.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
            // FRM-118: FAB is that other state's own action (createFirstGroupButton) -
            // hidden here the same way addContactFab is on Group Detail's empty state.
            binding.addGroupFab.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
            adapter.submitList(rows)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): GroupsFragment = GroupsFragment()
    }
}
