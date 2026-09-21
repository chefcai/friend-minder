package com.example.friendminder.ui.addcontacts

import android.Manifest
import android.content.Intent
import android.graphics.Color
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.friendminder.R
import com.example.friendminder.data.contacts.ContactsLoader
import com.example.friendminder.data.models.Contact
import com.example.friendminder.databinding.FragmentAddContactsStep1Binding
import com.example.friendminder.ui.common.ValuePickerDialogFragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/**
 * Add-contact flow, Step 1 "Choose people" (FRM-102, SCREENS-PHASE3.md
 * §9.2/§9.3). Replaces the old Phase 2 `FriendListFragment` picker (now
 * deleted) in both of its former modes - onboarding is dropped entirely (§9.0), so
 * there is only one mode here. Entered from Home's FAB or its empty
 * state's "Add someone" button.
 *
 * Untracked device contacts only: already-tracked people are excluded up
 * front (§9.2), so unlike the old picker this screen carries no
 * missing-contact / remove-by-tap handling at all - that responsibility
 * moved to [com.example.friendminder.ui.home.LegacyDiagnosticsFragment]'s
 * missing-contacts banner (FRM-99) and is untouched by this ticket.
 *
 * Edge-to-edge header wiring duplicated from HomeFragment/
 * OverallHistoryFragment rather than shared - see those classes' kdoc for
 * why (no base Fragment class in this codebase).
 */
class AddContactsStep1Fragment : Fragment() {

    private var _binding: FragmentAddContactsStep1Binding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AddContactCandidateAdapter

    /** Untracked device contacts with at least one phone number. */
    private var allCandidates: List<Contact> = emptyList()
    private var phoneNumbersByContactId: Map<String, List<String>> = emptyMap()

