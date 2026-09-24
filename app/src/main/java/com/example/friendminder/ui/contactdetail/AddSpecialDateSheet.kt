package com.example.friendminder.ui.contactdetail

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import com.example.friendminder.R
import com.example.friendminder.databinding.DialogAddSpecialDateBinding
import com.example.friendminder.ui.common.FmBottomSheet
import com.example.friendminder.ui.common.ValuePickerDialogFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * FRM-173 (DL-1): "Add special date" on the shared [FmBottomSheet],
 * replacing the stock MaterialAlertDialog (its text-button Save measured
 * 3.81:1 on #ECE6F0). A label field, then Date and "Remind me" value rows
 * each opening a picker, then the full-width "Save date" primary - which
 * stays disabled, saying why, until there is a label.
 *
 * The sheet only collects the values; [RESULT_KEY] hands them back to
 * Contact Detail, which saves through BirthdayService as before.
 */
class AddSpecialDateSheet : FmBottomSheet() {

    private var _binding: DialogAddSpecialDateBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val calendar: Calendar = Calendar.getInstance()
    private var reminderDaysBefore: Int = REMIND_DAY_OF

    override fun sheetTitle(): CharSequence = getString(R.string.action_add_special_date)

    override fun primaryLabel(): CharSequence = getString(R.string.action_save_special_date)

    override fun onCreateSheetContent(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?): View {
        _binding = DialogAddSpecialDateBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savedInstanceState?.let {
            calendar.timeInMillis = it.getLong(STATE_DATE_MILLIS, calendar.timeInMillis)
            reminderDaysBefore = it.getInt(STATE_REMINDER, REMIND_DAY_OF)
        }
        renderValues()
        renderSaveEnabled()
        binding.labelInput.doAfterTextChanged { renderSaveEnabled() }

        binding.dateRow.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    renderValues()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        binding.remindRow.setOnClickListener {
            ValuePickerDialogFragment.newInstance(
                requestKey = REMIND_PICKER_REQUEST_KEY,
                title = getString(R.string.label_special_date_remind_me),
                options = REMIND_OPTIONS.map { it to reminderLabel(it) },
                selectedValue = reminderDaysBefore
            ).show(childFragmentManager, "special_date_remind_picker")
        }
        childFragmentManager.setFragmentResultListener(REMIND_PICKER_REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            reminderDaysBefore = bundle.getInt(ValuePickerDialogFragment.RESULT_VALUE)
            renderValues()
        }
    }

    override fun onPrimaryClick() {
        val label = binding.labelInput.text?.toString()?.trim().orEmpty()
        if (label.isBlank()) return
        setFragmentResult(
            RESULT_KEY,
            bundleOf(
                RESULT_LABEL to label,
                RESULT_MONTH to calendar.get(Calendar.MONTH) + 1,
                RESULT_DAY to calendar.get(Calendar.DAY_OF_MONTH),
                RESULT_REMINDER_DAYS_BEFORE to reminderDaysBefore
            )
        )
        dismiss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_DATE_MILLIS, calendar.timeInMillis)
        outState.putInt(STATE_REMINDER, reminderDaysBefore)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderValues() {
        binding.dateValue.text = SimpleDateFormat("MMM d", Locale.getDefault()).format(calendar.time)
        binding.remindValue.text = reminderLabel(reminderDaysBefore)
    }

    private fun renderSaveEnabled() {
        val hasLabel = !binding.labelInput.text?.toString().isNullOrBlank()
        setPrimaryEnabled(hasLabel, getString(R.string.helper_special_date_needs_label))
    }

    private fun reminderLabel(daysBefore: Int): String = getString(
        when (daysBefore) {
            REMIND_ONE_DAY_BEFORE -> R.string.label_reminder_1_day_before
            REMIND_ONE_WEEK_BEFORE -> R.string.label_reminder_1_week_before
            else -> R.string.label_reminder_day_of
        }
    )

    companion object {
        const val RESULT_KEY = "add_special_date_result"
        const val RESULT_LABEL = "label"
        const val RESULT_MONTH = "month"
        const val RESULT_DAY = "day"
        const val RESULT_REMINDER_DAYS_BEFORE = "reminder_days_before"

        // Same encoding the old dialog passed to BirthdayService: 0, -1, -7.
        private const val REMIND_DAY_OF = 0
        private const val REMIND_ONE_DAY_BEFORE = -1
        private const val REMIND_ONE_WEEK_BEFORE = -7
        private val REMIND_OPTIONS = listOf(REMIND_DAY_OF, REMIND_ONE_DAY_BEFORE, REMIND_ONE_WEEK_BEFORE)
        private const val REMIND_PICKER_REQUEST_KEY = "special_date_remind_picker"
        private const val STATE_DATE_MILLIS = "date_millis"
        private const val STATE_REMINDER = "reminder"

        fun newInstance(): AddSpecialDateSheet = AddSpecialDateSheet()
    }
}
