package com.example.friendminder.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.friendminder.R

private const val GAP_FRACTION = 0.12f
private const val MAX_BAR_HEIGHT_FRACTION = 0.92f
private const val CORNER_RADIUS_DP = 4f
private const val AXIS_LABEL_AREA_HEIGHT_DP = 18f
private const val AXIS_LABEL_BASELINE_INSET_DP = 4f
private const val AXIS_LABEL_TEXT_SIZE_SP = 12f

/**
 * Minimal Canvas bar chart for the Dashboard's "Monthly outreach" card
 * (Designer's DESIGN-SYSTEM-PHASE2.md §5.6): one bar per week, `primary`
 * color, 4dp top-corner radius, persistent Caption-style axis labels
 * (`on-surface-variant`) under each bar, no gridlines — a tap shows the
 * exact count as a short Toast (~2s, per "Chart interaction" in
 * SCREENS-PHASE2.md §2), rather than a persistent value label.
 *
 * Deliberately a hand-rolled View rather than a charting library: the PRD's
 * Definition of Done explicitly rules out new external dependencies to stay
 * F-Droid compatible, and 4-5 flat bars don't need one. Per-bar TalkBack
 * announcements (SCREENS-PHASE2.md §6) aren't implemented as separate
 * explorable nodes here - only a whole-view content description
 * summarizing the series (now including each bar's axis label, e.g.
 * "Monthly outreach: 3wk ago: 3 contacts, ..., This wk: 7 contacts") -
 * flagged as a simplification rather than silently skipped.
 *
 * GH #101: this view previously never received the tap it claimed to
 * support. [onTouchEvent] only handled `ACTION_UP`, so on `ACTION_DOWN` it
 * fell through to `super.onTouchEvent()`, whose default implementation
 * returns `false` for a non-clickable View (the XML never set
 * `android:clickable="true"`, and nothing here called `setOnClickListener`).
 * A parent `ViewGroup`'s touch dispatch only keeps delivering a gesture's
 * later events to a child whose `ACTION_DOWN` it consumed - so the `ACTION_UP`
 * branch was dead code, not just fragile. Fixed by making the view
 * explicitly clickable and consuming `ACTION_DOWN`. Also asks its parent to
 * not intercept mid-gesture ([android.view.ViewParent.requestDisallowInterceptTouchEvent]) -
 * this card sits inside `fragment_dashboard.xml`'s `ScrollView`, and a real
 * finger tap almost always includes a little incidental movement, which a
 * `ScrollView` can otherwise interpret as the start of a scroll and steal
 * the rest of the gesture before `ACTION_UP` ever arrives.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Int> = emptyList()
    private var labels: List<String> = emptyList()
    private var lastTappedIndex = -1
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.fm_primary)
    }
    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.fm_on_surface_variant)
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            AXIS_LABEL_TEXT_SIZE_SP,
            resources.displayMetrics
        )
    }
    private val barRects = mutableListOf<RectF>()
    private val density = resources.displayMetrics.density
    private val cornerRadius = density * CORNER_RADIUS_DP

    init {
        isClickable = true
        isFocusable = true
    }

    /**
     * @param newValues one count per bar, oldest-to-newest (see [DashboardChartCalculator.weeklyBuckets]).
     * @param newLabels one persistent axis label per bar, same order as [newValues]
     *   (see [DashboardChartCalculator.axisWeeksAgo]) - e.g. `["3wk ago", "2wk ago", "1wk ago", "This wk"]`.
     *   Pass an empty list to omit axis labels entirely (bars still render, no label strip is reserved).
     */
    fun setValues(newValues: List<Int>, newLabels: List<String> = emptyList()) {
        require(newLabels.isEmpty() || newLabels.size == newValues.size) {
            "labels size (${newLabels.size}) must be empty or match values size (${newValues.size})"
        }
        values = newValues
        labels = newLabels
        contentDescription = buildContentDescription(newValues, newLabels)
        invalidate()
    }

    private fun buildContentDescription(values: List<Int>, labels: List<String>): String {
        val header = context.getString(R.string.label_dashboard_chart_header)
        val series = values.indices.joinToString(", ") { index ->
            val count = context.getString(R.string.format_chart_bar_count, values[index])
            if (index < labels.size) "${labels[index]}: $count" else count
        }
        return "$header: $series"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        barRects.clear()
        if (values.isEmpty() || width == 0 || height == 0) return

        val labelAreaHeight = if (labels.isNotEmpty()) density * AXIS_LABEL_AREA_HEIGHT_DP else 0f
        val chartHeight = height - labelAreaHeight
        val labelBaselineY = height - density * AXIS_LABEL_BASELINE_INSET_DP

        val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val barCount = values.size
        val gap = width * GAP_FRACTION / barCount
        val barWidth = (width - gap * (barCount + 1)) / barCount

        values.forEachIndexed { index, value ->
            val left = gap + index * (barWidth + gap)
            val barHeight = (value.toFloat() / maxValue) * chartHeight * MAX_BAR_HEIGHT_FRACTION
            val top = chartHeight - barHeight
            val rect = RectF(left, top, left + barWidth, chartHeight)
            barRects += rect

            val path = Path().apply {
                addRoundRect(
                    rect,
                    floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f),
                    Path.Direction.CW
                )
            }
            canvas.drawPath(path, barPaint)

            if (index < labels.size) {
                canvas.drawText(labels[index], left + barWidth / 2f, labelBaselineY, axisLabelPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Claim the gesture now, and keep the parent ScrollView from
                // stealing it on any incidental move before ACTION_UP - see
                // this class's KDoc (GH #101).
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val tappedIndex = barRects.indexOfFirst { it.left <= event.x && event.x <= it.right }
                if (tappedIndex >= 0) {
                    lastTappedIndex = tappedIndex
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
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
