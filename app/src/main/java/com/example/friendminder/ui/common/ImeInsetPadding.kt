package com.example.friendminder.ui.common

import android.graphics.Rect
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * GH #153: screens that opt into the extended teal header
 * ([EdgeToEdgeHeader.applyEdgeToEdgeHeader]) call
 * WindowCompat.setDecorFitsSystemWindows(window, false) while they're
 * visible, which hands the app full responsibility for every system inset
 * on that screen - including the keyboard's. Nothing ever consumed the IME
 * inset, so MainActivity's windowSoftInputMode="adjustResize" had nothing
 * to resize on those screens specifically: the window's content area never
 * shrank when the keyboard opened, and it just drew over whatever was
 * focused (confirmed via uiautomator - the affected ScrollView's measured
 * bounds were bit-for-bit identical before and after the keyboard opened).
 *
 * A screen that keeps the platform's normal decorFitsSystemWindows(true)
 * behavior doesn't need this - adjustResize alone already shrinks its
 * window for the keyboard. This is only for the edge-to-edge screens that
 * opted out of that automatic handling.
 */
object ImeInsetPadding {

    /**
     * Applies the keyboard's bottom inset as extra bottom padding on
     * [scrollView] (on top of whatever bottom padding it already had -
     * `basePaddingBottom` is read once, before this listener can run and
     * overwrite it, so repeated inset dispatches never compound the
     * original padding with itself), then scrolls a currently-focused
     * field back above the keyboard.
     *
     * Padding alone isn't enough: it only grows the scrollable range, it
     * doesn't move anything - confirmed on-device that the padding was
     * being applied correctly (imeBottom and all) and the focused field
     * was still sitting entirely under the keyboard's touchable region.
     * [android.view.View.requestRectangleOnScreen] doesn't fix that
     * either - the ScrollView doesn't know the keyboard exists as far as
     * its own layout is concerned (it's a system overlay, not a resize of
     * the ScrollView itself, since decorFitsSystemWindows is false here),
     * so from its perspective the field already fits inside its
     * unchanged height and there's nothing to scroll. This computes the
     * field's position directly and scrolls the difference by hand:
     * "visible" is redefined as this view's own height minus the keyboard
     * inset, and the field is pushed above that line if it isn't already.
     */
    fun applyToBottom(scrollView: ScrollView) {
        val basePaddingBottom = scrollView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            scrollView.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, basePaddingBottom + imeBottom)
            if (imeBottom > 0) {
                scrollView.findFocus()?.let { focused ->
                    scrollView.post { scrollFocusedAboveKeyboard(scrollView, focused, imeBottom) }
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(scrollView)
    }

    private fun scrollFocusedAboveKeyboard(scrollView: ScrollView, focused: android.view.View, imeBottom: Int) {
        val fieldBounds = Rect(0, 0, focused.width, focused.height)
        scrollView.offsetDescendantRectToMyCoords(focused, fieldBounds)
        val visibleTop = scrollView.scrollY
        val visibleBottom = scrollView.scrollY + scrollView.height - imeBottom
        when {
            fieldBounds.bottom > visibleBottom -> scrollView.scrollBy(0, fieldBounds.bottom - visibleBottom)
            fieldBounds.top < visibleTop -> scrollView.scrollBy(0, fieldBounds.top - visibleTop)
        }
    }
}
