package com.example.friendminder.ui.settings

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import com.example.friendminder.R
import com.example.friendminder.databinding.DialogTemplatesEditorBinding
import com.example.friendminder.ui.common.applyPhaseThreeSheetChrome
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * FRM-165 (ST-2): full-height editor for the message templates, one per
 * line. Settings shows only a "Templates" value row now; this sheet is
 * where the text is edited. The edited text is handed back through a
 * fragment result whenever the sheet is dismissed (Done, handle, scrim or
 * back), so no edit is lost to a dismissal path the user didn't expect to
 * be a "cancel".
 */
class TemplatesEditorSheet : BottomSheetDialogFragment() {

    private var _binding: DialogTemplatesEditorBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var currentText: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogTemplatesEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyPhaseThreeSheetChrome()
        if (savedInstanceState == null) {
            binding.templatesInput.setText(requireArguments().getString(ARG_TEXT).orEmpty())
        }
        currentText = binding.templatesInput.text?.toString().orEmpty()
        renderCounter(currentText)
        binding.templatesInput.doAfterTextChanged {
            currentText = it?.toString().orEmpty()
            renderCounter(currentText)
        }
        binding.templatesDoneButton.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        // Full height, opened expanded: the point of moving the field here
        // is room to see every template at once.
        val sheetDialog = dialog as? BottomSheetDialog ?: return
        sheetDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
        }
        sheetDialog.behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        setFragmentResult(requireArguments().getString(ARG_REQUEST_KEY).orEmpty(), bundleOf(RESULT_TEXT to currentText))
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderCounter(text: String) {
        val count = countTemplates(text)
        binding.templatesCounter.text = resources.getQuantityString(R.plurals.format_template_counter, count, count)
    }

    companion object {
        const val RESULT_TEXT = "templates_text"
        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_TEXT = "text"

        /** One template per non-blank line (chefcai/friend-minder#30). */
        fun countTemplates(text: String): Int = text.lines().count { it.isNotBlank() }

        fun newInstance(requestKey: String, text: String): TemplatesEditorSheet = TemplatesEditorSheet().apply {
            arguments = bundleOf(ARG_REQUEST_KEY to requestKey, ARG_TEXT to text)
        }
    }
}
