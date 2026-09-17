package com.example.friendminder.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.friendminder.R
import com.example.friendminder.data.contacts.ContactsLoader
import com.example.friendminder.databinding.FragmentHomeBinding
import com.example.friendminder.ui.friendlist.FriendListFragment
import com.example.friendminder.ui.dashboard.DashboardFragment
import com.example.friendminder.ui.groups.GroupsFragment
import com.example.friendminder.ui.settings.AdvancedSettingsFragment
import com.example.friendminder.ui.settings.SettingsFragment
import com.example.friendminder.utils.ServiceLocator
import com.example.friendminder.work.SuggestionWorker
import kotlinx.coroutines.launch

/**
 * Landing screen for a returning user (Designer spec §3.3): notification
 * preview + test button, edit-friends/settings shortcuts, and the two status
 * banners (§4.7, §4.4). The toolbar's gear icon (chefcai/friend-minder#38)
 * opens AdvancedSettingsFragment, kept separate from the regular Settings
 * screen since it's the one place SEND_SMS gets requested from.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setOnMenuItemClickListener { item -> onMenuItemClicked(item) }

        binding.addFriendsButton.setOnClickListener { openFriendList() }
        binding.editFriendsRow.setOnClickListener { openFriendList() }
        binding.dashboardRow.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.nav_host_container, DashboardFragment.newInstance())
                addToBackStack(null)
            }
        }
        binding.groupsRow.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.nav_host_container, GroupsFragment.newInstance())
                addToBackStack(null)
            }
        }
        binding.settingsRow.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.nav_host_container, SettingsFragment.newInstance(isOnboarding = false))
                addToBackStack(null)
            }
        }
        binding.testNotificationButton.setOnClickListener {
            WorkManager.getInstance(requireContext())
                .enqueue(OneTimeWorkRequestBuilder<SuggestionWorker>().build())
        }
        binding.notificationsDisabledBanner.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
            )
        }
        binding.missingContactsBanner.setOnClickListener { openFriendList() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun onMenuItemClicked(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_advanced_settings) return false
        parentFragmentManager.commit {
            replace(R.id.nav_host_container, AdvancedSettingsFragment.newInstance())
            addToBackStack(null)
        }
        return true
    }

    private fun openFriendList() {
        parentFragmentManager.commit {
            replace(R.id.nav_host_container, FriendListFragment.newInstance(isOnboarding = false))
            addToBackStack(null)
        }
    }

    private fun refresh() {
        binding.notificationsDisabledBanner.visibility =
            if (NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) View.GONE else View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val friendListRepo = ServiceLocator.friendListRepository
            val cooldownRepo = ServiceLocator.cooldownRepository
            val friends = friendListRepo.getFriendList()

            val isEmpty = friends.isEmpty()
            binding.emptyStateGroup.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.previewGroup.visibility = if (isEmpty) View.GONE else View.VISIBLE

            if (!isEmpty) {
                val leastRecentlySuggested = friends.minByOrNull { cooldownRepo.getLastSuggestion(it.id) ?: 0L }
                binding.previewContactName.text =
                    leastRecentlySuggested?.name ?: getString(R.string.placeholder_contact_name)
            }

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val existingIds = ContactsLoader.loadExistingContactIds(requireContext())
                val missingCount = friends.count { it.id !in existingIds }
                if (missingCount > 0) {
                    binding.missingContactsBanner.text =
                        getString(R.string.format_banner_missing_contacts, missingCount)
                    binding.missingContactsBanner.visibility = View.VISIBLE
                } else {
                    binding.missingContactsBanner.visibility = View.GONE
                }
            } else {
                binding.missingContactsBanner.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
