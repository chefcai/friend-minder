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
import com.example.friendminder.ui.addcontacts.AddContactsStep1Fragment
import com.example.friendminder.ui.contactdetail.ContactDetailFragment
import com.example.friendminder.utils.ServiceLocator
import com.google.android.material.snackbar.Snackbar
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
 * One thing this screen still deliberately does NOT build, flagged on
 * FRM-99's Jira ticket rather than guessed at: the designed empty state
 * (FRM-107: 96dp glyph, headline, body copy, filled button) hasn't been
 * signed off, so zero-contacts shows a plain interim placeholder instead
 * (see fragment_home.xml's emptyStateGroup).
 *
 * The FAB (and the empty state's "Add someone" button) open FRM-102's
 * rebuilt add-contact flow ([AddContactsStep1Fragment]) as of that ticket -
 * see [openAddContact] and [AddContactsStep1Fragment.BACK_STACK_NAME]'s
 * kdoc for why that push uses a named back-stack entry.
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

        // FRM-102: shown once, after Step 2's "Add N people" collapses the
        // whole flow's back-stack entries and lands back here (see
        // AddContactsStep2Fragment.addSelectedPeople). Undo fully reverses
        // each added contact - friend-list membership, any frequency
        // override, and any group memberships - rather than partially,
        // since none of those existed before this flow ran.
        parentFragmentManager.setFragmentResultListener(ADD_CONTACTS_RESULT_KEY, viewLifecycleOwner) { _, bundle ->
            val addedIds = bundle.getStringArrayList(ADD_CONTACTS_RESULT_IDS).orEmpty()
            // Posted rather than called directly: this listener fires as part
            // of the same pop-back-stack transaction that recreates this
            // fragment's view, which runs BEFORE MainActivity's
            // addOnBackStackChangedListener -> updateBottomNav() call that
            // flips the bottom nav bar back to VISIBLE (it's GONE while the
            // add-contact flow's full-screen steps are shown). Building the
            // anchored Snackbar synchronously here anchors it against a nav
            // bar that's still gone, so it settles flush against the bottom
            // of the screen and the nav bar slides in on top of it a moment
            // later - "Undo" ends up sitting under the nav bar's touch
            // target. Posting defers this to after that listener has run.
            binding.root.post { showAddedSnackbar(addedIds) }
        }

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
        parentFragmentManager.commit {
            replace(R.id.nav_host_container, AddContactsStep1Fragment.newInstance())
            addToBackStack(AddContactsStep1Fragment.BACK_STACK_NAME)
        }
    }

    private fun showAddedSnackbar(addedIds: List<String>) {
        if (addedIds.isEmpty()) return
        // nav_host_container has no CoordinatorLayout ancestor (deliberately -
        // see activity_main.xml's kdoc), so a plain Snackbar.make() doesn't
        // know to avoid the bottom nav bar it lives beside: it ends up
        // overlapping the nav bar's touch targets instead of sitting above
        // them, which made "Undo" land on whichever tab was underneath it.
        // Anchoring explicitly to the bottom nav (see the .post{} call site
        // above for why this has to run after the nav bar is back to
        // VISIBLE, not synchronously from the fragment-result callback) is
        // what nav_host_container's own layout_weight trick can't do for an
        // overlay view like this one.
        val anchor = requireActivity().findViewById<View>(R.id.bottomNav)
            ?.takeIf { it.visibility == View.VISIBLE }
        // Anchoring gets the Snackbar above the nav bar, but it settles in
        // the same bottom-right corner as addContactFab (no CoordinatorLayout
        // here to push the FAB up out of the way, the way it would on a
        // screen that has one) - the FAB stays underneath, mostly hidden but
        // still very much alive for touch, so a tap that visually lands on
        // "Undo" can actually hit the FAB and relaunch the add-contact flow
        // instead. Hiding the FAB for the Snackbar's lifetime removes the
        // ambiguity outright; refresh() (already the source of truth for
        // whether the FAB should be showing at all) puts it back once the
        // Snackbar is gone, whichever way it went away.
        binding.addContactFab.visibility = View.GONE
        Snackbar.make(
            binding.root,
            resources.getQuantityString(R.plurals.format_add_contacts_snackbar, addedIds.size, addedIds.size),
            Snackbar.LENGTH_LONG
        ).setAnchorView(anchor)
            .setAction(R.string.action_undo) { undoAdd(addedIds) }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                    if (_binding != null) refresh()
                }
            })
            .show()
    }

    private fun undoAdd(addedIds: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val friendListRepo = ServiceLocator.friendListRepository
            val reminderFrequencyRepo = ServiceLocator.reminderFrequencyRepository
            val groupService = ServiceLocator.groupService
            addedIds.forEach { id ->
                groupService.getGroupsForContact(id).forEach { group -> groupService.removeContactFromGroup(id, group.id) }
                reminderFrequencyRepo.clearOverride(id)
                friendListRepo.removeFriend(id)
            }
            refresh()
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
        const val ADD_CONTACTS_RESULT_KEY = "home_add_contacts_result"
        const val ADD_CONTACTS_RESULT_IDS = "home_add_contacts_result_ids"

        fun newInstance(): HomeFragment = HomeFragment()
    }
}
