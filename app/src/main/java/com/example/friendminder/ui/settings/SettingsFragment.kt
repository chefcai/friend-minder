package com.example.friendminder.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.databinding.FragmentSettingsBinding
import com.example.friendminder.ui.common.EdgeToEdgeHeader
import com.example.friendminder.ui.common.ImeInsetPadding
import com.example.friendminder.ui.common.ValuePickerDialogFragment
import com.example.friendminder.ui.home.LegacyDiagnosticsFragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FRM-178 (Cai, option B): the reminder time (Fixed time / Random window,
 * the Time row and its status line) moved to the Notifications screen - see
 * [ReminderTimeController]. This screen keeps reminders per day, the
 * cooldown and messages, and shows the time on its Notifications row.
 *
 * Reminder configuration (Designer spec section 3.2), reachable from the
 * bottom nav (FRM-100). Carries the "Advanced" row (below the reminder
 * form) that opens [AdvancedSettingsFragment].
 *
 * FRM-102 (SCREENS-PHASE3.md 9.0, "onboarding is dropped"): this screen
 * used to run in two modes - a forward-only first-launch step reached from
 * FriendListFragment with no back arrow, landing on HomeFragment on save,
 * versus an edit mode reachable from the bottom nav. The app now always
 * launches to Home (MainActivity no longer branches on "do you have
 * friends yet"), so onboarding mode can never be entered and is retired
 * here rather than left as dead, unreachable code - see MainActivity's own
 * 9.0 update for the other half of this change.
 *
 * FRM-130 (SCREENS-PHASE3.md 7.2, "there is no Save Settings button"):
 * every control below writes to [ServiceLocator.settingsRepository] on
 * change, via [scheduleAutoSaveUnlessLoading]'s 600ms debounce (7.4's
 * decided value), confirmed by the settingsSavedPill overlay fading
 * in/out rather than a Toast. [isLoadingSettings] guards the programmatic
 * field population in [loadCurrentSettings] from triggering a spurious
 * autosave (and pill) the moment the screen opens.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var isLoadingSettings = false
    private var autoSaveJob: Job? = null
    private var templatesText: String = ""
    private var savedPillJob: Job? = null

    // GH #98/#121: cooldownRow replaced cooldownSpinner, so this is now the
    // source of truth for the picked value between loadCurrentSettings and
    // performSave rather than a Spinner selection index.
    private var cooldownDays = DEFAULT_COOLDOWN_DAYS

    // GH #165/FRM-130: contactsPerDayRow replaced contactsPerDaySpinner,
    // same reasoning as cooldownDays above - source of truth between
    // loadCurrentSettings and performSave rather than a Spinner position.
    private var contactsPerDay = DEFAULT_CONTACTS_PER_DAY

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op: see 3.3 banner for persistent denial */ }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        EdgeToEdgeHeader.applyHeaderInsets(binding.headerContainer, binding.statusBarSpacer)
        // GH #153: this screen's edge-to-edge header opts it out of the
        // platform's automatic keyboard resize (see ImeInsetPadding's
        // kdoc) - without this, the keyboard drew straight over
        // messageTemplateInput with no accommodation at all. (FRM-165: the
        // templates field now lives in TemplatesEditorSheet; the padding is
        // kept so the scroll still clears the keyboard if one is up.)
        ImeInsetPadding.applyToBottom(binding.settingsScrollView)

        // GH #165/FRM-130: contactsPerDayRow replaced contactsPerDaySpinner
        // - same shared picker, same inline-in-onViewCreated wiring as
        // cooldownRow just below (see its comment for why it's inline
        // rather than its own function).
        binding.contactsPerDayRow.setOnClickListener {
            ValuePickerDialogFragment.newInstance(
                requestKey = CONTACTS_PER_DAY_PICKER_REQUEST_KEY,
                title = getString(R.string.title_edit_contacts_per_day),
                options = (MIN_CONTACTS_PER_DAY..MAX_CONTACTS_PER_DAY).map { count ->
                    count to resources.getQuantityString(R.plurals.format_contacts_per_day_option, count, count)
                },
                selectedValue = contactsPerDay
            ).show(childFragmentManager, "settings_contacts_per_day_picker")
        }
        childFragmentManager.setFragmentResultListener(CONTACTS_PER_DAY_PICKER_REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            contactsPerDay = bundle.getInt(ValuePickerDialogFragment.RESULT_VALUE)
            renderContactsPerDayValue()
            scheduleAutoSaveUnlessLoading()
        }

        // GH #98/#121: "build it once" - the global cooldown is one of the
        // four call sites #98 names for the shared picker's Custom... path
        // (SCREENS-PHASE3.md §7.2/§8.4). Inlined into the click listener
        // rather than its own function - see setUpNavigationRows for why
        // that matters here (detekt's TooManyFunctions).
        binding.cooldownRow.setOnClickListener {
            ValuePickerDialogFragment.newInstance(
                requestKey = COOLDOWN_PICKER_REQUEST_KEY,
                title = getString(R.string.title_edit_cooldown),
                options = COOLDOWN_OPTIONS.map { days ->
                    days to when (days) {
                        COOLDOWN_OPTION_7 -> getString(R.string.cooldown_option_7)
                        COOLDOWN_OPTION_14 -> getString(R.string.cooldown_option_14)
                        else -> getString(R.string.cooldown_option_3)
                    }
                },
                selectedValue = cooldownDays,
                customEntry = ValuePickerDialogFragment.CustomEntryOptions()
            ).show(childFragmentManager, "settings_cooldown_picker")
        }
        childFragmentManager.setFragmentResultListener(COOLDOWN_PICKER_REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            cooldownDays = bundle.getInt(ValuePickerDialogFragment.RESULT_VALUE)
            renderCooldownValue()
            scheduleAutoSaveUnlessLoading()
        }

        setUpMessageTemplateControls()
        setUpNavigationRows()

        ensureNotificationPermission()
        loadCurrentSettings()
    }

    // A property-typed lambda rather than a function - dodges detekt's
    // TooManyFunctions, which only counts declared functions, not lambdas.
    private val renderCooldownValue = {
        binding.cooldownValue.text = when (cooldownDays) {
            COOLDOWN_OPTION_3 -> getString(R.string.cooldown_option_3)
            COOLDOWN_OPTION_7 -> getString(R.string.cooldown_option_7)
            COOLDOWN_OPTION_14 -> getString(R.string.cooldown_option_14)
            else -> resources.getQuantityString(R.plurals.format_days_option, cooldownDays, cooldownDays)
        }
    }

    // GH #165/FRM-130: contactsPerDayRow's render step, same lambda-not-
    // function dodge as renderCooldownValue above.
    private val renderContactsPerDayValue = {
        binding.contactsPerDayValue.text =
            resources.getQuantityString(R.plurals.format_contacts_per_day_option, contactsPerDay, contactsPerDay)
    }

    // FRM-165 (ST-2): the templates are edited in TemplatesEditorSheet, a
    // full-height sheet opened from the Templates value row; Settings keeps
    // the text in templatesText and shows only the count.
    private fun setUpMessageTemplateControls() {
        binding.includeMessageSwitch.setOnCheckedChangeListener { _, checked ->
            renderTemplatesRowEnabled(checked)
            scheduleAutoSaveUnlessLoading()
        }
        binding.templatesRow.setOnClickListener {
            TemplatesEditorSheet.newInstance(TEMPLATES_EDITOR_REQUEST_KEY, templatesText)
                .show(childFragmentManager, "settings_templates_editor")
        }
        childFragmentManager.setFragmentResultListener(TEMPLATES_EDITOR_REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            val edited = bundle.getString(TemplatesEditorSheet.RESULT_TEXT).orEmpty()
            if (edited != templatesText) {
                templatesText = edited
                renderTemplatesValue()
                scheduleAutoSaveUnlessLoading()
            }
        }
    }

    // FRM-99: interim wiring for the retired setup-hub's remaining content -
    // see LegacyDiagnosticsFragment's kdoc. notificationsDiagnosticsRow is
    // not a Designer-P3 spec, just wired here so the functionality stays
    // reachable pending the FRM-103 pass that's meant to design its real
    // home. Mirrors advancedRow's row pattern rather than a new one.
    //
    // GH #120: advancedRow already sits directly above this row in
    // fragment_settings.xml (only a 1dp divider between them), so
    // "Advanced adjacent to Notifications" was already true going into
    // this ticket - the actual gap was the row's own label, which still
    // read "Notifications & diagnostics" ("diagnostics" names an
    // implementation detail, not something a user came looking for, per
    // the issue). title_notifications_diagnostics is now just
    // "Notifications" - resource id kept as-is since renaming it touches
    // nothing user-facing and isn't part of this ticket's ask.
    private fun setUpNavigationRows() {
        binding.advancedRow.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.nav_host_container, AdvancedSettingsFragment.newInstance())
                addToBackStack(null)
            }
        }

        binding.notificationsDiagnosticsRow.setOnClickListener {
            parentFragmentManager.commit {
                replace(R.id.nav_host_container, LegacyDiagnosticsFragment.newInstance())
                addToBackStack(null)
            }
        }
    }

    private fun scheduleAutoSaveUnlessLoading() {
        if (isLoadingSettings) return
        autoSaveJob?.cancel()
        autoSaveJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MS)
            performSave()
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun loadCurrentSettings() {
        isLoadingSettings = true
        viewLifecycleOwner.lifecycleScope.launch {
            val repo = ServiceLocator.settingsRepository

            // FRM-178: the time itself is edited on the Notifications screen;
            // its row here shows it as the value.
            binding.notificationsValue.text = ReminderTimeController.summary(requireContext())

            contactsPerDay = repo.getContactsPerDay().coerceIn(MIN_CONTACTS_PER_DAY, MAX_CONTACTS_PER_DAY)
            renderContactsPerDayValue()
            cooldownDays = repo.getCooldownDays()
            renderCooldownValue()

            val messageEnabled = repo.isMessageEnabled()
            val templatesText = repo.getMessageTemplates().joinToString("\n")
            binding.includeMessageSwitch.isChecked = messageEnabled
            renderTemplatesRowEnabled(messageEnabled)
            this@SettingsFragment.templatesText = templatesText
            renderTemplatesValue()

            isLoadingSettings = false
        }
    }

    // Property-typed lambdas, same TooManyFunctions dodge as
    // renderCooldownValue. The value is the bare count ("Templates  3 >");
    // the sheet itself spells it out.
    private val renderTemplatesValue = {
        binding.templatesValue.text = TemplatesEditorSheet.countTemplates(templatesText).toString()
    }

    private val renderTemplatesRowEnabled = { enabled: Boolean ->
        binding.templatesRow.isEnabled = enabled
        binding.templatesRow.alpha = if (enabled) 1f else DISABLED_ROW_ALPHA
    }

    private fun performSave() {
        val includeMessage = binding.includeMessageSwitch.isChecked
        val templates = templatesText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        viewLifecycleOwner.lifecycleScope.launch {
            val settingsRepo = ServiceLocator.settingsRepository
            settingsRepo.setContactsPerDay(contactsPerDay)
            settingsRepo.setCooldownDays(cooldownDays)
            settingsRepo.setMessageEnabled(includeMessage)
            settingsRepo.setMessageTemplates(templates)

            // FRM-178: the time is stored by the Notifications screen;
            // reschedule from storage so the new per-day count is applied at
            // the stored time.
            ReminderTimeController.reschedule()

            showSavedPill()
        }
    }

    // FRM-130 7.4: fades in, holds, fades out - never a layout shift, since
    // settingsSavedPill is an overlay sibling in the root FrameLayout rather
    // than a LinearLayout child that others reflow around.
    private fun showSavedPill() {
        val pill = binding.settingsSavedPill
        savedPillJob?.cancel()
        pill.animate().cancel()
        pill.alpha = 0f
        pill.visibility = View.VISIBLE
        pill.animate().alpha(1f).setDuration(PILL_FADE_IN_MS).start()

        savedPillJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(PILL_VISIBLE_MS)
            pill.animate()
                .alpha(0f)
                .setDuration(PILL_FADE_OUT_MS)
                .withEndAction { pill.visibility = View.INVISIBLE }
                .start()
        }
    }

    override fun onResume() {
        super.onResume()
        EdgeToEdgeHeader.applyEdgeToEdgeHeader(this)
    }

    override fun onPause() {
        super.onPause()
        EdgeToEdgeHeader.restoreStandardStatusBar(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoSaveJob?.cancel()
        savedPillJob?.cancel()
        _binding = null
    }

    companion object {
        private const val TEMPLATES_EDITOR_REQUEST_KEY = "settings_templates_editor"
        private const val DISABLED_ROW_ALPHA = 0.38f
        private const val AUTO_SAVE_DEBOUNCE_MS = 600L
        private const val PILL_FADE_IN_MS = 150L
        private const val PILL_VISIBLE_MS = 1500L
        private const val PILL_FADE_OUT_MS = 200L

        private const val COOLDOWN_OPTION_3 = 3
        private const val COOLDOWN_OPTION_7 = 7
        private const val COOLDOWN_OPTION_14 = 14
        private val COOLDOWN_OPTIONS = intArrayOf(COOLDOWN_OPTION_3, COOLDOWN_OPTION_7, COOLDOWN_OPTION_14)
        private const val DEFAULT_COOLDOWN_DAYS = COOLDOWN_OPTION_3
        private const val COOLDOWN_PICKER_REQUEST_KEY = "settings_cooldown_picker"

        // GH #165/FRM-130: contactsPerDayRow's range - unchanged from the
        // Spinner it replaced, which offered exactly 1..5.
        private const val MIN_CONTACTS_PER_DAY = 1
        private const val MAX_CONTACTS_PER_DAY = 5
        private const val DEFAULT_CONTACTS_PER_DAY = MIN_CONTACTS_PER_DAY
        private const val CONTACTS_PER_DAY_PICKER_REQUEST_KEY = "settings_contacts_per_day_picker"

        fun newInstance(): SettingsFragment = SettingsFragment()
    }
}
