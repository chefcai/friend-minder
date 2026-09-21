package com.example.friendminder.ui.common

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment

/**
 * GH #132: the extended teal header (DESIGN-SYSTEM-PHASE3.md §5) - a
 * statusBarSpacer plus a fixed-height title band whose combined fill spans
 * behind the status bar with no seam - started as one copy on HomeFragment,
 * then three more independent copies of the same three functions on
 * OverallHistoryFragment and both AddContactsStep fragments.
 * OverallHistoryFragment's own kdoc already flagged this: "worth revisiting
 * once FRM-103 re-skins Groups/Settings/Contact Detail... and there are
 * five call sites doing this, not two." GH #132 brings the real count past
 * any reasonable point to keep copying, so this centralizes the three
 * behaviors here. Each call site still owns its own onViewCreated/onResume/
 * onPause wiring and layout IDs (matches this codebase's no-shared-base-
 * Fragment style - see HomeFragment's kdoc for why) - only the window/inset
 * plumbing is shared, not the lifecycle around it.
 */
object EdgeToEdgeHeader {

    /** Call from onResume: extends content behind the status bar with light (white) status-bar icons. */
    fun applyEdgeToEdgeHeader(fragment: Fragment) {
        val window = fragment.requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    /** Call from onPause: undoes [applyEdgeToEdgeHeader] so a screen without a teal header gets the normal status bar back. */
    fun restoreStandardStatusBar(fragment: Fragment) {
        val window = fragment.requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        val typedValue = TypedValue()
        if (fragment.requireContext().theme.resolveAttribute(android.R.attr.statusBarColor, typedValue, true)) {
            window.statusBarColor = typedValue.data
        }
    }

    /**
     * Call from onViewCreated: feeds the real status-bar inset into
     * [statusBarSpacer]'s height so the teal fill spans it with no seam.
     * Seeds synchronously from the current root insets first (HomeFragment's
     * GH #122 fix) so the very first frame is already correct instead of
     * waiting on the first reactive insets pass - folded into every call
     * site by centralizing here, not just Home's.
     */
    fun applyHeaderInsets(headerContainer: View, statusBarSpacer: View) {
        val updateSpacerHeight = { topInset: Int ->
            if (statusBarSpacer.layoutParams.height != topInset) {
                statusBarSpacer.layoutParams = statusBarSpacer.layoutParams.apply { height = topInset }
                statusBarSpacer.requestLayout()
            }
        }
        ViewCompat.getRootWindowInsets(headerContainer)?.let { current ->
            updateSpacerHeight(current.getInsets(WindowInsetsCompat.Type.statusBars()).top)
        }
        ViewCompat.setOnApplyWindowInsetsListener(headerContainer) { _, insets ->
            updateSpacerHeight(insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            insets
        }
        ViewCompat.requestApplyInsets(headerContainer)
    }
}
