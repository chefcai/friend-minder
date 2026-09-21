package com.example.friendminder.ui.common

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.setFragmentResult
import com.example.friendminder.R
import com.example.friendminder.databinding.DialogValuePickerBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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
 *
 * GH #98/#121: [allowCustomDays] turns on this sheet's one exception to
 * "tap an option, sheet closes" - a final "Custom..." row that swaps the
 * option list for a numeric entry field **in the same sheet** rather than
 * stacking a second one (SCREENS-PHASE3.md §8.4). It's opt-in because two
 * of this component's existing callers aren't day-count pickers at all -
 * [com.example.friendminder.ui.addcontacts.AddContactsStep1Fragment]'s
 * "which phone number" sheet has no business offering to type a custom
 * phone number. [warningText], when non-null, is GH #121/§6.2a's sheet
 * line ("Close Friends checks in every 3 days...") - it is independent of
 * [allowCustomDays] and independent of which container is showing, since
 * the spec places it "beneath the sheet title" rather than beneath either
 * the option list or the custom-entry field specifically.
 */
class ValuePickerDialogFragment : BottomSheetDialogFragment() {

    // GH #98/#121: grouped into one object rather than four trailing
    // newInstance params (allowCustomDays/customMin/customMax/
    // warningText) - with them inline, newInstance tripped detekt's
    // LongParameterList at 9 arguments. null means "not an interval
    // picker" (§8.4's Custom... row/field, and the min/max/warning that go
    // with it, only make sense for a picker of day counts - see the class
    // kdoc). Declared here rather than inside the companion object: Kotlin
    // only resolves the plain ValuePickerDialogFragment.CustomEntryOptions(...)
    // callers use for a class nested directly in this class, not one
    // nested inside its companion object (that would need
    // ValuePickerDialogFragment.Companion.CustomEntryOptions instead).
    data class CustomEntryOptions(
        val min: Int = DEFAULT_CUSTOM_MIN,
        val max: Int = DEFAULT_CUSTOM_MAX,
        val warningText: String? = null
    )

    private var _binding: DialogValuePickerBinding? = null
    private val binding get() = _binding!!

