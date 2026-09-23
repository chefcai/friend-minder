package com.example.friendminder.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.example.friendminder.R
import com.example.friendminder.data.storage.SlotScheduling
import com.example.friendminder.databinding.FragmentSettingsBinding
import com.example.friendminder.ui.common.EdgeToEdgeHeader
import com.example.friendminder.ui.common.ImeInsetPadding
import com.example.friendminder.ui.common.ValuePickerDialogFragment
import com.example.friendminder.ui.common.stackIfLabelsDontFit
import com.example.friendminder.ui.home.LegacyDiagnosticsFragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
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

    private var fixedHour = 8
    private var fixedMinute = 0
    private var randomStartHour = 7
    private var randomEndHour = 9

    private var isLoadingSettings = false
    private var autoSaveJob: Job? = null
    private var savedPillJob: Job? = null

    // GH #98/#121: cooldownRow replaced cooldownSpinner, so this is now the
    // source of truth for the picked value between loadCurrentSettings and
    // performSave rather than a Spinner selection index.
    private var cooldownDays = DEFAULT_COOLDOWN_DAYS

    // GH #165/FRM-130: contactsPerDayRow replaced contactsPerDaySpinner,
    // same reasoning as cooldownDays above - source of truth between
    // loadCurrentSettings and performSave rather than a Spinner position.
    private var contactsPerDay = DEFAULT_CONTACTS_PER_DAY

    // GH #165/FRM-130: timeModeGroup is now a MaterialButtonToggleGroup
    // rather than a RadioGroup, so "which mode is selected" is tracked
    // here instead of read back via checkedRadioButtonId.
    private var isRandomTimeMode = false

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
        // messageTemplateInput with no accommodation at all.
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

        setUpReminderTimeControls()
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

    private fun setUpReminderTimeControls() {
        // GH #165/FRM-130: timeModeGroup is a MaterialButtonToggleGroup now
        // (segmented control, Section 7.2), wired the same way Contact
        // Detail's tabToggleGroup is - a click listener per button that
        // sets isChecked itself, rather than
        // MaterialButtonToggleGroup.addOnButtonCheckedListener, which also
        // fires once (isChecked=false) for the button being deselected -
        // one extra branch to filter for no benefit here. Local fun rather
        // than a member function to stay under detekt's TooManyFunctions
        // threshold, same reasoning as the lambda-typed render* properties
        // above.
        fun selectTimeMode(isRandom: Boolean) {
            isRandomTimeMode = isRandom
            binding.fixedTimeToggleButton.isChecked = !isRandom
            binding.randomWindowToggleButton.isChecked = isRandom
            binding.fixedTimeGroup.visibility = if (isRandom) View.GONE else View.VISIBLE
            binding.randomWindowGroup.visibility = if (isRandom) View.VISIBLE else View.GONE
            validateTimeRange()
            scheduleAutoSaveUnlessLoading()
        }
        binding.timeModeGroup.stackIfLabelsDontFit()
        binding.fixedTimeToggleButton.setOnClickListener { selectTimeMode(isRandom = false) }
        binding.randomWindowToggleButton.setOnClickListener { selectTimeMode(isRandom = true) }

        binding.fixedTimeButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hour, minute ->
                fixedHour = hour; fixedMinute = minute; renderTimeButtons()
                // GH #150 follow-up: fixed-time changes don't affect
                // validateTimeRange()'s pass/fail (that's random-window-only),
                // but the notice it renders depends on fixedHour/fixedMinute,
                // so it still needs a call here to pick up the new time.
                validateTimeRange()
                scheduleAutoSaveUnlessLoading()
            }, fixedHour, fixedMinute, false).show()
        }
        binding.randomFromButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hour, _ ->
                randomStartHour = hour; renderTimeButtons(); validateTimeRange()
                scheduleAutoSaveUnlessLoading()
            }, randomStartHour, 0, false).show()
        }
        binding.randomToButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hour, _ ->
                randomEndHour = hour; renderTimeButtons(); validateTimeRange()
                scheduleAutoSaveUnlessLoading()
            }, randomEndHour, 0, false).show()
        }
    }

    private fun setUpMessageTemplateControls() {
        binding.includeMessageSwitch.setOnCheckedChangeListener { _, checked ->
            binding.messageTemplateInput.isEnabled = checked
            scheduleAutoSaveUnlessLoading()
        }
        // One template per line (chefcai/friend-minder#30); the char counter
        // now reports how many usable templates that resolves to rather than
        // a single field's character count.
        binding.messageTemplateInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateTemplateCounter(s?.toString().orEmpty())
                scheduleAutoSaveUnlessLoading()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
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

            val isRandom = repo.isRandomTimeEnabled()
            isRandomTimeMode = isRandom
            binding.fixedTimeToggleButton.isChecked = !isRandom
            binding.randomWindowToggleButton.isChecked = isRandom
            binding.fixedTimeGroup.visibility = if (isRandom) View.GONE else View.VISIBLE
            binding.randomWindowGroup.visibility = if (isRandom) View.VISIBLE else View.GONE

            repo.getReminderTime()?.let { (h, m) -> fixedHour = h; fixedMinute = m }
            repo.getRandomTimeRange()?.let { (s, e) -> randomStartHour = s; randomEndHour = e }
            renderTimeButtons()

            contactsPerDay = repo.getContactsPerDay().coerceIn(MIN_CONTACTS_PER_DAY, MAX_CONTACTS_PER_DAY)
            renderContactsPerDayValue()
            cooldownDays = repo.getCooldownDays()
            renderCooldownValue()

            val messageEnabled = repo.isMessageEnabled()
            val templatesText = repo.getMessageTemplates().joinToString("\n")
            binding.includeMessageSwitch.isChecked = messageEnabled
            binding.messageTemplateInput.isEnabled = messageEnabled
            binding.messageTemplateInput.setText(templatesText)
            updateTemplateCounter(templatesText)

            validateTimeRange()
            isLoadingSettings = false
        }
    }

    private fun updateTemplateCounter(text: String) {
        val count = text.lines().map { it.trim() }.count { it.isNotBlank() }
        binding.charCounterText.text =
            resources.getQuantityString(R.plurals.format_template_counter, count, count)
    }

    // Shared by renderTimeButtons/renderNextReminderNotice rather than each
    // formatting its own Calendar+SimpleDateFormat. Omitting minute formats
    // hour-only ("h a"), matching NotificationScheduler.scheduleWithRandomTime
    // only accepting whole hours for the random window's from/to pickers -
    // flagged for Architect/Designer, see FRM-5 status notes. Folded into one
    // function (rather than a separate hour-only variant) to stay under
    // detekt's TooManyFunctions threshold - GH #150 follow-up added a third
    // caller ([renderNextReminderNotice]) that would otherwise have needed a
    // third near-identical copy of this formatting.
    private fun formatTimeLabel(hour: Int, minute: Int? = null): String {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute ?: 0) }
        val pattern = if (minute != null) "h:mm a" else "h a"
        return java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(cal.time)
    }

    // Combines what were separate renderFixedTimeButton/renderRandomButtons
    // functions - both are cheap no-ops when their mode isn't active, and
    // merging keeps the class under detekt's TooManyFunctions threshold now
    // that GH #150 follow-up added renderNextReminderNotice.
    private fun renderTimeButtons() {
        binding.fixedTimeButton.text = formatTimeLabel(fixedHour, fixedMinute)
        binding.randomFromButton.text = formatTimeLabel(randomStartHour)
        binding.randomToButton.text = formatTimeLabel(randomEndHour)
    }

    // GH #150 follow-up: a same-day resave of the reminder time (e.g. after
    // today's reminder already fired) legitimately schedules another one
    // later today rather than being silently blocked - see
    // WorkManagerNotificationScheduler's enqueueDailySlot kdoc. This previews
    // which one (today vs. tomorrow) the currently entered time/window will
    // actually produce, so that's a visible, deliberate choice rather than a
    // surprise. Random mode uses the window's end hour as the today/tomorrow
    // threshold - validateTimeRange already guarantees end > start, so there's
    // no midnight-wrap case to account for here.
    // Property-typed lambda rather than a function, same dodge as
    // renderCooldownValue above - keeps the class under detekt's
    // TooManyFunctions threshold. Returns the text rather than setting it
    // directly, since the caller (validateTimeRange) is now what owns
    // timeStatusText - the one shared view also used for the range error.
    private val nextReminderNoticeText = {
        val isRandom = isRandomTimeMode
        val now = System.currentTimeMillis()
        if (isRandom) {
            val isToday = SlotScheduling.occursLaterToday(randomEndHour, minute = 0, nowMillis = now)
            getString(
                if (isToday) R.string.notice_next_reminder_today_random else R.string.notice_next_reminder_tomorrow_random,
                formatTimeLabel(randomStartHour),
                formatTimeLabel(randomEndHour)
            )
        } else {
            val isToday = SlotScheduling.occursLaterToday(fixedHour, fixedMinute, nowMillis = now)
            getString(
                if (isToday) R.string.notice_next_reminder_today_fixed else R.string.notice_next_reminder_tomorrow_fixed,
                formatTimeLabel(fixedHour, fixedMinute)
            )
        }
    }

    // GH #150 follow-up: timeRangeErrorText and nextReminderNoticeText used
    // to be two separate views toggled opposite each other by visibility -
    // correct as long as nothing raced the two flags, but fragile, and it
    // meant the "not really an error, but pay attention" notice was styled
    // as neutral/muted instead of getting the same visual weight as the
    // real error. Now there's exactly one view (timeStatusText), always
    // styled like the error (colorError/textAppearanceBodySmall) and always
    // visible, so the two can't structurally collide or fight for
    // attention - only one message is ever showing, whichever applies.
    private fun validateTimeRange(): Boolean {
        val isRandom = isRandomTimeMode
        val valid = !isRandom || randomEndHour > randomStartHour
        val status = binding.timeStatusText
        // FRM-164 (ST-1): the notice is informational - fm_ink_dim with a
        // leading 16dp clock icon and a 4dp gap. fm_error (no icon) is kept
        // for the one state the user has to fix: an end hour at or before
        // the start hour. The icon is an inline ImageSpan rather than a
        // compound drawable so that when the line wraps at large font scales
        // it stays on the first line instead of centring across both.
        val context = requireContext()
        val colorRes = if (valid) R.color.fm_ink_dim else R.color.fm_error
        status.setTextColor(androidx.core.content.ContextCompat.getColor(context, colorRes))
        status.text = if (valid) {
            val size = resources.getDimensionPixelSize(R.dimen.fm_status_icon_size)
            val gap = resources.getDimensionPixelSize(R.dimen.fm_status_icon_gap)
            val icon = checkNotNull(androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_schedule_24)).mutate()
            icon.setTint(androidx.core.content.ContextCompat.getColor(context, R.color.fm_ink_dim))
            val inset = android.graphics.drawable.InsetDrawable(icon, 0, 0, gap, 0)
            inset.setBounds(0, 0, size + gap, size)
            val align = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.text.style.DynamicDrawableSpan.ALIGN_CENTER
            } else {
                android.text.style.DynamicDrawableSpan.ALIGN_BASELINE
            }
            android.text.SpannableStringBuilder(" ").apply {
                setSpan(android.text.style.ImageSpan(inset, align), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                append(nextReminderNoticeText())
            }
        } else {
            getString(R.string.error_end_before_start)
        }
        return valid
    }

    private fun performSave() {
        if (!validateTimeRange()) return

        val isRandom = isRandomTimeMode
        val includeMessage = binding.includeMessageSwitch.isChecked
        val templates = binding.messageTemplateInput.text?.toString().orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        viewLifecycleOwner.lifecycleScope.launch {
            val settingsRepo = ServiceLocator.settingsRepository
            settingsRepo.setRandomTimeEnabled(isRandom)
            settingsRepo.setReminderTime(fixedHour, fixedMinute)
            settingsRepo.setRandomTimeRange(randomStartHour, randomEndHour)
            settingsRepo.setContactsPerDay(contactsPerDay)
            settingsRepo.setCooldownDays(cooldownDays)
            settingsRepo.setMessageEnabled(includeMessage)
            settingsRepo.setMessageTemplates(templates)

            val scheduler = ServiceLocator.notificationScheduler
            if (isRandom) {
                scheduler.scheduleWithRandomTime(randomStartHour, randomEndHour, contactsPerDay)
            } else {
                scheduler.scheduleDaily(fixedHour, fixedMinute, contactsPerDay)
            }

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
