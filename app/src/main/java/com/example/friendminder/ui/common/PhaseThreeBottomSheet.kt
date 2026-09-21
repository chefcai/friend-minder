package com.example.friendminder.ui.common

import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Shared Phase 3 dialog chrome (SCREENS-PHASE3.md §8.1): every dialog is a
 * `fm_surface` bottom sheet with 16dp top corners and a 4x32dp drag handle
 * 12dp from the top, dismissed only by the handle, the scrim, or back - no
 * `✕`, no separate Cancel button alongside the primary action.
 *
 * Material's default [com.google.android.material.bottomsheet.BottomSheetDialog]
 * paints its own opaque, square-cornered background behind whatever the
 * fragment inflates, so that default has to be made transparent for a
 * content layout's own rounded-corner background
 * ([com.example.friendminder.R.drawable.bg_bottom_sheet_surface]) to show
 * through instead of being boxed in by a square sheet around it. One place
 * for that swap rather than repeating it per dialog fragment - introduced
 * here for FRM-102's two rebuilt dialogs (frequency, group-assignment);
 * FRM-103's dialog pass should call this rather than re-solving it per
 * screen.
 *
 * Call from `onViewCreated`, after the dialog's view hierarchy exists.
 */
fun BottomSheetDialogFragment.applyPhaseThreeSheetChrome() {
    dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        ?.setBackgroundResource(android.R.color.transparent)
}
