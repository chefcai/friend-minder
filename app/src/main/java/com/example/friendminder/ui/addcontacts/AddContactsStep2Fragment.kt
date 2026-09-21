package com.example.friendminder.ui.addcontacts

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact
import com.example.friendminder.databinding.FragmentAddContactsStep2Binding
import com.example.friendminder.ui.common.AvatarBinder
import com.example.friendminder.ui.common.ValuePickerDialogFragment
import com.example.friendminder.ui.home.HomeFragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Add-contact flow, Step 2 "Set up" (FRM-102, SCREENS-PHASE3.md §9.4):
 * where "the PRD's 'group and frequency assignment at add-time' is
 * satisfied." Nothing here is persisted until [addSelectedPeople] runs -
 * everything above it (frequency choice, group selection) lives purely in
 * this Fragment's own fields, exactly like Step 1's selection does, so
 * backing out of the whole flow before tapping "Add N people" leaves no
 * trace.
 */
class AddContactsStep2Fragment : Fragment() {

    private var _binding: FragmentAddContactsStep2Binding? = null
    private val binding get() = _binding!!

    private lateinit var selectedContacts: List<Contact>

    // Null means "track the app-wide default" rather than pin an explicit
    // per-contact override - same convention as Contact Detail's picker.
    private var chosenFrequencyDays: Int? = null
    private var globalDefaultFrequencyDays: Int = DEFAULT_FREQUENCY_DAYS
    private var chosenGroupIds: MutableSet<String> = mutableSetOf()
    private var groupNamesById: Map<String, String> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddContactsStep2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectedContacts = ContactBundleCodec.decode(requireArguments())

        binding.backButton.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.checkInEveryRow.setOnClickListener { showFrequencyPicker() }
        binding.groupsRow.setOnClickListener { showGroupPicker() }
        binding.addPeopleButton.setOnClickListener { addSelectedPeople() }

        childFragmentManager.setFragmentResultListener(FREQUENCY_PICKER_REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            val pickedDays = bundle.getInt(ValuePickerDialogFragment.RESULT_VALUE)
            chosenFrequencyDays = if (pickedDays == globalDefaultFrequencyDays) null else pickedDays
            renderFrequencyValue()
        }
        childFragmentManager.setFragmentResultListener(BulkGroupPickerDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, bundle ->
            chosenGroupIds = bundle.getStringArrayList(BulkGroupPickerDialogFragment.RESULT_GROUP_IDS).orEmpty().toMutableSet()
            // The picker lets the user create a brand-new group inline (its own
            // "+ New Group" flow), so groupNamesById - snapshotted once in
            // onViewCreated - can be missing that group's name by the time we
            // get here. Re-fetch before rendering rather than trusting the
            // stale snapshot, or a just-created group's row renders blank.
            viewLifecycleOwner.lifecycleScope.launch {
                groupNamesById = ServiceLocator.groupService.getGroups().associate { it.id to it.name }
                renderGroupsValue()
            }
        }

        applyHeaderInsets()
        bindAvatarSummary()
        binding.addPeopleButton.text = resources.getQuantityString(
            R.plurals.format_add_contacts_button, selectedContacts.size, selectedContacts.size
        )

