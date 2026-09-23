package com.example.friendminder.ui.common

import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * FRM-160 (audit CD-8): at larger text sizes a two-segment control no longer
 * fits side by side - each segment gets under half the screen, and
 * MaterialButton keeps its label on one line, so "Special Dates" / "Random
 * window" were cut off ("Special Dat…" already at 130%, "Special …" at 200%).
 *
 * After the first layout, if any segment's label is ellipsized, the control
 * switches to a vertical stack of full-width segments, so every label shows
 * at full size. It is measured rather than keyed to a font-scale threshold
 * because whether a label fits depends on the text, the locale and the
 * screen width, not just the scale.
 */
fun MaterialButtonToggleGroup.stackIfLabelsDontFit() {
    doOnLayout {
        val clipped = (0 until childCount).any { i ->
            val layout = (getChildAt(i) as? TextView)?.layout
            layout != null && layout.lineCount > 0 && layout.getEllipsisCount(0) > 0
        }
        if (!clipped || orientation == LinearLayout.VERTICAL) return@doOnLayout
        orientation = LinearLayout.VERTICAL
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.layoutParams = (child.layoutParams as LinearLayout.LayoutParams).apply {
                width = LinearLayout.LayoutParams.MATCH_PARENT
                height = LinearLayout.LayoutParams.WRAP_CONTENT
                weight = 0f
            }
        }
    }
}
