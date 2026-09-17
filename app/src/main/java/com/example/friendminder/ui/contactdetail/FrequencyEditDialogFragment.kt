package com.example.friendminder.ui.contactdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.databinding.DialogFrequencyEditBinding
import com.example.friendminder.utils.ServiceLocator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/** Per-contact reminder frequency override (PRD §6.2; SCREENS-PHASE2.md §4 gear icon). */
class FrequencyEditDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogFrequencyEditBinding? = null
    private val binding get() = _binding!!

    private val contactId: String by lazy { requireArguments().getString(ARG_CONTACT_ID)!! }
    private var globalDefault: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFrequencyEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.closeButton.setOnClickListener { dismiss() }
        binding.slider.addOnChangeListener { _, value, _ ->
            binding.frequencyValueText.text = getString(R.string.format_frequency_slider_value, value.toInt())
        }
        binding.resetButton.setOnClickListener { resetToDefault() }
        binding.saveButton.setOnClickListener { save() }

        viewLifecycleOwner.lifecycleScope.launch {
            globalDefault = ServiceLocator.settingsRepository.getCooldownDays()
            val override = ServiceLocator.reminderFrequencyRepository.getOverride(contactId)
            val effective = (override ?: globalDefault).coerceIn(binding.slider.valueFrom.toInt(), binding.slider.valueTo.toInt())
            binding.slider.value = effective.toFloat()
            binding.frequencyValueText.text = getString(R.string.format_frequency_slider_value, effective)
        }
    }

    private fun resetToDefault() {
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.reminderFrequencyRepository.clearOverride(contactId)
            setFragmentResult(RESULT_KEY, bundleOf())
            dismiss()
        }
    }

    private fun save() {
        val days = binding.slider.value.toInt()
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.reminderFrequencyRepository.setOverride(contactId, days)
            setFragmentResult(RESULT_KEY, bundleOf())
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "frequency_edit_result"
        private const val ARG_CONTACT_ID = "arg_contact_id"

        fun newInstance(contactId: String): FrequencyEditDialogFragment =
            FrequencyEditDialogFragment().apply {
                arguments = bundleOf(ARG_CONTACT_ID to contactId)
            }
    }
}
