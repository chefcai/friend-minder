package com.example.friendminder.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DashboardChartCalculator] (FRM-55): pure math with no
 * Android or repository dependencies, factored out of [DashboardFragment]
 * specifically so it's testable in plain JUnit - same rationale as
 * [com.example.friendminder.domain.services.StatisticsCalculatorTest] (FRM-45).
 */
class DashboardChartCalculatorTest {

    @Test
    fun `derives per-week counts from cumulative counts, oldest week first`() {
        // countSince(now)=0, countSince(-7d)=1, countSince(-14d)=3, countSince(-21d)=6,
        // countSince(-28d)=10 -- cumulative grows as the cutoff moves further into the
        // past (a wider window can only match as many or more logs), never shrinks.
        // Per-week (newest first): 10-6=4, 6-3=3, 3-1=2, 1-0=1 -> reversed to oldest-first.
        val cumulative = listOf(0, 1, 3, 6, 10)
        assertEquals(listOf(4, 3, 2, 1), DashboardChartCalculator.weeklyBuckets(cumulative))
    }

    @Test
    fun `all-zero input yields all-zero buckets`() {
        val cumulative = listOf(0, 0, 0, 0, 0)
        assertEquals(listOf(0, 0, 0, 0), DashboardChartCalculator.weeklyBuckets(cumulative))
    }

    @Test
    fun `minimum two cumulative counts yields exactly one bucket`() {
        val cumulative = listOf(2, 5)
        assertEquals(listOf(3), DashboardChartCalculator.weeklyBuckets(cumulative))
    }

    @Test
    fun `equal consecutive cumulative counts yield a zero bucket for that week`() {
        val cumulative = listOf(5, 8, 8)
        assertEquals(listOf(0, 3), DashboardChartCalculator.weeklyBuckets(cumulative))
    }

    @Test
    fun `never produces a negative bucket even with realistic non-decreasing input`() {
        // Regression guard for the reversed-subtraction bug: every real call site
        // passes a non-decreasing cumulative sequence (further-back cutoff can only
        // match as many or more logs), so every bucket must be >= 0.
        val cumulative = listOf(0, 0, 1, 1, 4)
        val buckets = DashboardChartCalculator.weeklyBuckets(cumulative)
        assertTrue(buckets.all { it >= 0 })
        assertEquals(listOf(3, 0, 1, 0), buckets)
    }

    @Test
    fun `fewer than two cumulative counts throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardChartCalculator.weeklyBuckets(listOf(5))
        }
    }

    @Test
    fun `empty input throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            DashboardChartCalculator.weeklyBuckets(emptyList())
        }
    }
}
