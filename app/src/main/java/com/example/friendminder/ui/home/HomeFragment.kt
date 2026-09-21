package com.example.friendminder.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.friendminder.R
import com.example.friendminder.databinding.FragmentHomeBinding
import com.example.friendminder.ui.contactdetail.ContactDetailFragment
import com.example.friendminder.ui.friendlist.FriendListFragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/**
 * Home (root) - FRM-99, SCREENS-PHASE3.md §1. Replaces DashboardFragment as
 * the app's root screen and absorbs the contact-list role of the old
 * "setup hub" HomeFragment (renamed to [LegacyDiagnosticsFragment]).
 *
 * No separate ViewModel - matches every other screen in this app (see
 * the old DashboardFragment's kdoc for the reasoning); state lives in the
 * Fragment.
 *
 * Two things this screen deliberately does NOT build, both flagged on
 * FRM-99's Jira ticket rather than guessed at:
 *  - The designed empty state (FRM-107: 96dp glyph, headline, body copy,
 *    filled button) hasn't been signed off, so zero-contacts shows a plain
 *    interim placeholder instead (see fragment_home.xml's emptyStateGroup).
 *  - The FAB's approved destination is the rebuilt single-flow add-contact
 *    process (FRM-102), which isn't implemented yet. It opens the existing
 *    Edit Friends picker ([FriendListFragment]) in the meantime.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HomeContactAdapter

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

        adapter = HomeContactAdapter(
            photoLoader = ServiceLocator.contactPhotoLoader,
            scope = viewLifecycleOwner.lifecycleScope
        ) { contact -> openContactDetail(contact.id) }
        binding.contactRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.contactRecyclerView.adapter = adapter

        binding.addContactFab.setOnClickListener { openAddContact() }
        binding.emptyStateAddButton.setOnClickListener { openAddContact() }

        applyHeaderInsets()
    }

    override fun onResume() {
        super.onResume()
        applyEdgeToEdgeHeader()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        restoreStandardStatusBar()
    }

    /**
     * DESIGN-SYSTEM-PHASE3.md §5: "one #1F817D fill spanning the status bar
     * and the 72dp title bar as a single view ... edge-to-edge + top inset
     * as padding (not just android:statusBarColor), because Android 15
     * forces edge-to-edge at targetSdk 35 and the inset approach survives
     * that bump." Scoped to only run while Home is the visible fragment
     * (applied in onResume, reverted in onPause) rather than flipped once
     * at the Activity level, since Groups/Settings/Contact Detail haven't
     * been re-skinned yet (FRM-103) and still expect the platform's normal,
     * non-edge-to-edge status bar behaviour.
     */
    private fun applyEdgeToEdgeHeader() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    private fun restoreStandardStatusBar() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        val typedValue = android.util.TypedValue()
        if (requireContext().theme.resolveAttribute(android.R.attr.statusBarColor, typedValue, true)) {
            window.statusBarColor = typedValue.data
        }
    }

    /** Feeds the real status-bar inset into statusBarSpacer, so the teal fill spans it with no seam (§5). */
    private fun applyHeaderInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { _, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.statusBarSpacer.layoutParams = binding.statusBarSpacer.layoutParams.apply {
                height = topInset
            }
            binding.statusBarSpacer.requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(binding.headerContainer)
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            val friends = ServiceLocator.friendListRepository.getFriendList()

            if (friends.isEmpty()) {
                binding.contactRecyclerView.visibility = View.GONE
                binding.emptyStateGroup.visibility = View.VISIBLE
                binding.addContactFab.visibility = View.GONE // §1.7: FAB hidden in the empty state
                return@launch
            }

            binding.emptyStateGroup.visibility = View.GONE
            binding.contactRecyclerView.visibility = View.VISIBLE
            binding.addContactFab.visibility = View.VISIBLE

            val statisticsService = ServiceLocator.statisticsService
            val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }

            val rows = friends
                .sortedWith(compareBy(collator) { it.name })
                .map { contact ->
                    val stats = statisticsService.getStatistics(contact.id)
                    HomeContactRow(
                        contact = contact,
                        streak = stats.streak,
                        lastTouchText = HomeLastTouchFormatter.format(requireContext(), stats.lastContacted)
                    )
                }

            adapter.submitList(rows)
        }
    }

    private fun openAddContact() {
        // FRM-102 (rebuilt single-flow add-contact) isn't approved/built yet -
        // see this class's kdoc. Closest working equivalent in the interim.
        parentFragmentManager.commit {
            replace(R.id.nav_host_container, FriendListFragment.newInstance(isOnboarding = false))
            addToBackStack(null)
        }
    }

    private fun openContactDetail(contactId: String) {
        parentFragmentManager.commit {
            replace(R.id.nav_host_container, ContactDetailFragment.newInstance(contactId))
            addToBackStack(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): HomeFragment = HomeFragment()
    }
}