        viewLifecycleOwner.lifecycleScope.launch {
            globalDefaultFrequencyDays = ServiceLocator.settingsRepository.getCooldownDays()
            renderFrequencyValue()

            groupNamesById = ServiceLocator.groupService.getGroups().associate { it.id to it.name }
            renderGroupsValue()
        }
    }

    override fun onResume() {
        super.onResume()
        applyEdgeToEdgeHeader()
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

    /** §9.4: "the selected avatars in a row (up to six, then '+N')". */
    private fun bindAvatarSummary() {
        binding.avatarSummaryContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val sizePx = (AVATAR_SIZE_DP * density).toInt()
        val marginPx = (AVATAR_SPACING_DP * density).toInt()

        selectedContacts.take(MAX_AVATARS_SHOWN).forEachIndexed { index, contact ->
            val frame = FrameLayout(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(sizePx, sizePx).also {
                    if (index > 0) it.marginStart = marginPx
                }
            }
            val photo = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = null
            }
            val initials = TextView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
                gravity = android.view.Gravity.CENTER
                textSize = 14f
            }
            frame.addView(photo)
            frame.addView(initials)
            binding.avatarSummaryContainer.addView(frame)
            AvatarBinder.bind(photo, initials, contact, ServiceLocator.contactPhotoLoader, viewLifecycleOwner.lifecycleScope)
        }

        val overflow = selectedContacts.size - MAX_AVATARS_SHOWN
        if (overflow > 0) {
            val overflowText = TextView(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(sizePx, sizePx).also { it.marginStart = marginPx }
                gravity = android.view.Gravity.CENTER
                textSize = 14f
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.fm_ink_dim))
                background = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_avatar_placeholder)
                text = getString(R.string.format_avatar_overflow, overflow)
            }
            binding.avatarSummaryContainer.addView(overflowText)
        }

        binding.selectedCountText.text = resources.getQuantityString(
            R.plurals.format_add_contacts_selected_people, selectedContacts.size, selectedContacts.size
        )
    }

    private fun renderFrequencyValue() {
        val effective = chosenFrequencyDays ?: globalDefaultFrequencyDays
        binding.checkInEveryValue.text = when (effective) {
            FREQUENCY_OPTION_7 -> getString(R.string.cooldown_option_7)
            FREQUENCY_OPTION_14 -> getString(R.string.cooldown_option_14)
            else -> getString(R.string.cooldown_option_3)
        }
    }

    private fun renderGroupsValue() {
        binding.groupsValue.text = if (chosenGroupIds.isEmpty()) {
            getString(R.string.label_groups_value_none)
        } else {
            chosenGroupIds.mapNotNull { groupNamesById[it] }.sorted().joinToString(", ")
        }
    }

    private fun showFrequencyPicker() {
        val effective = chosenFrequencyDays ?: globalDefaultFrequencyDays
        ValuePickerDialogFragment.newInstance(
            requestKey = FREQUENCY_PICKER_REQUEST_KEY,
            title = getString(R.string.label_check_in_every),
            values = FREQUENCY_OPTIONS,
            labels = FREQUENCY_OPTIONS.map { days ->
                when (days) {
                    FREQUENCY_OPTION_7 -> getString(R.string.cooldown_option_7)
                    FREQUENCY_OPTION_14 -> getString(R.string.cooldown_option_14)
                    else -> getString(R.string.cooldown_option_3)
                }
            },
            selectedValue = effective
        ).show(childFragmentManager, "add_contacts_frequency_picker")
    }

    private fun showGroupPicker() {
        BulkGroupPickerDialogFragment.newInstance(chosenGroupIds).show(childFragmentManager, "add_contacts_group_picker")
    }

    private fun addSelectedPeople() {
        binding.addPeopleButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val friendListRepo = ServiceLocator.friendListRepository
            val reminderFrequencyRepo = ServiceLocator.reminderFrequencyRepository
            val groupService = ServiceLocator.groupService

            selectedContacts.forEach { contact ->
                friendListRepo.addFriend(contact)
                chosenFrequencyDays?.let { days -> reminderFrequencyRepo.setOverride(contact.id, days) }
                chosenGroupIds.forEach { groupId -> groupService.assignContactToGroup(contact.id, groupId) }
            }

            parentFragmentManager.setFragmentResult(
                HomeFragment.ADD_CONTACTS_RESULT_KEY,
                androidx.core.os.bundleOf(
                    HomeFragment.ADD_CONTACTS_RESULT_IDS to ArrayList(selectedContacts.map { it.id })
                )
            )
            // §9.1/§10.2-style flow collapse: one call removes both Step 1
            // and Step 2 from the back stack, returning cleanly to Home -
            // see AddContactsStep1Fragment.BACK_STACK_NAME's kdoc.
            parentFragmentManager.popBackStack(
                AddContactsStep1Fragment.BACK_STACK_NAME,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val AVATAR_SIZE_DP = 40
        private const val AVATAR_SPACING_DP = 8
        private const val MAX_AVATARS_SHOWN = 6
        private const val DEFAULT_FREQUENCY_DAYS = 3

        private const val FREQUENCY_OPTION_3 = 3
        private const val FREQUENCY_OPTION_7 = 7
        private const val FREQUENCY_OPTION_14 = 14
        private val FREQUENCY_OPTIONS = intArrayOf(FREQUENCY_OPTION_3, FREQUENCY_OPTION_7, FREQUENCY_OPTION_14)
        private const val FREQUENCY_PICKER_REQUEST_KEY = "add_contacts_frequency_picker"

        fun newInstance(contacts: List<Contact>): AddContactsStep2Fragment =
            AddContactsStep2Fragment().apply {
                arguments = ContactBundleCodec.encode(contacts)
            }
    }
}
