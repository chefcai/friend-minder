package com.example.friendminder.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.data.contacts.ContactsLoader
import com.example.friendminder.databinding.FragmentLegacyDiagnosticsBinding
import com.example.friendminder.notifications.NotificationHelper
import com.example.friendminder.ui.addcontacts.AddContactsStep1Fragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Notification/diagnostic banners + test-notification preview (Designer
 * spec §3.3, from Phase 2). Formerly `HomeFragment`, the Phase 2 "setup
 * hub" - renamed and trimmed by Publisher-P3 (FRM-99) once Dashboard (its
 * only entry point) stopped being the app's root: the three navigation
 * shortcut rows this screen used to carry (Groups / Edit Friends /
 * Settings) are gone, since all three are reachable directly now (Groups
 * and Settings from the bottom nav - FRM-100; Edit Friends superseded by
 * Home's FAB - FRM-99 §1.7). What's left - the banners and the test-
 * notification preview - doesn't have an approved new home yet
 * (SCREENS-PHASE3.md's own capture notes queue it for the FRM-103 pass:
 * "Setup hub: notification-preview block ... needs a home (likely
 * Settings)"), so it's wired into SettingsFragment under a plain
 * "Notifications & diagnostics" row in the interim, rather than left
 * unreachable. See FRM-99's Jira comment.
 */
class LegacyDiagnosticsFragment : Fragment() {

    private var _binding: FragmentLegacyDiagnosticsBinding? = null
    private val binding get() = _binding!!

    /**
     * IDs backing the current missingContactsBanner text (GH #89) - kept
     * around so tapping the banner can open [MissingContactsDialogFragment]
     * without a second query, and re-populated on every [refresh].
     */
    private var missingContactIds: Set<String> = emptySet()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLegacyDiagnosticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.addFriendsButton.setOnClickListener { openFriendList() }
        // FRM-78: this used to enqueue the real SuggestionWorker, which posts a
        // notification indistinguishable from a genuine reminder (same contact
        // name, same SMS quick-action - tapping it created a real outreach log
        // entry for a contact the app never actually suggested) and is invisible
        // to any "how many reminders fired today" accounting. Post a dedicated,
        // clearly-labeled diagnostic notification instead - see
        // NotificationHelper.postTestNotification.
        binding.testNotificationButton.setOnClickListener {
            NotificationHelper.postTestNotification(requireContext())
        }
        binding.notificationsDisabledBanner.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
            )
        }
        // GH #89: used to just jump to Edit Friends, leaving the user to spot
        // and remove the stale entry themselves by comparing two lists. Now
        // opens a dialog that names the affected contact(s) and removes them
        // directly.
        binding.missingContactsBanner.setOnClickListener {
            if (missingContactIds.isNotEmpty()) {
                MissingContactsDialogFragment.newInstance(missingContactIds)
                    .show(childFragmentManager, MISSING_CONTACTS_DIALOG_TAG)
            }
        }
        childFragmentManager.setFragmentResultListener(
            MissingContactsDialogFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ -> refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // FRM-99's kdoc for this class already notes "Edit Friends superseded
    // by Home's FAB"; this banner's own "Add friends" button (shown only in
    // its empty state) is the other surviving entry point into the same
    // flow, so FRM-102 repoints it here too rather than leaving it aimed at
    // the now-deleted FriendListFragment.
    private fun openFriendList() {
        parentFragmentManager.commit {
            replace(R.id.nav_host_container, AddContactsStep1Fragment.newInstance())
            addToBackStack(AddContactsStep1Fragment.BACK_STACK_NAME)
        }
    }

    private fun refresh() {
        binding.notificationsDisabledBanner.visibility =
            if (NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()) View.GONE else View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val friendListRepo = ServiceLocator.friendListRepository
            val friends = friendListRepo.getFriendList()

            val isEmpty = friends.isEmpty()
            binding.emptyStateGroup.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.previewGroup.visibility = if (isEmpty) View.GONE else View.VISIBLE

            if (!isEmpty) {
                val cooldownRepo = ServiceLocator.cooldownRepository
                val leastRecentlySuggested = friends.minByOrNull { cooldownRepo.getLastSuggestion(it.id) ?: 0L }
                binding.previewContactName.text =
                    leastRecentlySuggested?.name ?: getString(R.string.placeholder_contact_name)
            }

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val existingIds = ContactsLoader.loadExistingContactIds(requireContext())
                val missing = friends.filter { it.id !in existingIds }
                missingContactIds = missing.map { it.id }.toSet()
                if (missing.isNotEmpty()) {
                    binding.missingContactsBanner.text =
                        getString(R.string.format_banner_missing_contacts, missing.size)
                    binding.missingContactsBanner.visibility = View.VISIBLE
                } else {
                    binding.missingContactsBanner.visibility = View.GONE
                }
            } else {
                missingContactIds = emptySet()
                binding.missingContactsBanner.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MISSING_CONTACTS_DIALOG_TAG = "missing_contacts_dialog"

        fun newInstance(): LegacyDiagnosticsFragment = LegacyDiagnosticsFragment()
    }
}
