package com.example.friendminder.ui.contactdetail

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.SpecialDateSource
import com.example.friendminder.databinding.FragmentContactDetailBinding
import com.example.friendminder.databinding.ItemSpecialDateBinding
import com.example.friendminder.ui.common.AvatarBinder
import com.example.friendminder.ui.outreach.OutreachLogDialogFragment
import com.example.friendminder.utils.ServiceLocator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * FRM-57: updated ContactDetailFragment (PRD §6.5, §9.3;
 * SCREENS-PHASE2.md §4). This fragment didn't exist at all before Phase 2 —
 * the MVP had no per-contact detail screen — so "updated" here means built
 * from scratch against Designer's spec, not modifying prior code.
 *
 * Reached from DashboardFragment (streak/neglected/upcoming rows) and
 * GroupDetailFragment (member rows); see those files' kdoc for why
 * FriendListFragment itself isn't a third entry point (it's a multi-select
 * picker, not a browsable list).
 */
class ContactDetailFragment : Fragment() {

    private var _binding: FragmentContactDetailBinding? = null
    private val binding get() = _binding!!

    private val contactId: String by lazy { requireArguments().getString(ARG_CONTACT_ID)!! }
    private lateinit var historyAdapter: OutreachHistoryAdapter
    private var showingHistory = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.toolbar.setOnMenuItemClickListener { onMenuItemClicked(it) }

        historyAdapter = OutreachHistoryAdapter()
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerView.adapter = historyAdapter

        binding.historyToggleButton.setOnClickListener { showHistory() }
        binding.specialDatesToggleButton.setOnClickListener { showSpecialDates() }
        showHistory()

        binding.addGroupButton.setOnClickListener {
            EditContactGroupsDialogFragment.newInstance(contactId).show(childFragmentManager, "edit_groups")
        }
        binding.logOutreachButton.setOnClickListener {
            OutreachLogDialogFragment.newInstance(contactId).show(childFragmentManager, "log_outreach")
        }
        binding.addSpecialDateButton.setOnClickListener { showAddSpecialDateDialog() }

        childFragmentManager.setFragmentResultListener(EditContactGroupsDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, _ -> refresh() }
        childFragmentManager.setFragmentResultListener(OutreachLogDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, _ -> refresh() }
        childFragmentManager.setFragmentResultListener(FrequencyEditDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, _ -> refresh() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun onMenuItemClicked(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_edit_frequency) return false
        FrequencyEditDialogFragment.newInstance(contactId).show(childFragmentManager, "edit_frequency")
        return true
    }

    private fun showHistory() {
        showingHistory = true
        binding.historyToggleButton.isChecked = true
        binding.historyRecyclerView.visibility = View.VISIBLE
        binding.logOutreachButton.visibility = View.VISIBLE
        binding.specialDatesScroll.visibility = View.GONE
        refreshHistoryEmptyState()
    }

    private fun showSpecialDates() {
        showingHistory = false
        binding.specialDatesToggleButton.isChecked = true
        binding.historyRecyclerView.visibility = View.GONE
        binding.logOutreachButton.visibility = View.GONE
        binding.historyEmptyText.visibility = View.GONE
        binding.specialDatesScroll.visibility = View.VISIBLE
    }

