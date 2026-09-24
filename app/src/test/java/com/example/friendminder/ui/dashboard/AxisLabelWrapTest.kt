package com.example.friendminder.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** FRM-163 (X6): axis labels wrap instead of overrunning their bar slot. */
class AxisLabelWrapTest {

    // 10px per character, so widths are easy to reason about.
    private val measure: (String) -> Float = { it.length * 10f }

    @Test
    fun labelThatFitsStaysOnOneLine() {
        assertEquals(listOf("This wk"), wrapAxisLabel("This wk", 100f, measure))
    }

    @Test
    fun wideLabelSplitsWhereTheWiderLineIsNarrowest() {
        assertEquals(listOf("3 wks", "ago"), wrapAxisLabel("3 wks ago", 60f, measure))
    }

    @Test
    fun singleWordIsReturnedUnchangedForTheCallerToScale() {
        assertEquals(listOf("Yesterday"), wrapAxisLabel("Yesterday", 40f, measure))
    }
}
