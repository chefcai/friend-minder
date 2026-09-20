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
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.databinding.FragmentSettingsBinding
import com.example.friendminder.ui.dashboard.DashboardFragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Reminder configuration (Designer spec §3.2). Onboarding mode is the second
 * step of first launch (no back arrow — back returns to FriendListFragment
 * via the ordinary back stack); edit mode is reachable from HomeFragment.
 * GH #56: onboarding completion now lands on DashboardFragment (the app's
 * root screen), not HomeFragment.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val isOnboarding: Boolean by lazy { requireArguments().getBoolean(ARG_ONBOARDING) }

    private var fixedHour = 8
    private var fixedMinute = 0
    private var randomStartHour = 7
    private var randomEndHour = 9

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op: see §3.3 banner for persistent denial */ }

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

        binding.toolbar.title = getString(R.string.title_settings)
        if (!isOnboarding) {
            binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            binding.toolbar.setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.contactsPerDaySpinner.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, (1..5).toList()
        )

        // Simple, locale-agnostic labels without relying on plural resources for MVP.
        val cooldownOptions = listOf(3, 7, 14)
        binding.cooldownSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            cooldownOptions.map { "$it days" }
        )

        binding.timeModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val isRandom = checkedId == binding.randomWindowRadio.id
            binding.fixedTimeGroup.visibility = if (isRandom) View.GONE else View.VISIBLE
            binding.randomWindowGroup.visibility = if (isRandom) View.VISIBLE else View.GONE
            validateTimeRange()
        }

        binding.fixedTimeButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hour, minute ->
                fixedHour = hour; fixedMinute = minute; renderFixedTimeButton()
            }, fixedHour, fixedMinute, false).show()
        }
        binding.randomFromButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hour, _ ->
                randomStartHour = hour; renderRandomButtons(); validateTimeRange()
            }, randomStartHour, 0, false).show()
        }
        binding.randomToButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hour, _ ->
                randomEndHour = hour; renderRandomButtons(); validateTimeRange()
            }, randomEndHour, 0, false).show()
        }

        binding.includeMessageCheckbox.setOnCheckedChangeListener { _, checked ->
            binding.messageTemplateInput.isEnabled = checked
        }
        // One template per line (chefcai/friend-minder#30); the char counter
        // now reports how many usable templates that resolves to rather than
        // a single field's character count.
        binding.messageTemplateInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateTemplateCounter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.saveSettingsButton.setOnClickListener { onSaveClicked() }

        loadCurrentSettings()
    }

    private fun loadCurrentSettings() {
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
        // only (minute is discarded) — flagged for Architect/Designer, see
        // FRM-5 status notes.
        binding.randomFromButton.text = hourLabel(randomStartHour)
        binding.randomToButton.text = hourLabel(randomEndHour)
    }

    private fun hourLabel(hour: Int): String {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0) }
        return java.text.SimpleDateFormat("h a", Locale.getDefault()).format(cal.time)
    }

    private fun validateTimeRange(): Boolean {
        val isRandom = binding.timeModeGroup.checkedRadioButtonId == binding.randomWindowRadio.id
        val valid = !isRandom || randomEndHour > randomStartHour
        binding.timeRangeErrorText.visibility = if (valid) View.GONE else View.VISIBLE
        binding.saveSettingsButton.isEnabled = valid
        return valid
    }

    private fun onSaveClicked() {
        if (!validateTimeRange()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.saveSettingsButton.isEnabled = false
        val isRandom = binding.timeModeGroup.checkedRadioButtonId == binding.randomWindowRadio.id
        val contactsPerDay = (binding.contactsPerDaySpinner.selectedItemPosition + 1).coerceIn(1, 5)
        val cooldownDays = listOf(3, 7, 14)[binding.cooldownSpinner.selectedItemPosition.coerceIn(0, 2)]
        val includeMessage = binding.includeMessageCheckbox.isChecked
        val templates = binding.messageTemplateInput.text?.toString().orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Captured before the coroutine's suspend points so a fast fragment
        // transition (see navigation below) can't tear down the view before
        // the confirmation toast is shown (chefcai/friend-minder#31).
        val appContext = requireContext().applicationContext

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

            Toast.makeText(appContext, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show()

            if (isOnboarding) {
                // Collapse the onboarding back-stack (FriendList -> Settings)
                // so Dashboard becomes the new root (GH #56); back from
                // Dashboard exits the app.
                parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                parentFragmentManager.commit {
                    replace(R.id.nav_host_container, DashboardFragment.newInstance())
                }
            } else {
                parentFragmentManager.popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ONBOARDING = "arg_onboarding"

        fun newInstance(isOnboarding: Boolean): SettingsFragment = SettingsFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_ONBOARDING, isOnboarding) }
        }
    }
}
