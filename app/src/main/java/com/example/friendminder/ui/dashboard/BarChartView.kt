package com.example.friendminder.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.friendminder.R

private const val GAP_FRACTION = 0.12f
private const val MAX_BAR_HEIGHT_FRACTION = 0.92f
private const val CORNER_RADIUS_DP = 4f

/**
 * Minimal Canvas bar chart for the Dashboard's "Monthly outreach" card
 * (Designer's DESIGN-SYSTEM-PHASE2.md §4.6): one bar per week, `primary`
 * color, 4dp top-corner radius, no gridlines/persistent axis labels — a
 * tap shows the exact count as a short Toast (~2s, per "Chart interaction"
 * in SCREENS-PHASE2.md §2), rather than a persistent tooltip.
 *
 * Deliberately a hand-rolled View rather than a charting library: the PRD's
 * Definition of Done explicitly rules out new external dependencies to stay
 * F-Droid compatible, and 4-5 flat bars don't need one. Per-bar TalkBack
 * announcements (SCREENS-PHASE2.md §6) aren't implemented here — only a
 * whole-view content description summarizing the series — flagged as a
 * simplification rather than silently skipped.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Int> = emptyList()
    private var lastTappedIndex = -1
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.fm_primary)
    }
    private val barRects = mutableListOf<RectF>()
    private val cornerRadius = resources.displayMetrics.density * CORNER_RADIUS_DP

    fun setValues(newValues: List<Int>) {
        values = newValues
        contentDescription = context.getString(
            R.string.label_dashboard_chart_header
        ) + ": " + newValues.joinToString(", ")
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        barRects.clear()
        if (values.isEmpty() || width == 0 || height == 0) return

        val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val barCount = values.size
        val gap = width * GAP_FRACTION / barCount
        val barWidth = (width - gap * (barCount + 1)) / barCount

        values.forEachIndexed { index, value ->
            val left = gap + index * (barWidth + gap)
            val barHeight = (value.toFloat() / maxValue) * height * MAX_BAR_HEIGHT_FRACTION
            val top = height - barHeight
            val rect = RectF(left, top, left + barWidth, height.toFloat())
            barRects += rect

            val path = Path().apply {
                addRoundRect(
                    rect,
                    floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f),
                    Path.Direction.CW
                )
            }
            canvas.drawPath(path, barPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val tappedIndex = barRects.indexOfFirst { it.left <= event.x && event.x <= it.right }
            if (tappedIndex >= 0) {
                lastTappedIndex = tappedIndex
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        val index = lastTappedIndex
        if (index in values.indices) {
            Toast.makeText(
                context,
                context.getString(R.string.format_chart_bar_count, values[index]),
                Toast.LENGTH_SHORT
            ).show()
        }
        return true
    }
}
