package com.example.friendminder.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.friendminder.R
import com.example.friendminder.databinding.SheetFmBaseBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * FRM-172 (DL-1): the shared bottom-sheet base (DESIGN-SYSTEM Section 6.6).
 *
 * Every sheet gets the same chrome from sheet_fm_base.xml: fm_surface with
 * 16dp top corners, a 4x32dp handle, a 24dp gutter, a Screen Heading
 * title, and at most one full-width 56dp primary. Dismissal is the handle,
 * the scrim (fm_ink at 40%) or back - there is no close X and no Cancel.
 *
 * Subclasses supply the title and content; a sheet with a primary also
 * supplies [primaryLabel] and [onPrimaryClick]. Pickers leave
 * [primaryLabel] null and keep tap-to-select-and-dismiss.
 */
abstract class FmBottomSheet : BottomSheetDialogFragment() {

    private var _sheet: SheetFmBaseBinding? = null
    protected val sheet: SheetFmBaseBinding get() = checkNotNull(_sheet)

    /** The sheet title, sentence case. */
    protected abstract fun sheetTitle(): CharSequence

    /** Inflate the sheet body into [parent] (do not attach); returned view is added by the base. */
    protected abstract fun onCreateSheetContent(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?): View

    /** Label for the single primary button, or null for a picker (no button). */
    protected open fun primaryLabel(): CharSequence? = null

    protected open fun onPrimaryClick() = Unit

    /** True for sheets that should open expanded at full height with the content filling it. */
    protected open val isFullHeight: Boolean = false

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val base = SheetFmBaseBinding.inflate(inflater, container, false)
        _sheet = base
        base.sheetTitle.text = sheetTitle()
        base.sheetContent.addView(onCreateSheetContent(inflater, base.sheetContent, savedInstanceState))
        val label = primaryLabel()
        if (label != null) {
            base.sheetPrimaryButton.visibility = View.VISIBLE
            base.sheetPrimaryButton.text = label
            base.sheetPrimaryButton.setOnClickListener { onPrimaryClick() }
        }
        if (isFullHeight) {
            base.root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            base.sheetContent.layoutParams = (base.sheetContent.layoutParams as LinearLayout.LayoutParams).apply {
                height = 0
                weight = 1f
            }
        }
        return base.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyPhaseThreeSheetChrome()
    }

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? BottomSheetDialog ?: return
        // The scrim is fm_ink at 40% rather than the platform's black dim.
        sheetDialog.window?.setDimAmount(0f)
        sheetDialog.findViewById<View>(com.google.android.material.R.id.touch_outside)
            ?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.fm_scrim))
        if (isFullHeight) {
            sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
                bottomSheet.layoutParams = bottomSheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            }
            sheetDialog.behavior.skipCollapsed = true
            sheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    /**
     * Enables or disables the primary. A disabled primary must say why
     * (DESIGN-SYSTEM Section 6.6), so [reasonWhenDisabled] is required
     * whenever [enabled] is false and is shown directly above the button.
     */
    protected fun setPrimaryEnabled(enabled: Boolean, reasonWhenDisabled: CharSequence? = null) {
        require(enabled || !reasonWhenDisabled.isNullOrBlank()) { "A disabled primary needs helper text saying why" }
        sheet.sheetPrimaryButton.isEnabled = enabled
        sheet.sheetPrimaryHelper.text = if (enabled) null else reasonWhenDisabled
        sheet.sheetPrimaryHelper.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    /** Hides the primary (and its helper) entirely, e.g. on an empty state with nothing to act on. */
    protected fun setPrimaryVisible(visible: Boolean) {
        sheet.sheetPrimaryButton.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) sheet.sheetPrimaryHelper.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _sheet = null
    }
}
