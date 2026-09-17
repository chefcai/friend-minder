package com.example.friendminder.ui.outreach

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.data.models.OutreachType
import com.example.friendminder.databinding.DialogOutreachLogBinding
import com.example.friendminder.utils.ServiceLocator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * FRM-58: manual outreach logging (PRD §6.4; SCREENS-PHASE2.md §5). Entry
 * point is ContactDetailFragment's "Log Outreach" button (Designer left the
 * exact entry point to Publisher's call — a FAB or bottom-sheet menu item
 * both satisfy "reachable in <=1 tap"; a plain button on the detail screen
 * does too, and keeps this screen's layout simpler).
 */
class OutreachLogDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogOutreachLogBinding? = null
    private val binding get() = _binding!!

    private val contactId: String by lazy { requireArguments().getString(ARG_CONTACT_ID)!! }
    private val calendar: Calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogOutreachLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.closeButton.setOnClickListener { dismiss() }
        binding.cancelButton.setOnClickListener { dismiss() }
        binding.typeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            binding.saveButton.isEnabled = checkedIds.isNotEmpty()
        }
        binding.saveButton.isEnabled = false
        binding.saveButton.setOnClickListener { save() }

        updateDateTimeButtons()
        binding.dateButton.setOnClickListener { showDatePicker() }
        binding.timeButton.setOnClickListener { showTimePicker() }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                updateDateTimeButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            // PRD §6.4 / SCREENS-PHASE2.md §5: backdating is allowed, future dates are not.
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                updateDateTimeButtons()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun updateDateTimeButtons() {
        binding.dateButton.text = DATE_FORMAT.format(calendar.time)
        binding.timeButton.text = TIME_FORMAT.format(calendar.time)
    }

    private fun selectedType(): OutreachType? = when (binding.typeChipGroup.checkedChipId) {
        binding.chipSms.id -> OutreachType.SMS
        binding.chipCall.id -> OutreachType.CALL
        binding.chipInPerson.id -> OutreachType.IN_PERSON
        binding.chipVideo.id -> OutreachType.VIDEO
        binding.chipOther.id -> OutreachType.OTHER
        else -> null
    }

    private fun save() {
        val type = selectedType() ?: return
        val note = binding.noteInput.text?.toString()?.trim().orEmpty().ifBlank { null }
        val timestamp = calendar.timeInMillis.coerceAtMost(System.currentTimeMillis())

        binding.saveButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.outreachLogService.logOutreach(contactId, type, timestamp, note)
            ServiceLocator.statisticsService.invalidate(contactId)
            setFragmentResult(RESULT_KEY, bundleOf())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "outreach_log_result"
        private const val ARG_CONTACT_ID = "arg_contact_id"
        private val DATE_FORMAT = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        private val TIME_FORMAT = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun newInstance(contactId: String): OutreachLogDialogFragment =
            OutreachLogDialogFragment().apply {
                arguments = bundleOf(ARG_CONTACT_ID to contactId)
            }
    }
}
