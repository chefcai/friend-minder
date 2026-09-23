package com.example.friendminder.ui.history

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact
import com.example.friendminder.data.models.ContactStatistics
import com.example.friendminder.databinding.FragmentOverallHistoryBinding
import com.example.friendminder.databinding.ItemHomeContactBinding
import com.example.friendminder.domain.services.OutreachLogService
import com.example.friendminder.ui.common.AvatarBinder
import com.example.friendminder.ui.common.withLivePhotoUris
import com.example.friendminder.ui.contactdetail.ContactDetailFragment
import com.example.friendminder.ui.dashboard.DashboardChartCalculator
import com.example.friendminder.ui.home.HomeLastTouchFormatter
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Overall History (FRM-101, SCREENS-PHASE3.md §4): the app-wide stats
 * screen, replacing the now-deleted DashboardFragment (its key-metric block
 * and Monthly outreach chart move here re-skinned; "Needs attention" and
 * the aggregated upcoming-dates list are retired - see this ticket's PR
 * description for the full accounting). No separate ViewModel - matches
 * every other screen in this app (see HomeFragment's kdoc for the
 * reasoning); state lives in the Fragment.
 *
 * Reuses the exact edge-to-edge header wiring HomeFragment established
 * (DESIGN-SYSTEM-PHASE3.md §5) - duplicated rather than shared, since
 * there's no shared base Fragment class anywhere in this codebase (matches
 * its minimal-dependency, no-shared-ViewModel style) and introducing one
 * for two call sites isn't this ticket's job. Worth revisiting once FRM-103
 * re-skins Groups/Settings/Contact Detail with the same header and there
 * are five call sites doing this, not two.
 *
 * "No outreach ever logged" (SCREENS-PHASE3.md §4.7's empty-state trigger)
 * is computed from every tracked friend's lifetime
 * [ContactStatistics.totalContacts], not from the monthly aggregate or the
 * bounded streak/chart data alone - those only reflect a recent window, so
 * a friend whose only outreach was logged more than a month ago would
 * otherwise show a zeroed key metric next to an incorrectly-triggered
 * "nothing to show" state. This is a stricter, more literal reading of
 * "ever logged" than the old Dashboard's isEmpty check used.
 */
class OverallHistoryFragment : Fragment() {

    private var _binding: FragmentOverallHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOverallHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        applyHeaderInsets()
    }

    override fun onResume() {
        super.onResume()
        applyEdgeToEdgeHeader()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        restoreStandardStatusBar()
    }

    /** DESIGN-SYSTEM-PHASE3.md §5 - identical to HomeFragment.applyEdgeToEdgeHeader; see that class's kdoc. */
    private fun applyEdgeToEdgeHeader() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
    }

    private fun restoreStandardStatusBar() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        val typedValue = TypedValue()
        if (requireContext().theme.resolveAttribute(android.R.attr.statusBarColor, typedValue, true)) {
            window.statusBarColor = typedValue.data
        }
    }

    /** Feeds the real status-bar inset into statusBarSpacer, so the teal fill spans it with no seam (§5). */
    private fun applyHeaderInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerContainer) { _, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.statusBarSpacer.layoutParams = binding.statusBarSpacer.layoutParams.apply {
                height = topInset
            }
            binding.statusBarSpacer.requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(binding.headerContainer)
    }

    private fun refresh() {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.contentScroll.visibility = View.GONE
        binding.emptyStateGroup.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val friendListRepository = ServiceLocator.friendListRepository
            val statisticsService = ServiceLocator.statisticsService
            val outreachLogService = ServiceLocator.outreachLogService

            // GH #116: enrich with each friend's live device-contact
            // photo before building rows - see withLivePhotoUris' kdoc.
            val friends = withLivePhotoUris(requireContext(), friendListRepository.getFriendList())
            binding.loadingIndicator.visibility = View.GONE

            if (friends.isEmpty()) {
                binding.emptyStateGroup.visibility = View.VISIBLE
                return@launch
            }

            val byId = friends.associateBy { it.id }
            val stats = friends.map { statisticsService.getStatistics(it.id) }
            val everLogged = stats.any { it.totalContacts > 0 }

            if (!everLogged) {
                binding.emptyStateGroup.visibility = View.VISIBLE
                return@launch
            }

            // FRM-170 (OH-1): the key figure counts people, not events - distinct
            // tracked contacts with at least one outreach this calendar month
            // (Architect, FRM-169). monthlyOutreachCount counted every log, so
            // it could read "15 contacts reached" with 4 contacts tracked.
            val contactsReached = statisticsService.getAggregateStatistics().contactsReachedThisMonth
            val weeklyChart = computeWeeklyChart(outreachLogService)

            binding.contentScroll.visibility = View.VISIBLE
            bindKeyMetric(contactsReached)
            binding.monthlyChart.setValues(weeklyChart, weeklyChartAxisLabels(weeklyChart.size))
            bindStreaks(byId, stats)
        }
    }

    private fun bindKeyMetric(contactsReached: Int) {
        binding.keyMetricFigure.text = contactsReached.toString()
        binding.keyMetricLabel.text = resources.getQuantityString(
            R.plurals.label_overall_history_key_metric, contactsReached
        )
        binding.keyMetricBlock.contentDescription = resources.getQuantityString(
            R.plurals.format_overall_history_key_metric_description, contactsReached, contactsReached
        )
    }

    /**
     * §4.6: top 5 by streak length descending, ties broken by name via the
     * same locale-aware [Collator] Home uses; whole section (its label
     * included) hidden entirely when no contact has a streak. Rows are
     * inflated straight into streaksContainer rather than through a
     * RecyclerView (§4.8) - the same bounded-list pattern the old
     * DashboardFragment used for its own top-5 sections. No manual
     * cancel-on-recycle for the avatar-load jobs (unlike
     * HomeContactAdapter's ViewHolder): this is a static list, not a
     * RecyclerView reusing view holders across different data, so
     * viewLifecycleOwner.lifecycleScope already cancels every job here the
     * moment this screen's view is destroyed.
     */
    private fun bindStreaks(byId: Map<String, Contact>, stats: List<ContactStatistics>) {
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
        val streaked = stats
            .filter { it.streak > 0 }
            .mapNotNull { stat -> byId[stat.contactId]?.let { contact -> Triple(contact, stat.streak, stat.lastContacted) } }
            .sortedWith(compareByDescending<Triple<Contact, Int, Long?>> { it.second }.thenBy(collator) { it.first.name })
            .take(MAX_STREAK_ROWS)

        binding.streaksSection.visibility = if (streaked.isEmpty()) View.GONE else View.VISIBLE
        binding.streaksContainer.removeAllViews()
        streaked.forEachIndexed { index, (contact, streak, lastContacted) ->
            val itemBinding = ItemHomeContactBinding.inflate(layoutInflater, binding.streaksContainer, false)
            bindStreakRow(itemBinding, contact, streak, lastContacted, isFirst = index == 0)
            binding.streaksContainer.addView(itemBinding.root)
        }
    }

    private fun bindStreakRow(
        itemBinding: ItemHomeContactBinding,
        contact: Contact,
        streak: Int,
        lastContacted: Long?,
        isFirst: Boolean
    ) {
        itemBinding.topDivider.visibility = if (isFirst) View.GONE else View.VISIBLE
        itemBinding.contactName.text = contact.name
        itemBinding.lastTouch.text = HomeLastTouchFormatter.format(requireContext(), lastContacted)

        // Every row here has streak > 0 by construction (see bindStreaks'
        // filter), so this is always the filled badge state - the open-ring
        // state item_home_contact.xml also supports never applies on this
        // screen.
        itemBinding.statusBadge.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_badge_filled)
        itemBinding.statusBadgeNumeral.visibility = View.VISIBLE
        itemBinding.statusBadgeNumeral.text = if (streak > MAX_DISPLAYED_STREAK) {
            getString(R.string.format_streak_badge_overflow)
        } else {
            streak.toString()
        }
        itemBinding.statusBadge.contentDescription =
            resources.getQuantityString(R.plurals.format_home_streak_description, streak, streak)

        AvatarBinder.bind(
            itemBinding.contactPhoto, itemBinding.contactInitial, contact,
            ServiceLocator.contactPhotoLoader, viewLifecycleOwner.lifecycleScope
        )
        itemBinding.root.setOnClickListener { openContactDetail(contact.id) }
    }

    private fun openContactDetail(contactId: String) {
        // Same as HomeFragment.openContactDetail - see this class's kdoc.
        parentFragmentManager.commit {
            replace(R.id.nav_host_container, ContactDetailFragment.newInstance(contactId))
            addToBackStack(null)
        }
    }

    private suspend fun computeWeeklyChart(outreachLogService: OutreachLogService): List<Int> {
        val now = System.currentTimeMillis()
        val weekMillis = TimeUnit.DAYS.toMillis(DAYS_PER_WEEK.toLong())
        val cumulative = (0..CHART_WEEKS).map { outreachLogService.countSince(now - it * weekMillis) }
        return DashboardChartCalculator.weeklyBuckets(cumulative)
    }

    /**
     * Persistent per-bar axis labels for the Monthly outreach chart (GH #101 -
     * DESIGN-SYSTEM-PHASE3.md §6.8/SCREENS-PHASE3.md §4.5 call for these to be
     * preserved unchanged from the orphaned Dashboard's implementation).
     * [DashboardChartCalculator.axisWeeksAgo] does the oldest-first index
     * math; this just turns each "weeks ago" into a localized string.
     */
    private fun weeklyChartAxisLabels(barCount: Int): List<String> =
        DashboardChartCalculator.axisWeeksAgo(barCount).map { weeksAgo ->
            if (weeksAgo == 0) {
                getString(R.string.label_chart_axis_this_week)
            } else {
                resources.getQuantityString(R.plurals.format_chart_axis_weeks_ago, weeksAgo, weeksAgo)
            }
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CHART_WEEKS = 4
        private const val DAYS_PER_WEEK = 7
        // 9+ (format_streak_badge_overflow's literal text): matches
        // HomeContactAdapter's own threshold, since this screen reuses that
        // exact row/badge - not the old DashboardFragment's (10).
        private const val MAX_DISPLAYED_STREAK = 9
        private const val MAX_STREAK_ROWS = 5

        fun newInstance(): OverallHistoryFragment = OverallHistoryFragment()
    }
}