    private fun refreshHistoryEmptyState() {
        binding.historyEmptyText.visibility = if (historyAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            val friends = ServiceLocator.friendListRepository.getFriendList()
            val contact = friends.firstOrNull { it.id == contactId }
            if (contact == null) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
                return@launch
            }

            binding.toolbar.title = contact.name
            binding.contactNameText.text = contact.name
            AvatarBinder.bind(
                binding.contactPhoto,
                binding.contactInitial,
                contact,
                ServiceLocator.contactPhotoLoader,
                viewLifecycleOwner.lifecycleScope
            )

            bindStats(contact)
            bindGroups(contact)
            bindHistory(contact)
            bindSpecialDates(contact)
        }
    }

    private suspend fun bindStats(contact: Contact) {
        val stats = ServiceLocator.statisticsService.getStatistics(contact.id)
        val frequencyOverride = ServiceLocator.reminderFrequencyRepository.getOverride(contact.id)
        val globalDefault = ServiceLocator.settingsRepository.getCooldownDays()
        val effectiveFrequency = frequencyOverride ?: globalDefault

        val birthday = ServiceLocator.birthdayService.getSpecialDatesForContact(contact.id)
            .firstOrNull { it.source == SpecialDateSource.CONTACTS }
        val frequencyText = if (frequencyOverride != null) {
            getString(R.string.format_frequency_custom, effectiveFrequency)
        } else {
            getString(R.string.format_frequency_default, effectiveFrequency)
        }
        binding.birthdayFrequencyCaption.text = if (birthday != null) {
            getString(R.string.format_special_date_row, formatMonthDay(birthday.month, birthday.day), frequencyText)
        } else {
            frequencyText
        }

        binding.lastContactText.text = getString(R.string.format_last_contact, formatDaysAgo(stats.daysSinceContact))
        binding.streakText.text = resources.getQuantityString(R.plurals.format_streak_days, stats.streak, stats.streak)
        binding.reachRateText.text = getString(R.string.format_reach_rate, stats.reachRate)
    }

    private suspend fun bindGroups(contact: Contact) {
        val groups = ServiceLocator.groupService.getGroupsForContact(contact.id)
        binding.groupsChipGroup.removeAllViews()
        groups.forEach { group ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = group.name
                isCloseIconVisible = true
                setCloseIconResource(R.drawable.ic_close)
                setOnCloseIconClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        ServiceLocator.groupService.removeContactFromGroup(contact.id, group.id)
                        refresh()
                    }
                }
            }
            binding.groupsChipGroup.addView(chip)
        }
    }

    private fun bindHistory(contact: Contact) {
        viewLifecycleOwner.lifecycleScope.launch {
            val history = ServiceLocator.outreachLogService.getHistory(contact.id)
            // ListAdapter.submitList() diffs off the main thread and applies the
            // result asynchronously, so itemCount right after this call can still
            // reflect the *previous* list (GH #81): calling refreshHistoryEmptyState()
            // here was a race that usually - but not always - lost, leaving the
            // empty-state text stuck over newly-logged rows until some other
            // trigger (e.g. reopening the screen) called it again after the diff
            // had caught up. The commit callback runs once the diff is actually
            // applied, so itemCount is accurate every time.
            historyAdapter.submitList(history) { refreshHistoryEmptyState() }
        }
    }

    private fun bindSpecialDates(contact: Contact) {
        viewLifecycleOwner.lifecycleScope.launch {
            val dates = ServiceLocator.birthdayService.getSpecialDatesForContact(contact.id)
            binding.specialDatesContainer.removeAllViews()
            binding.specialDatesEmptyText.visibility = if (dates.isEmpty()) View.VISIBLE else View.GONE
            dates.sortedWith(compareBy({ it.month }, { it.day })).forEach { date ->
                val itemBinding = ItemSpecialDateBinding.inflate(layoutInflater, binding.specialDatesContainer, false)
                itemBinding.specialDateLabel.text = getString(
                    R.string.format_special_date_row, date.label, formatMonthDay(date.month, date.day)
                )
                itemBinding.specialDateReminder.text = reminderLabel(date.reminderDaysBefore)
                itemBinding.deleteSpecialDateButton.visibility =
                    if (date.source == SpecialDateSource.CUSTOM) View.VISIBLE else View.GONE
                itemBinding.deleteSpecialDateButton.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        ServiceLocator.birthdayService.removeSpecialDate(date.id)
                        refresh()
                    }
                }
                binding.specialDatesContainer.addView(itemBinding.root)
            }
        }
    }

    private fun reminderLabel(daysBefore: Int): String = when (daysBefore) {
        0 -> getString(R.string.label_reminder_day_of)
        -1 -> getString(R.string.label_reminder_1_day_before)
        -DAYS_IN_WEEK -> getString(R.string.label_reminder_1_week_before)
        else -> getString(R.string.label_reminder_day_of)
    }

    private fun formatDaysAgo(days: Int?): String = when {
        days == null -> getString(R.string.label_never_contacted)
        days <= 0 -> getString(R.string.label_today)
        else -> resources.getQuantityString(R.plurals.format_days_ago, days, days)
    }

    private fun formatMonthDay(month: Int, day: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        return MONTH_DAY_FORMAT.format(calendar.time)
    }

    private fun showAddSpecialDateDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_special_date, null)
        val labelInput = dialogView.findViewById<TextInputEditText>(R.id.labelInput)
        val pickDateButton = dialogView.findViewById<android.widget.Button>(R.id.pickDateButton)
        val reminderGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.reminderRadioGroup)

        val calendar = Calendar.getInstance()
        updatePickDateButtonText(pickDateButton, calendar)
        pickDateButton.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, day)
                    updatePickDateButtonText(pickDateButton, calendar)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_add_special_date)
            .setView(dialogView)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val label = labelInput.text?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@setPositiveButton
                val reminderDaysBefore = when (reminderGroup.checkedRadioButtonId) {
                    R.id.radioOneDayBefore -> -1
                    R.id.radioOneWeekBefore -> -DAYS_IN_WEEK
                    else -> 0
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    ServiceLocator.birthdayService.addCustomSpecialDate(
                        contactId = contactId,
                        label = label,
                        month = calendar.get(Calendar.MONTH) + 1,
                        day = calendar.get(Calendar.DAY_OF_MONTH),
                        reminderDaysBefore = reminderDaysBefore
                    )
                    refresh()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun updatePickDateButtonText(button: android.widget.Button, calendar: Calendar) {
        button.text = MONTH_DAY_FORMAT.format(calendar.time)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT_ID = "arg_contact_id"
        private const val DAYS_IN_WEEK = 7
        private val MONTH_DAY_FORMAT = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())

        fun newInstance(contactId: String): ContactDetailFragment =
            ContactDetailFragment().apply {
                arguments = bundleOf(ARG_CONTACT_ID to contactId)
            }
    }
}