    private var behavior: BottomSheetBehavior<View>? = null
    private var showingCustomEntry = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        // §8.4: "back, the handle and the scrim return to the option list
        // rather than dismissing the sheet" while the custom-entry field is
        // showing. BottomSheetDialogFragment routes all three of those
        // through the same STATE_HIDDEN transition on its behavior, so
        // catching it here (rather than three separate listeners for back/
        // scrim/swipe) covers all three at once.
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            val sheetBehavior = sheet?.let { BottomSheetBehavior.from(it) } ?: return@setOnShowListener
            behavior = sheetBehavior
            sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheetView: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN && showingCustomEntry) {
                        showOptionList()
                        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }

                override fun onSlide(bottomSheetView: View, slideOffset: Float) = Unit
            })
        }
        return dialog
    }

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

        val args = requireArguments()
        val requestKey = args.getString(ARG_REQUEST_KEY)!!
        val titleText = args.getString(ARG_TITLE)!!
        val values = args.getIntArray(ARG_VALUES)!!
        val labels = args.getStringArrayList(ARG_LABELS)!!
        val selectedValue = args.getInt(ARG_SELECTED_VALUE)
        val allowCustomDays = args.getBoolean(ARG_ALLOW_CUSTOM_DAYS)
        val customMin = args.getInt(ARG_CUSTOM_MIN)
        val customMax = args.getInt(ARG_CUSTOM_MAX)
        val warningText = args.getString(ARG_WARNING_TEXT)

        binding.dialogTitle.text = titleText
        binding.warningText.text = warningText
        binding.warningText.visibility = if (warningText.isNullOrEmpty()) View.GONE else View.VISIBLE

        buildOptionList(requestKey, values, labels, selectedValue, allowCustomDays)
        if (allowCustomDays) {
            setUpCustomEntry(requestKey, customMin, customMax)
        }
    }

    private fun buildOptionList(
        requestKey: String,
        values: IntArray,
        labels: List<String>,
        selectedValue: Int,
        allowCustomDays: Boolean
    ) {
        binding.optionContainer.removeAllViews()

        val density = resources.displayMetrics.density
        val metrics = RowMetrics(
            rowHeightPx = (ROW_MIN_HEIGHT_DP * density).toInt(),
            gutterPx = (GUTTER_DP * density).toInt(),
            checkSizePx = (CHECK_SIZE_DP * density).toInt()
        )

        // §8.4: "a custom value already in force appears as a marked
        // option at the top of the list next time the sheet opens, so it
        // is one tap to keep." A value already present in the preset list
        // isn't custom - it's just the currently-selected preset - so this
        // only fires when selectedValue is a genuine one-off.
        if (allowCustomDays && selectedValue !in values) {
            addOptionRow(
                requestKey = requestKey,
                value = selectedValue,
                label = resources.getQuantityString(R.plurals.format_days_option, selectedValue, selectedValue),
                isSelected = true,
                metrics = metrics
            )
        }

        values.forEachIndexed { index, value ->
            addOptionRow(
                requestKey = requestKey,
                value = value,
                label = labels[index],
                isSelected = value == selectedValue,
                metrics = metrics
            )
        }

        if (allowCustomDays) {
            addDivider(metrics.gutterPx)
            addCustomRow(metrics)
        }
    }

    private data class RowMetrics(val rowHeightPx: Int, val gutterPx: Int, val checkSizePx: Int)

    private fun addOptionRow(requestKey: String, value: Int, label: String, isSelected: Boolean, metrics: RowMetrics) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = metrics.rowHeightPx
            setPadding(metrics.gutterPx, 0, metrics.gutterPx, 0)
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_home_row_ripple)
            setOnClickListener {
                setFragmentResult(requestKey, bundleOf(RESULT_VALUE to value))
                dismiss()
            }
        }

        val labelView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = label
            textSize = 17f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.fm_ink))
        }
        row.addView(labelView)

        val check = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(metrics.checkSizePx, metrics.checkSizePx)
            setImageResource(R.drawable.ic_check)
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        }
        row.addView(check)

        binding.optionContainer.addView(row)
    }

    // §8.4: "set apart from the options above it by a 1dp fm_divider
    // hairline inset 24dp" - inset by the same gutter the rows themselves
    // pad with, so the hairline lines up with each row's text rather than
    // running edge to edge like every other divider in the app.
    private fun addDivider(gutterPx: Int) {
        val divider = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * resources.displayMetrics.density).toInt()
            ).apply { marginStart = gutterPx }
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.fm_divider))
        }
        binding.optionContainer.addView(divider)
    }

    private fun addCustomRow(metrics: RowMetrics) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = metrics.rowHeightPx
            setPadding(metrics.gutterPx, 0, metrics.gutterPx, 0)
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_home_row_ripple)
            setOnClickListener { showCustomEntry() }
        }
        // No radio mark: §8.4 - "it has no radio mark, because it is not a
        // value."
        val labelView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = getString(R.string.label_custom_option)
            textSize = 17f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.fm_ink))
        }
        row.addView(labelView)
        binding.optionContainer.addView(row)
    }

    private fun setUpCustomEntry(requestKey: String, customMin: Int, customMax: Int) {
        binding.customDaysInputLayout.helperText = getString(R.string.format_custom_days_range, customMin, customMax)
        binding.customDaysSetButton.isEnabled = false
        // Deliberately not pre-filled: the marked row at the top of the
        // option list (buildOptionList) is already §8.4's "keep it in one
        // tap" path for a custom value already in force. Pre-filling the
        // field here too would make Custom... look like an edit-in-place
        // control instead of a fresh entry point.
        binding.customDaysInput.doOnTextChanged { text, _, _, _ ->
            binding.customDaysSetButton.isEnabled = parseCustomDays(text?.toString(), customMin, customMax) != null
        }
        binding.customDaysSetButton.setOnClickListener {
            val days = parseCustomDays(binding.customDaysInput.text?.toString(), customMin, customMax) ?: return@setOnClickListener
            setFragmentResult(requestKey, bundleOf(RESULT_VALUE to days))
            dismiss()
        }
    }

    private fun parseCustomDays(text: String?, min: Int, max: Int): Int? {
        val days = text?.trim()?.toIntOrNull() ?: return null
        return days.takeIf { it in min..max }
    }

    private fun showCustomEntry() {
        showingCustomEntry = true
        binding.optionContainer.visibility = View.GONE
        binding.customEntryContainer.visibility = View.VISIBLE
    }

    private fun showOptionList() {
        showingCustomEntry = false
        binding.customEntryContainer.visibility = View.GONE
        binding.optionContainer.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        behavior = null
        _binding = null
    }

    companion object {
        const val RESULT_VALUE = "value_picker_result_value"

        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_VALUES = "arg_values"
        private const val ARG_LABELS = "arg_labels"
        private const val ARG_SELECTED_VALUE = "arg_selected_value"
        private const val ARG_ALLOW_CUSTOM_DAYS = "arg_allow_custom_days"
        private const val ARG_CUSTOM_MIN = "arg_custom_min"
        private const val ARG_CUSTOM_MAX = "arg_custom_max"
        private const val ARG_WARNING_TEXT = "arg_warning_text"

        private const val ROW_MIN_HEIGHT_DP = 48
        private const val GUTTER_DP = 24
        private const val CHECK_SIZE_DP = 24

        private const val DEFAULT_CUSTOM_MIN = 1
        private const val DEFAULT_CUSTOM_MAX = 30

        // options is (value, label) pairs rather than separate values/
        // labels arrays - detekt's LongParameterList still flagged this
        // function at 6 params after CustomEntryOptions folded the other
        // four together, so value+label fold together too.
        fun newInstance(
            requestKey: String,
            title: String,
            options: List<Pair<Int, String>>,
            selectedValue: Int,
            customEntry: CustomEntryOptions? = null
        ): ValuePickerDialogFragment = ValuePickerDialogFragment().apply {
            arguments = bundleOf(
                ARG_REQUEST_KEY to requestKey,
                ARG_TITLE to title,
                ARG_VALUES to options.map { it.first }.toIntArray(),
                ARG_LABELS to ArrayList(options.map { it.second }),
                ARG_SELECTED_VALUE to selectedValue,
                ARG_ALLOW_CUSTOM_DAYS to (customEntry != null),
                ARG_CUSTOM_MIN to (customEntry?.min ?: DEFAULT_CUSTOM_MIN),
                ARG_CUSTOM_MAX to (customEntry?.max ?: DEFAULT_CUSTOM_MAX),
                ARG_WARNING_TEXT to customEntry?.warningText
            )
        }
    }
}