    /** Insertion order preserved deliberately: Step 2's avatar summary shows "up to six" in the order they were chosen (§9.4). */
    private val selectedIds = linkedSetOf<String>()
    private var searchQuery: String = ""
    private var shownEmptyState: EmptyState? = null

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loadCandidates()
            } else {
                val canAskAgain = shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)
                showPermissionState(if (canAskAgain) EmptyState.PERMISSION_DENIED_ONCE else EmptyState.PERMISSION_PERMANENTLY_DENIED)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddContactsStep1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Step 1's Fragment instance survives being covered by Step 2 (it's
        // a back-stack replace(), not a destroy - §9.6 "selections survive
        // back-navigation"), but its *view* is torn down and rebuilt, so the
        // search box always starts this pass empty. Reset the field to
        // match rather than leaving a stale query filtering a blank-looking
        // box.
        searchQuery = ""

        binding.backButton.setOnClickListener { cancelFlow() }

        adapter = AddContactCandidateAdapter(
            photoLoader = ServiceLocator.contactPhotoLoader,
            scope = viewLifecycleOwner.lifecycleScope
        ) { contact -> onCandidateClicked(contact) }
        binding.candidateRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.candidateRecyclerView.adapter = adapter
        // GH #71 (see the old FriendListFragment/ContactAdapter): search
        // filtering resubmits the list on every keystroke, which fights
        // with RecyclerView's default add/move/change animator.
        binding.candidateRecyclerView.itemAnimator = null

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                renderList()
                binding.candidateRecyclerView.scrollToPosition(0)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.nextButton.setOnClickListener { goToStep2() }

        childFragmentManager.setFragmentResultListener(NUMBER_PICKER_REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            onPhoneNumberChosen(bundle.getInt(ValuePickerDialogFragment.RESULT_VALUE))
        }

        applyHeaderInsets()
        applyFooterInsets()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            loadCandidates()
        } else {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    override fun onResume() {
        super.onResume()
        applyEdgeToEdgeHeader()
        // §9.3: "Re-check on resume - the current code does this and it
        // must survive." Only re-checks while a permission-denied state is
        // actually showing, so granting elsewhere (system Settings) is
        // picked up without re-prompting on every ordinary resume.
        val isShowingPermissionState = shownEmptyState == EmptyState.PERMISSION_DENIED_ONCE ||
            shownEmptyState == EmptyState.PERMISSION_PERMANENTLY_DENIED
        if (isShowingPermissionState &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            loadCandidates()
        }
    }

    override fun onPause() {
        super.onPause()
        restoreStandardStatusBar()
    }

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

    private fun applyHeaderInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { _, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.statusBarSpacer.layoutParams = binding.statusBarSpacer.layoutParams.apply { height = topInset }
            binding.statusBarSpacer.requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(binding.headerContainer)
    }

    /**
     * FRM-118/FRM-128: this screen enables edge-to-edge for itself (same as
     * Home), but until now nothing accounted for the BOTTOM inset - the
     * pinned footer sat flush against the true bottom of the window with
     * no clearance for the system nav bar/gesture area, which could push
     * "Next" partly or fully into territory the system reserves for its
     * own back-swipe gesture (FRM-118). Mirrors MainActivity's bottom-nav
     * fix: grow the footer's existing bottom padding by the real inset
     * rather than replacing it.
     *
     * The list's bottom padding can't be a fixed borrowed constant either
     * (FRM-128 - SCREENS-PHASE3.md SS9.2 wants "footer height + 16dp",
     * and the footer's real height now also depends on the inset above,
     * plus whether its explanatory copy wraps to a second line at zero
     * selected) - it's recomputed from the footer's actual measured
     * height every time that height changes, in both directions.
     */
    private fun applyFooterInsets() {
        val updateCandidateListBottomPadding = {
            val runOut = resources.getDimensionPixelSize(R.dimen.fm_space_4)
            binding.candidateRecyclerView.updatePadding(bottom = binding.footerBar.height + runOut)
        }

        val baseFooterPaddingBottom = binding.footerBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.footerBar) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = baseFooterPaddingBottom + bottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(binding.footerBar)

        binding.footerBar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                updateCandidateListBottomPadding()
            }
        }
    }

    private fun cancelFlow() {
        parentFragmentManager.popBackStack()
    }

    private fun loadCandidates() {
        shownEmptyState = null
        binding.emptyStateGroup.visibility = View.GONE
        binding.candidateRecyclerView.visibility = View.GONE
        binding.loadingIndicator.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val trackedIds = ServiceLocator.friendListRepository.getFriendList().map { it.id }.toSet()
            val result = ContactsLoader.loadContactsWithPhoneNumbers(requireContext())
            phoneNumbersByContactId = result.phoneNumbersByContactId
            allCandidates = result.contacts.filterNot { it.id in trackedIds }

            binding.loadingIndicator.visibility = View.GONE

            if (allCandidates.isEmpty()) {
                showPermissionState(EmptyState.NO_PHONE_NUMBERS)
                return@launch
            }

            renderList()
        }
    }

    private fun renderList() {
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
        val filtered = allCandidates
            .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
            .sortedWith(compareBy(collator) { it.name })

        binding.candidateRecyclerView.visibility = View.VISIBLE
        binding.emptyStateGroup.visibility = View.GONE
        shownEmptyState = null

        adapter.submitList(
            filtered.map { contact ->
                AddContactCandidateRow(
                    contact = contact,
                    isSelected = contact.id in selectedIds,
                    phoneNumberCount = phoneNumbersByContactId[contact.id]?.size ?: 1
                )
            }
        )

        updateFooter()
    }

    private fun onCandidateClicked(contact: Contact) {
        val isCurrentlySelected = contact.id in selectedIds
        if (!isCurrentlySelected) {
            // §9.2: multi-number contacts (FRM-28/GH #39) need a choice made
            // now, before the selection takes effect.
            val numbers = phoneNumbersByContactId[contact.id].orEmpty()
            if (numbers.size > 1) {
                showPhoneNumberPicker(contact, numbers)
                return
            }
        }

        if (isCurrentlySelected) selectedIds -= contact.id else selectedIds += contact.id
        renderList()
    }

    private var pendingNumberPickerContactId: String? = null

    private fun showPhoneNumberPicker(contact: Contact, numbers: List<String>) {
        pendingNumberPickerContactId = contact.id
        ValuePickerDialogFragment.newInstance(
            requestKey = NUMBER_PICKER_REQUEST_KEY,
            title = getString(R.string.format_choose_phone_number, contact.name),
            options = numbers.indices.map { it to numbers[it] },
            selectedValue = 0
        ).show(childFragmentManager, "choose_phone_number")
    }

    private fun onPhoneNumberChosen(numberIndex: Int) {
        val contactId = pendingNumberPickerContactId ?: return
        pendingNumberPickerContactId = null
        val chosenNumber = phoneNumbersByContactId[contactId]?.getOrNull(numberIndex) ?: return
        allCandidates = allCandidates.map { if (it.id == contactId) it.copy(phoneNumber = chosenNumber) else it }
        selectedIds += contactId
        renderList()
    }

    private fun updateFooter() {
        val count = selectedIds.size
        binding.footerCountText.text = if (count == 0) {
            getString(R.string.label_add_contacts_select_to_continue)
        } else {
            getString(R.string.format_selected_count, count)
        }
        binding.nextButton.isEnabled = count > 0
    }

    private fun showPermissionState(state: EmptyState) {
        shownEmptyState = state
        binding.loadingIndicator.visibility = View.GONE
        binding.candidateRecyclerView.visibility = View.GONE
        binding.emptyStateGroup.visibility = View.VISIBLE

        when (state) {
            EmptyState.PERMISSION_DENIED_ONCE -> {
                binding.emptyStateHeadline.text = getString(R.string.label_add_contacts_permission_headline)
                binding.emptyStateBody.text = getString(R.string.label_add_contacts_permission_denied_body)
                binding.emptyStateAction.text = getString(R.string.action_allow_access)
                binding.emptyStateAction.visibility = View.VISIBLE
                binding.emptyStateAction.setOnClickListener {
                    requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                }
            }
            EmptyState.PERMISSION_PERMANENTLY_DENIED -> {
                binding.emptyStateHeadline.text = getString(R.string.label_add_contacts_permission_headline)
                binding.emptyStateBody.text = getString(R.string.label_add_contacts_permission_permanently_denied_body)
                binding.emptyStateAction.text = getString(R.string.action_open_settings)
                binding.emptyStateAction.visibility = View.VISIBLE
                binding.emptyStateAction.setOnClickListener {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", requireContext().packageName, null)
                        }
                    )
                }
            }
            EmptyState.NO_PHONE_NUMBERS -> {
                binding.emptyStateHeadline.text = getString(R.string.label_no_contacts_with_numbers)
                binding.emptyStateBody.text = getString(R.string.label_add_contacts_no_numbers_body)
                binding.emptyStateAction.visibility = View.GONE
            }
        }
        binding.footerCountText.text = getString(R.string.label_add_contacts_select_to_continue)
        binding.nextButton.isEnabled = false
    }

    private fun goToStep2() {
        if (selectedIds.isEmpty()) return
        // selectedIds order is preserved (LinkedHashSet) so Step 2's avatar
        // summary lists them in the order they were chosen (§9.4).
        val chosen = selectedIds.mapNotNull { id -> allCandidates.firstOrNull { it.id == id } }
        parentFragmentManager.commit {
            replace(R.id.nav_host_container, AddContactsStep2Fragment.newInstance(chosen))
            addToBackStack(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class EmptyState { PERMISSION_DENIED_ONCE, PERMISSION_PERMANENTLY_DENIED, NO_PHONE_NUMBERS }

    companion object {
        // Named so AddContactsStep2Fragment.addSelectedPeople can pop both
        // steps off the back stack in a single call
        // (popBackStack(BACK_STACK_NAME, POP_BACK_STACK_INCLUSIVE)) once the
        // flow completes, landing cleanly back on whichever screen pushed
        // Step 1 (Home's FAB or empty state, or LegacyDiagnosticsFragment's
        // "Add friends" button) - same technique MainActivity already uses
        // for NAV_DESTINATION_BACK_STACK_NAME.
        const val BACK_STACK_NAME = "add_contacts_flow"
        private const val NUMBER_PICKER_REQUEST_KEY = "add_contacts_number_picker"

        fun newInstance(): AddContactsStep1Fragment = AddContactsStep1Fragment()
    }
}
