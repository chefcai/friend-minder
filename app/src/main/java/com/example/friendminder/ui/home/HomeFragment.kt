package com.example.friendminder.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact
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
 *
 * FRM-112 (SCREENS-PHASE3.md §10): a row long-press (or the row's "Select"
 * accessibility action, §10.5) enters bulk-removal selection mode.
 * [selectedContactIds] is the single source of truth for both "are we in
 * selection mode" ([isSelectionMode] - non-empty means yes) and "which
 * rows are selected"; [baseRows] holds the last-fetched contact/stat data
 * independent of selection state, and [renderRows] is what stamps the
 * current selection onto it for the adapter. Splitting fetch (refresh)
 * from render (renderRows) means toggling a selection never re-hits the
 * repository layer.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HomeContactAdapter
    private lateinit var selectionModeBackCallback: OnBackPressedCallback

    private var baseRows: List<BaseContactRow> = emptyList()
    private val selectedContactIds = mutableSetOf<String>()
    private val isSelectionMode: Boolean get() = selectedContactIds.isNotEmpty()

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
            scope = viewLifecycleOwner.lifecycleScope,
            // §10.2: tap either opens Contact Detail or toggles selection
            // depending on mode; long-press (or the row's "Select"
            // accessibility action, §10.5) always toggles either way -
            // both are one-line branches, folded in here rather than
            // given their own named functions (detekt TooManyFunctions).
            onRowClicked = { contact -> if (isSelectionMode) toggleSelection(contact.id) else openContactDetail(contact.id) },
            onRowLongPressed = { contact -> toggleSelection(contact.id) }
        )
        binding.contactRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.contactRecyclerView.adapter = adapter

        binding.emptyStateAddButton.setOnClickListener { openAddContact() }

        setUpSelectionMode()

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
            binding.root.post {
                if (addedIds.isNotEmpty()) {
                    showBottomAnchoredSnackbar(
                        resources.getQuantityString(R.plurals.format_add_contacts_snackbar, addedIds.size, addedIds.size),
                        undoAction = { undoAdd(addedIds) }
                    )
                }
            }
        }

        applyHeaderInsets()
    }

    /**
     * FRM-112 setup, split out of onViewCreated (detekt LongMethod) -
     * mirrors SettingsFragment.setUpNotificationsDiagnosticsRow's own
     * reason for the same split.
     */
    private fun setUpSelectionMode() {
        // §10.2: "Leave via the header's X, or system back, or by
        // deselecting the last contact" - the third path is
        // toggleSelection itself (isSelectionMode is derived from
        // selectedContactIds), this local covers the first two. A local
        // val rather than a member function keeps this class's function
        // count under detekt's TooManyFunctions threshold.
        val exit: () -> Unit = {
            if (isSelectionMode) {
                selectedContactIds.clear()
                updateSelectionModeUi()
                renderRows()
            }
        }
        binding.headerCloseButton.setOnClickListener { exit() }
        // §10.5: "the header's 'N selected' is a live region that
        // announces on change" - set once; every later headerTitle.text
        // write (updateSelectionModeUi) is then announced automatically.
        ViewCompat.setAccessibilityLiveRegion(binding.headerTitle, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)

        // §10.2: "Leave via ... system back". Registered disabled and
        // flipped on with the rest of the mode's UI state (see
        // updateSelectionModeUi) - added after MainActivity's own
        // activity-scoped callback, so it intercepts first while enabled.
        selectionModeBackCallback = requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, enabled = false) {
            exit()
        }

        childFragmentManager.setFragmentResultListener(
            ConfirmBulkRemovalDialogFragment.RESULT_KEY, viewLifecycleOwner
        ) { _, _ -> performBulkRemoval() }

        updateSelectionModeUi() // establishes the normal-mode FAB click listener etc.
    }

    override fun onResume() {
        super.onResume()
        setEdgeToEdgeHeader(enabled = true)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        setEdgeToEdgeHeader(enabled = false)
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
    private fun setEdgeToEdgeHeader(enabled: Boolean) {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !enabled
        if (enabled) {
            window.statusBarColor = Color.TRANSPARENT
        } else {
            val typedValue = android.util.TypedValue()
            if (requireContext().theme.resolveAttribute(android.R.attr.statusBarColor, typedValue, true)) {
                window.statusBarColor = typedValue.data
            }
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
                // Defensive: bulk removal always exits selection mode before
                // this can run empty, but a belt-and-braces reset here means
                // this screen can never show "N selected" over an empty list.
                if (isSelectionMode) {
                    selectedContactIds.clear()
                    updateSelectionModeUi()
                }
                baseRows = emptyList()
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

            baseRows = friends
                .sortedWith(compareBy(collator) { it.name })
                .map { contact ->
                    val stats = statisticsService.getStatistics(contact.id)
                    BaseContactRow(
                        contact = contact,
                        streak = stats.streak,
                        lastTouchText = HomeLastTouchFormatter.format(requireContext(), stats.lastContacted)
                    )
                }
            // A selected contact could in principle vanish out from under
            // selection mode (e.g. removed elsewhere) - drop any id that no
            // longer has a row rather than let the header's count drift
            // from what's actually selectable.
            val stillPresent = baseRows.mapTo(mutableSetOf()) { it.contact.id }
            if (selectedContactIds.retainAll(stillPresent)) {
                updateSelectionModeUi()
            }
            renderRows()
        }
    }

    /** Stamps the current selection state onto [baseRows] for the adapter - see the class kdoc. */
    private fun renderRows() {
        val rows = baseRows.map { base ->
            HomeContactRow(
                contact = base.contact,
                streak = base.streak,
                lastTouchText = base.lastTouchText,
                isSelectionMode = isSelectionMode,
                isSelected = base.contact.id in selectedContactIds
            )
        }
        adapter.submitList(rows)
    }

    private fun toggleSelection(contactId: String) {
        if (!selectedContactIds.remove(contactId)) {
            selectedContactIds.add(contactId)
        }
        // §10.2: "deselecting everything exits automatically rather than
        // leaving an empty mode" - isSelectionMode is derived from the set,
        // so this call already reflects that without a separate check.
        updateSelectionModeUi()
        renderRows()
    }

    /**
     * §10.3's table, applied: header title/leading icon, the row-badge
     * slot's mode (via [renderRows]'s isSelectionMode, not here), and the
     * FAB's fill/glyph/contentDescription/click-target all swap together.
     * Also the single place [selectionModeBackCallback] gets enabled or
     * disabled, so "system back" only ever does this while the mode is on.
     */
    private fun updateSelectionModeUi() {
        selectionModeBackCallback.isEnabled = isSelectionMode
        val titleLayoutParams = binding.headerTitle.layoutParams as ViewGroup.MarginLayoutParams
        if (isSelectionMode) {
            binding.headerCloseButton.visibility = View.VISIBLE
            titleLayoutParams.marginStart = resources.getDimensionPixelSize(R.dimen.fm_header_title_margin_with_leading_icon)
            binding.headerTitle.text = getString(R.string.format_selected_count, selectedContactIds.size)
            binding.addContactFab.setImageResource(R.drawable.ic_trash)
            binding.addContactFab.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.fm_error))
            binding.addContactFab.contentDescription = resources.getQuantityString(
                R.plurals.content_desc_bulk_removal_fab, selectedContactIds.size, selectedContactIds.size
            )
            binding.addContactFab.setOnClickListener { confirmBulkRemoval() }
        } else {
            binding.headerCloseButton.visibility = View.GONE
            titleLayoutParams.marginStart = resources.getDimensionPixelSize(R.dimen.fm_space_6)
            binding.headerTitle.text = getString(R.string.title_home)
            binding.addContactFab.setImageResource(R.drawable.ic_add)
            binding.addContactFab.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.fm_primary))
            binding.addContactFab.contentDescription = getString(R.string.content_desc_add_contact)
            binding.addContactFab.setOnClickListener { openAddContact() }
        }
        binding.headerTitle.layoutParams = titleLayoutParams
    }

    private fun openAddContact() {
        parentFragmentManager.commit {
            replace(R.id.nav_host_container, AddContactsStep1Fragment.newInstance())
            addToBackStack(AddContactsStep1Fragment.BACK_STACK_NAME)
        }
    }

    /**
     * Shared by the add-contacts Snackbar (ADD_CONTACTS_RESULT_KEY's
     * listener, above) and bulk removal's (performBulkRemoval, below) -
     * previously two near-identical bodies. nav_host_container has no
     * CoordinatorLayout ancestor (deliberately - see activity_main.xml's
     * kdoc), so a plain Snackbar.make() doesn't know to avoid the bottom
     * nav bar it lives beside, and settles into the same bottom-right
     * corner as addContactFab with no CoordinatorLayout to push the FAB
     * out of the way - a tap that visually lands on the Snackbar's action
     * can otherwise hit the FAB underneath instead. Anchoring to the nav
     * bar plus hiding the FAB for the Snackbar's lifetime (restored via
     * refresh() - already the source of truth for FAB visibility - on
     * dismiss, however it dismissed) fixes both at once. [undoAction] is
     * null for the no-Undo bulk-removal case (§10.4).
     */
    private fun showBottomAnchoredSnackbar(message: String, undoAction: (() -> Unit)? = null) {
        val anchor = requireActivity().findViewById<View>(R.id.bottomNav)
            ?.takeIf { it.visibility == View.VISIBLE }
        binding.addContactFab.visibility = View.GONE
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).setAnchorView(anchor)
        if (undoAction != null) {
            snackbar.setAction(R.string.action_undo) { undoAction() }
        }
        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                if (_binding != null) refresh()
            }
        }).show()
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

    /** §10.4: counts both the selected contacts and the outreach logs they'd take with them, before showing the sheet. */
    private fun confirmBulkRemoval() {
        val ids = selectedContactIds.toList()
        if (ids.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val outreachLogRepo = ServiceLocator.outreachLogRepository
            val outreachCount = ids.sumOf { outreachLogRepo.getForContact(it).size }
            ConfirmBulkRemovalDialogFragment.newInstance(ids.size, outreachCount)
                .show(childFragmentManager, ConfirmBulkRemovalDialogFragment::class.java.simpleName)
        }
    }

    /**
     * Runs only after [ConfirmBulkRemovalDialogFragment] reports a
     * confirmed result (§10.4) - deletes each selected contact's outreach
     * logs (no cascade delete on [ServiceLocator.friendListRepository]),
     * then mirrors [undoAdd]'s own group/frequency/friend-list cleanup, in
     * reverse: this is genuine removal rather than "undo an add", but the
     * same three stores need clearing either way. No Undo afterwards on
     * the resulting Snackbar, unlike the add-contacts flow's - see the
     * [showBottomAnchoredSnackbar] call at the end of this function.
     */
    private fun performBulkRemoval() {
        val ids = selectedContactIds.toList()
        if (ids.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val friendListRepo = ServiceLocator.friendListRepository
            val outreachLogRepo = ServiceLocator.outreachLogRepository
            val groupService = ServiceLocator.groupService
            val reminderFrequencyRepo = ServiceLocator.reminderFrequencyRepository

            ids.forEach { id ->
                outreachLogRepo.getForContact(id).forEach { log -> outreachLogRepo.delete(log.id) }
                groupService.getGroupsForContact(id).forEach { group -> groupService.removeContactFromGroup(id, group.id) }
                reminderFrequencyRepo.clearOverride(id)
                friendListRepo.removeFriend(id)
            }

            val removedCount = ids.size
            selectedContactIds.clear()
            updateSelectionModeUi()
            refresh()
            // §10.4: "No Undo - the history is genuinely gone, and an Undo
            // that cannot restore it would be a lie." undoAction stays
            // null, deliberately, unlike the add-contacts Snackbar's Undo
            // (see the ADD_CONTACTS_RESULT_KEY listener in onViewCreated).
            showBottomAnchoredSnackbar(
                resources.getQuantityString(R.plurals.format_bulk_removal_removed_snackbar, removedCount, removedCount)
            )
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

    /** Fetched contact/stat data, independent of selection state - see the class kdoc. */
    private data class BaseContactRow(
        val contact: Contact,
        val streak: Int,
        val lastTouchText: String
    )

    companion object {
        const val ADD_CONTACTS_RESULT_KEY = "home_add_contacts_result"
        const val ADD_CONTACTS_RESULT_IDS = "home_add_contacts_result_ids"

        fun newInstance(): HomeFragment = HomeFragment()
    }
}
