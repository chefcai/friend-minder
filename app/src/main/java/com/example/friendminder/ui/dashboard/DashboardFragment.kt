package com.example.friendminder.ui.dashboard

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.friendminder.R
import com.example.friendminder.data.models.Contact
import com.example.friendminder.databinding.FragmentDashboardBinding
import com.example.friendminder.databinding.ItemDashboardNeglectedBinding
import com.example.friendminder.databinding.ItemDashboardStreakBinding
import com.example.friendminder.databinding.ItemDashboardUpcomingBinding
import com.example.friendminder.data.models.AggregateStatistics
import com.example.friendminder.domain.services.BirthdayService
import com.example.friendminder.domain.services.OutreachLogService
import com.example.friendminder.domain.services.StatisticsService
import com.example.friendminder.ui.common.AvatarBinder
import com.example.friendminder.ui.contactdetail.ContactDetailFragment
import com.example.friendminder.utils.ServiceLocator
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * FRM-55: the Dashboard tab (PRD §6.5; Designer's SCREENS-PHASE2.md §2).
 * No separate ViewModel class — matches every other screen in this app
 * (HomeFragment, FriendListFragment, AdvancedSettingsFragment), all of
 * which call [ServiceLocator] services directly from
 * `viewLifecycleOwner.lifecycleScope` rather than through a ViewModel
 * layer. `claude/agents-phase-2.md` sketches "DashboardViewModel," but
 * introducing MVVM for only these four Phase 2 screens — inconsistent
 * with 100% of the existing codebase, and unverified against this
 * project's deliberately minimal dependency set (no
 * `lifecycle-viewmodel-ktx` in `build.gradle.kts`) — isn't worth the
 * inconsistency; the state-holding here just lives in the Fragment,
 * exactly like its siblings.
 *
 * Bounded-size sections (top 5 streaks, top 5 neglected, upcoming dates
 * within 14 days) are rendered by inflating item rows straight into a
 * container `LinearLayout` rather than a `RecyclerView` + adapter — simpler
 * for lists this small, and there's real precedent for it being a
 * deliberate choice rather than a shortcut (contrast with GroupAdapter/
 * GroupMemberAdapter/OutreachHistoryAdapter, which back genuinely
 * unbounded lists and do use RecyclerView).
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh(forceRefresh = false)
    }

    private fun refresh(forceRefresh: Boolean) {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.contentGroup.visibility = View.GONE
        binding.emptyStateGroup.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val friendListRepository = ServiceLocator.friendListRepository
            val statisticsService = ServiceLocator.statisticsService
            val birthdayService = ServiceLocator.birthdayService
            val outreachLogService = ServiceLocator.outreachLogService

            val friends = friendListRepository.getFriendList()
            binding.loadingIndicator.visibility = View.GONE

            if (friends.isEmpty()) {
                binding.emptyStateGroup.visibility = View.VISIBLE
                return@launch
            }

            val byId = friends.associateBy { it.id }
            val aggregate = statisticsService.getAggregateStatistics(forceRefresh)
            val upcoming = birthdayService.getUpcomingDates(UPCOMING_WINDOW_DAYS)
            val weeklyChart = computeWeeklyChart(outreachLogService)

            val streaks = aggregate.healthiestStreaks
                .filter { it.streak > 0 }
                .mapNotNull { stat -> byId[stat.contactId]?.let { it to stat.streak } }

            val neglected = aggregate.mostNeglected
                .mapNotNull { stat -> byId[stat.contactId]?.let { it to stat.daysSinceContact } }

            val upcomingEntries = upcoming.mapNotNull { date ->
                byId[date.contactId]?.let { Triple(it, date.label, date.daysUntil) }
            }

            val isEmpty = aggregate.monthlyOutreachCount == 0 &&
                streaks.isEmpty() && neglected.isEmpty() && upcomingEntries.isEmpty()

            if (isEmpty) {
                binding.emptyStateGroup.visibility = View.VISIBLE
                return@launch
            }

            binding.contentGroup.visibility = View.VISIBLE
            bindKeyMetric(aggregate)
            bindStreaks(streaks)
            bindNeglected(neglected)
            binding.monthlyChart.setValues(weeklyChart)
            bindUpcoming(upcomingEntries)
        }
    }

    private fun bindKeyMetric(aggregate: AggregateStatistics) {
        binding.keyMetricText.text = resources.getQuantityString(
            R.plurals.format_dashboard_key_metric,
            aggregate.monthlyOutreachCount,
            aggregate.monthlyOutreachCount
        )
    }

    private fun bindStreaks(streaks: List<Pair<Contact, Int>>) {
        binding.streaksCard.visibility = if (streaks.isEmpty()) View.GONE else View.VISIBLE
        binding.streaksContainer.removeAllViews()
        streaks.forEach { (contact, streak) ->
            val itemBinding = ItemDashboardStreakBinding.inflate(
                layoutInflater, binding.streaksContainer, false
            )
            itemBinding.streakName.text = contact.name
            itemBinding.streakBadge.text = if (streak >= MAX_DISPLAYED_STREAK) {
                getString(R.string.format_streak_badge_overflow)
            } else {
                streak.toString()
            }
            bindAvatar(itemBinding.contactPhoto, itemBinding.contactInitial, contact)
            itemBinding.root.setOnClickListener { openContactDetail(contact.id) }
            binding.streaksContainer.addView(itemBinding.root)
        }
    }

    private fun bindNeglected(neglected: List<Pair<Contact, Int?>>) {
        binding.neglectedCard.visibility = if (neglected.isEmpty()) View.GONE else View.VISIBLE
        binding.neglectedContainer.removeAllViews()
        neglected.forEach { (contact, days) ->
            val itemBinding = ItemDashboardNeglectedBinding.inflate(
                layoutInflater, binding.neglectedContainer, false
            )
            itemBinding.neglectedName.text = contact.name
            itemBinding.neglectedDays.text = formatDaysAgo(days)
            bindAvatar(itemBinding.contactPhoto, itemBinding.contactInitial, contact)
            itemBinding.root.setOnClickListener { openContactDetail(contact.id) }
            itemBinding.reachOutButton.setOnClickListener { openContactDetail(contact.id) }
            binding.neglectedContainer.addView(itemBinding.root)
        }
    }

    private fun bindUpcoming(upcoming: List<Triple<Contact, String, Int>>) {
        binding.upcomingCard.visibility = if (upcoming.isEmpty()) View.GONE else View.VISIBLE
        binding.upcomingContainer.removeAllViews()
        upcoming.forEach { (contact, label, daysUntil) ->
            val itemBinding = ItemDashboardUpcomingBinding.inflate(
                layoutInflater, binding.upcomingContainer, false
            )
            itemBinding.upcomingLabel.text = getString(R.string.format_special_date_row, contact.name, label)
            itemBinding.upcomingDays.text = if (daysUntil == 0) {
                getString(R.string.label_today)
            } else {
                resources.getQuantityString(R.plurals.format_upcoming_in_days, daysUntil, daysUntil)
            }
            itemBinding.root.setOnClickListener { openContactDetail(contact.id) }
            binding.upcomingContainer.addView(itemBinding.root)
        }
    }

    private fun bindAvatar(photoView: android.widget.ImageView, initialsView: android.widget.TextView, contact: Contact) {
        AvatarBinder.bind(photoView, initialsView, contact, ServiceLocator.contactPhotoLoader, viewLifecycleOwner.lifecycleScope)
    }

    private fun formatDaysAgo(days: Int?): String = when {
        days == null -> getString(R.string.label_never_contacted)
        days <= 0 -> getString(R.string.label_today)
        else -> resources.getQuantityString(R.plurals.format_days_ago, days, days)
    }

    private fun openContactDetail(contactId: String) {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val UPCOMING_WINDOW_DAYS = 14
        private const val CHART_WEEKS = 4
        private const val DAYS_PER_WEEK = 7
        private const val MAX_DISPLAYED_STREAK = 10

        fun newInstance(): DashboardFragment = DashboardFragment()
    }
}
