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
private const val AXIS_LABEL_PAD_DP = 2f
private const val AXIS_LABEL_SIDE_PAD_DP = 2f
// Caption (DESIGN-SYSTEM-PHASE3.md §3): 13sp/18sp/400.
private const val AXIS_LABEL_TEXT_SIZE_SP = 13f
private const val BASELINE_STROKE_WIDTH_DP = 1f

/**
 * Minimal Canvas bar chart for the Overall History screen's "Monthly
 * outreach" section (FRM-101, DESIGN-SYSTEM-PHASE3.md §6.8 +
 * SCREENS-PHASE3.md §4.5): one bar per week, `fm_primary` color, 4dp
 * top-corner radius, a 1dp `fm_divider` baseline across the full width,
 * and persistent Caption-style axis labels (`fm_ink_dim`) under each bar -
 * no gridlines, no frame, no card (re-skinned from the orphaned
 * Dashboard's card-bound original; the bucket/scaling math and
 * tap-to-reveal behavior below are unchanged). A tap shows the exact
 * count as a short Toast (~2s, per "Chart interaction" in the original
 * SCREENS-PHASE2.md §2, carried forward unchanged - §6.8's "values on
 * tap, not persistently" matches it), rather than a persistent value
 * label.
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
 * this view sits inside `fragment_overall_history.xml`'s single
 * `ScrollView` (formerly `fragment_dashboard.xml`'s), and a real
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
        color = ContextCompat.getColor(context, R.color.fm_ink_dim)
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            AXIS_LABEL_TEXT_SIZE_SP,
            resources.displayMetrics
        )
    }
    // DESIGN-SYSTEM-PHASE3.md §4.5: "1dp fm_divider baseline across content
    // width, no gridlines/frame" - drawn full-width (not gap-inset like the
    // bars), at the same y the bars sit on.
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.fm_divider)
    }
    private val baseLabelTextSize = axisLabelPaint.textSize
    private var labelLines: List<List<String>> = emptyList()
    private var labelAreaHeight = 0f
    private var labelLineHeight = 0f
    private var labelAscent = 0f
    private val barRects = mutableListOf<RectF>()
    private val density = resources.displayMetrics.density
    private val cornerRadius = density * CORNER_RADIUS_DP
    private val baselineStrokeWidth = density * BASELINE_STROKE_WIDTH_DP

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
        layoutLabels()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutLabels()
    }

    /**
     * FRM-163 (X6): the labels are sized in sp but each one only has its bar
     * slot to live in, and the strip under the bars used to be a fixed 18dp.
     * At 200% text the four labels overran each other. Wrap to two lines
     * first, shrink only if a single word still overflows, and size the strip
     * from the real text height.
     */
    private fun layoutLabels() {
        axisLabelPaint.textSize = baseLabelTextSize
        if (labels.isEmpty() || width == 0) {
            labelLines = emptyList()
            labelAreaHeight = 0f
            return
        }
        val gap = width * GAP_FRACTION / labels.size
        val usable = (width - gap) / labels.size - 2 * density * AXIS_LABEL_SIDE_PAD_DP
        labelLines = labels.map { wrapAxisLabel(it, usable, axisLabelPaint::measureText) }
        val widest = labelLines.flatten().maxOf { axisLabelPaint.measureText(it) }
        if (widest > usable) axisLabelPaint.textSize = baseLabelTextSize * usable / widest
        val metrics = axisLabelPaint.fontMetrics
        labelLineHeight = metrics.descent - metrics.ascent
        labelAscent = metrics.ascent
        val lineCount = labelLines.maxOf { it.size }
        labelAreaHeight = maxOf(
            density * AXIS_LABEL_AREA_HEIGHT_DP,
            lineCount * labelLineHeight + 2 * density * AXIS_LABEL_PAD_DP
        )
    }

    private fun buildContentDescription(values: List<Int>, labels: List<String>): String {
        val header = context.getString(R.string.label_overall_history_chart_header)
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

        val chartHeight = height - labelAreaHeight
        val firstBaselineY = chartHeight + density * AXIS_LABEL_PAD_DP - labelAscent

        val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val barCount = values.size
        val gap = width * GAP_FRACTION / barCount
        val barWidth = (width - gap * (barCount + 1)) / barCount

        canvas.drawRect(0f, chartHeight - baselineStrokeWidth, width.toFloat(), chartHeight, baselinePaint)

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

            labelLines.getOrNull(index)?.forEachIndexed { line, text ->
                canvas.drawText(text, left + barWidth / 2f, firstBaselineY + line * labelLineHeight, axisLabelPaint)
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

/**
 * FRM-163 (X6): fits one axis label into its bar slot. A label that is wider
 * than [maxWidth] (e.g. "3 wks ago" at 200% text) is split at the space that
 * makes the wider of the two lines narrowest, so large-text users keep the
 * full text size. A single word that still does not fit is returned as-is;
 * the caller scales the paint down as the last resort.
 */
internal fun wrapAxisLabel(label: String, maxWidth: Float, measure: (String) -> Float): List<String> {
    if (measure(label) <= maxWidth) return listOf(label)
    val words = label.split(' ').filter { it.isNotEmpty() }
    if (words.size < 2) return listOf(label)
    return (1 until words.size)
        .map { i -> listOf(words.take(i).joinToString(" "), words.drop(i).joinToString(" ")) }
        .minBy { lines -> lines.maxOf(measure) }
}
