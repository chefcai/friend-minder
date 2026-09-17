package com.example.friendminder.ui.friendlist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.friendminder.R
import com.example.friendminder.data.contacts.ContactsLoader
import com.example.friendminder.databinding.FragmentFriendListBinding
import com.example.friendminder.ui.settings.SettingsFragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Contact picker (Designer spec §3.1, §4.3, §4.4, §4.5). Two modes:
 *  - onboarding: first-launch flow; forward-only; saving advances to
 *    SettingsFragment (system back exits the app — it's the FragmentManager
 *    root, so there's no back-stack entry to pop).
 *  - edit: reachable from HomeFragment; shows a toolbar back arrow; saving
 *    (or pressing back) returns to HomeFragment.
 */
class FriendListFragment : Fragment() {

    private var _binding: FragmentFriendListBinding? = null
    private val binding get() = _binding!!

    private val isOnboarding: Boolean by lazy { requireArguments().getBoolean(ARG_ONBOARDING) }

    private lateinit var adapter: ContactAdapter

    /** Full candidate list currently shown: device contacts + any missing saved friends. */
    private var allItemsSource: List<com.example.friendminder.data.models.Contact> = emptyList()
    private val selectedIds = linkedSetOf<String>()
    private var missingFriendIds: Set<String> = emptySet()
    private var initialFriendIds: Set<String> = emptySet()
    private var searchQuery: String = ""

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadContacts() else showPermissionDenied()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.title = getString(
            if (isOnboarding) R.string.title_add_friends else R.string.title_edit_friends
        )
        if (!isOnboarding) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            binding.toolbar.setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        adapter = ContactAdapter { item -> onRowClicked(item) }
        binding.contactRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.contactRecyclerView.adapter = adapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                renderList()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.openSettingsButton.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
            )
        }

        binding.saveFriendsButton.setOnClickListener { saveAndContinue() }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadContacts()
        } else {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    override fun onResume() {
        super.onResume()
        // User may have just granted the permission from system Settings (§4.3).
        if (binding.permissionDeniedGroup.visibility == View.VISIBLE &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadContacts()
        }
    }

    private fun showPermissionDenied() {
        binding.permissionDeniedGroup.visibility = View.VISIBLE
        binding.contentGroup.visibility = View.GONE
        binding.selectionCountText.visibility = View.GONE
        binding.saveFriendsButton.visibility = View.GONE
    }

    private fun loadContacts() {
        binding.permissionDeniedGroup.visibility = View.GONE
        binding.contentGroup.visibility = View.VISIBLE
        binding.selectionCountText.visibility = View.VISIBLE
        binding.saveFriendsButton.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val friendListRepo = ServiceLocator.friendListRepository
            val savedFriends = friendListRepo.getFriendList()
            initialFriendIds = savedFriends.map { it.id }.toSet()
            selectedIds.clear()
            selectedIds += initialFriendIds

            val device = ContactsLoader.loadContactsWithPhoneNumbers(requireContext())
            val deviceIds = device.map { it.id }.toSet()
            val missing = savedFriends.filterNot { it.id in deviceIds }
            missingFriendIds = missing.map { it.id }.toSet()

            allItemsSource = device + missing
            renderList()
        }
    }

    private fun renderList() {
        val filtered = allItemsSource.filter {
            searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
        }
        adapter.submitList(
            filtered.map { contact ->
                PickerItem(
                    contact = contact,
                    isSelected = contact.id in selectedIds,
                    isMissing = contact.id in missingFriendIds
                )
            }
        )

        val noneAvailable = allItemsSource.isEmpty()
        binding.emptyStateText.visibility = if (noneAvailable) View.VISIBLE else View.GONE
        binding.contactRecyclerView.visibility = if (noneAvailable) View.GONE else View.VISIBLE

        updateSelectionCount()
    }

    private fun onRowClicked(item: PickerItem) {
        if (item.isMissing) {
            // "still shown ... so the user can see and remove it" (§4.4) — no
            // checkbox to toggle, so tapping the row removes it outright.
            missingFriendIds = missingFriendIds - item.contact.id
            selectedIds -= item.contact.id
            allItemsSource = allItemsSource.filterNot { it.id == item.contact.id }
            renderList()
            return
        }
        if (item.contact.id in selectedIds) selectedIds -= item.contact.id else selectedIds += item.contact.id
        renderList()
    }

    private fun updateSelectionCount() {
        val count = selectedIds.count { it !in missingFriendIds }
        binding.selectionCountText.text = if (count == 0) {
            getString(R.string.label_no_contacts_selected)
        } else {
            getString(R.string.format_selected_count, count)
        }
        binding.saveFriendsButton.isEnabled = count > 0
    }

    private fun saveAndContinue() {
        binding.saveFriendsButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val friendListRepo = ServiceLocator.friendListRepository
            val finalIds = selectedIds - missingFriendIds
            val toRemove = initialFriendIds - finalIds
            val toAdd = finalIds - initialFriendIds

            toRemove.forEach { friendListRepo.removeFriend(it) }
            toAdd.forEach { id ->
                allItemsSource.firstOrNull { it.id == id }?.let { friendListRepo.addFriend(it) }
            }

            if (isOnboarding) {
                // Collapse onboarding's own back-stack entry (if any) before
                // installing Settings as the next onboarding step.
                parentFragmentManager.commit {
                    replace(R.id.nav_host_container, SettingsFragment.newInstance(isOnboarding = true))
                    addToBackStack(null)
                }
            } else {
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ONBOARDING = "arg_onboarding"

        fun newInstance(isOnboarding: Boolean): FriendListFragment = FriendListFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_ONBOARDING, isOnboarding) }
        }
    }
}
