package com.example.friendminder.ui.common

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.example.friendminder.R
import com.example.friendminder.databinding.DialogValuePickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Generic single-value picker sheet (SCREENS-PHASE3.md §8.1/§8.4): "a list
 * of options with the current one marked ... tapping an option selects and
 * dismisses." No confirm button, no cancel button - dismissal is the
 * handle, the scrim, and back, same as every other Phase 3 dialog.
 *
 * Deliberately dumb: it knows nothing about frequencies or repositories,
 * only a title, a list of (value, label) options, and which value is
 * currently selected. The caller decides what a selection means -
 * [com.example.friendminder.ui.contactdetail.ContactDetailFragment.showFrequencyPicker]
 * writes it straight to [com.example.friendminder.data.storage.ReminderFrequencyRepository];
 * the add-contact flow's Step 2 just holds it in memory until contacts are
 * created. That split is what lets one component serve both call sites
 * (SCREENS-PHASE3.md §9.4) without either one leaking into this class.
 *
 * Options are passed as parallel int/String arrays rather than a richer
 * type, since a [Bundle] can't carry an arbitrary data class list without
 * Parcelable boilerplate for what is, today, a short list of day counts.
 */
class ValuePickerDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogValuePickerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogValuePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyPhaseThreeSheetChrome()

        val args = requireArguments()
        val requestKey = args.getString(ARG_REQUEST_KEY)!!
        val titleText = args.getString(ARG_TITLE)!!
        val values = args.getIntArray(ARG_VALUES)!!
        val labels = args.getStringArrayList(ARG_LABELS)!!
        val selectedValue = args.getInt(ARG_SELECTED_VALUE)

        binding.dialogTitle.text = titleText

        val density = resources.displayMetrics.density
        val rowHeightPx = (ROW_MIN_HEIGHT_DP * density).toInt()
        val gutterPx = (GUTTER_DP * density).toInt()
        val checkSizePx = (CHECK_SIZE_DP * density).toInt()

        values.forEachIndexed { index, value ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = rowHeightPx
                setPadding(gutterPx, 0, gutterPx, 0)
                isClickable = true
                isFocusable = true
                background = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_home_row_ripple)
                setOnClickListener {
                    setFragmentResult(requestKey, bundleOf(RESULT_VALUE to value))
                    dismiss()
                }
            }

            val label = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = labels[index]
                textSize = 17f
                setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.fm_ink))
            }
            row.addView(label)

            val check = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(checkSizePx, checkSizePx)
                setImageResource(R.drawable.ic_check)
                contentDescription = null
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                visibility = if (value == selectedValue) View.VISIBLE else View.INVISIBLE
            }
            row.addView(check)

            binding.optionContainer.addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_VALUE = "value_picker_result_value"

        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_VALUES = "arg_values"
        private const val ARG_LABELS = "arg_labels"
        private const val ARG_SELECTED_VALUE = "arg_selected_value"

        private const val ROW_MIN_HEIGHT_DP = 48
        private const val GUTTER_DP = 24
        private const val CHECK_SIZE_DP = 24

        fun newInstance(
            requestKey: String,
            title: String,
            values: IntArray,
            labels: List<String>,
            selectedValue: Int
        ): ValuePickerDialogFragment = ValuePickerDialogFragment().apply {
            arguments = bundleOf(
                ARG_REQUEST_KEY to requestKey,
                ARG_TITLE to title,
                ARG_VALUES to values,
                ARG_LABELS to ArrayList(labels),
                ARG_SELECTED_VALUE to selectedValue
            )
        }
    }
}
