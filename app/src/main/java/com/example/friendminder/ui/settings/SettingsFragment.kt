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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.databinding.FragmentSettingsBinding
import com.example.friendminder.ui.common.EdgeToEdgeHeader
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

        binding.contactsPerDaySpinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, (1..5).toList()
        )
        binding.contactsPerDaySpinner.onItemSelectedListener = autoSaveOnItemSelected()

        // Simple, locale-agnostic labels without relying on plural resources for MVP.
        val cooldownOptions = listOf(3, 7, 14)
        binding.cooldownSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            cooldownOptions.map { "$it days" }
        )
        binding.cooldownSpinner.onItemSelectedListener = autoSaveOnItemSelected()

        setUpReminderTimeControls()
        setUpMessageTemplateControls()
        setUpNavigationRows()

        ensureNotificationPermission()
        loadCurrentSettings()
    }

    // A Spinner's OnItemSelectedListener fires once as soon as it's attached
    // to an already-populated adapter, not only on a genuine user pick - the
    // isLoadingSettings guard is what keeps that initial fire silent.
    private fun autoSaveOnItemSelected() = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            scheduleAutoSaveUnlessLoading()
        }
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    private fun setUpReminderTimeControls() {
        binding.timeModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val isRandom = checkedId == binding.randomWindowRadio.id
            binding.fixedTimeGroup.visibility = if (isRandom) View.GONE else View.VISIBLE
            binding.randomWindowGroup.visibility = if (isRandom) View.VISIBLE else View.GONE
            validateTimeRange()
            scheduleAutoSaveUnlessLoading()
        }

        binding.fixedTimeButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hour, minute ->
                fixedHour = hour; fixedMinute = minute; renderFixedTimeButton()
                scheduleAutoSaveUnlessLoading()
            }, fixedHour, fixedMinute, false).show()
        }
        binding.randomFromButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hour, _ ->
                randomStartHour = hour; renderRandomButtons(); validateTimeRange()
                scheduleAutoSaveUnlessLoading()
            }, randomStartHour, 0, false).show()
        }
        binding.randomToButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hour, _ ->
                randomEndHour = hour; renderRandomButtons(); validateTimeRange()
                scheduleAutoSaveUnlessLoading()
            }, randomEndHour, 0, false).show()
        }
    }

    private fun setUpMessageTemplateControls() {
        binding.includeMessageCheckbox.setOnCheckedChangeListener { _, checked ->
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
            binding.timeModeGroup.check(
                if (isRandom) binding.randomWindowRadio.id else binding.fixedTimeRadio.id
            )
            binding.fixedTimeGroup.visibility = if (isRandom) View.GONE else View.VISIBLE
            binding.randomWindowGroup.visibility = if (isRandom) View.VISIBLE else View.GONE

            repo.getReminderTime()?.let { (h, m) -> fixedHour = h; fixedMinute = m }
            repo.getRandomTimeRange()?.let { (s, e) -> randomStartHour = s; randomEndHour = e }
            renderFixedTimeButton()
            renderRandomButtons()

            binding.contactsPerDaySpinner.setSelection((repo.getContactsPerDay() - 1).coerceIn(0, 4))
            val cooldown = repo.getCooldownDays()
            val cooldownIndex = listOf(3, 7, 14).indexOf(cooldown).let { if (it < 0) 0 else it }
            binding.cooldownSpinner.setSelection(cooldownIndex)

            val messageEnabled = repo.isMessageEnabled()
            val templatesText = repo.getMessageTemplates().joinToString("\n")
            binding.includeMessageCheckbox.isChecked = messageEnabled
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

    private fun renderFixedTimeButton() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, fixedHour); set(Calendar.MINUTE, fixedMinute)
        }
        binding.fixedTimeButton.text =
            java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
    }

    private fun renderRandomButtons() {
        // NotificationScheduler.scheduleWithRandomTime only accepts whole
        // hours, so these pickers intentionally show/store hour granularity
        // only (minute is discarded) - flagged for Architect/Designer, see
        // FRM-5 status notes. hourLabel folded into a local lambda (only
        // used here) to make room for GH #132's onResume/onPause under
        // detekt's TooManyFunctions threshold.
        val hourLabel = { hour: Int ->
            val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0) }
            java.text.SimpleDateFormat("h a", Locale.getDefault()).format(cal.time)
        }
        binding.randomFromButton.text = hourLabel(randomStartHour)
        binding.randomToButton.text = hourLabel(randomEndHour)
    }

    private fun validateTimeRange(): Boolean {
        val isRandom = binding.timeModeGroup.checkedRadioButtonId == binding.randomWindowRadio.id
        val valid = !isRandom || randomEndHour > randomStartHour
        binding.timeRangeErrorText.visibility = if (valid) View.GONE else View.VISIBLE
        return valid
    }

    private fun performSave() {
        if (!validateTimeRange()) return

        val isRandom = binding.timeModeGroup.checkedRadioButtonId == binding.randomWindowRadio.id
        val contactsPerDay = (binding.contactsPerDaySpinner.selectedItemPosition + 1).coerceIn(1, 5)
        val cooldownDays = listOf(3, 7, 14)[binding.cooldownSpinner.selectedItemPosition.coerceIn(0, 2)]
        val includeMessage = binding.includeMessageCheckbox.isChecked
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

        fun newInstance(): SettingsFragment = SettingsFragment()
    }
}
