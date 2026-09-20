package com.example.friendminder.ui.dashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val VIEW_WIDTH = 400
private const val VIEW_HEIGHT = 114

/**
 * Regression coverage for GH #101: this view previously claimed to support
 * tap-to-reveal, but [BarChartView.onTouchEvent] only handled `ACTION_UP`,
 * so the default (non-clickable) `ACTION_DOWN` handling returned `false` -
 * meaning a parent `ViewGroup`'s touch dispatch never routed the rest of the
 * gesture to this view at all. `ACTION_UP` was unreachable in practice.
 * These tests exercise the real Android touch-dispatch path (via a
 * `FrameLayout` parent, not calling `onTouchEvent` directly) so they'd have
 * caught that.
 */
@RunWith(AndroidJUnit4::class)
class BarChartViewTest {

    private fun layoutChartWithParent(): Pair<FrameLayout, BarChartView> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val chart = BarChartView(context)
        chart.setValues(listOf(1, 2, 3), listOf("2wk ago", "1wk ago", "This wk"))

        val parent = FrameLayout(context)
        parent.addView(chart, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        val widthSpec = View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY)
        parent.measure(widthSpec, heightSpec)
        parent.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)

        // onDraw is what populates barRects (the tap hit-boxes) - a real
        // layout pass alone doesn't invoke it, so draw onto a throwaway
        // Bitmap-backed Canvas first, the same as the real view pipeline would.
        val bitmap = Bitmap.createBitmap(VIEW_WIDTH, VIEW_HEIGHT, Bitmap.Config.ARGB_8888)
        chart.draw(Canvas(bitmap))

        return parent to chart
    }

    @Test
    fun chartViewIsClickable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val chart = BarChartView(context)
        assertTrue("BarChartView must be clickable or its ACTION_DOWN is never consumed (GH #101)", chart.isClickable)
    }

    @Test
    fun tapOnABarIsDeliveredThroughRealParentDispatch() {
        val (parent, chart) = layoutChartWithParent()
        // First bar's rect starts just past the leading gap - well inside the view.
        val tapX = 20f
        val tapY = VIEW_HEIGHT / 2f
        val now = android.os.SystemClock.uptimeMillis()

        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, tapX, tapY, 0)
        val up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, tapX, tapY, 0)
        try {
            // Dispatch through the PARENT, exercising the real ViewGroup touch-target
            // routing that the old (non-clickable) implementation silently failed.
            assertTrue("ACTION_DOWN must be consumed or later events never reach this view", parent.dispatchTouchEvent(down))
            assertTrue("ACTION_UP must be consumed once ACTION_DOWN claimed the gesture", parent.dispatchTouchEvent(up))
        } finally {
            down.recycle()
            up.recycle()
        }
    }
}
