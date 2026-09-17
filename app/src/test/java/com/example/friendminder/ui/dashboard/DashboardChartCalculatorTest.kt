package com.example.friendminder.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
        // now=10, -7d=6, -14d=3, -21d=1, -28d=0 -> per-week (newest first) 4,3,2,1 -> reversed
        val cumulative = listOf(10, 6, 3, 1, 0)
        assertEquals(listOf(1, 2, 3, 4), DashboardChartCalculator.weeklyBuckets(cumulative))
    }

    @Test
    fun `all-zero input yields all-zero buckets`() {
        val cumulative = listOf(0, 0, 0, 0, 0)
        assertEquals(listOf(0, 0, 0, 0), DashboardChartCalculator.weeklyBuckets(cumulative))
    }

    @Test
    fun `minimum two cumulative counts yields exactly one bucket`() {
        val cumulative = listOf(5, 2)
        assertEquals(listOf(3), DashboardChartCalculator.weeklyBuckets(cumulative))
    }

    @Test
    fun `equal consecutive cumulative counts yield a zero bucket for that week`() {
        val cumulative = listOf(8, 8, 5)
        assertEquals(listOf(3, 0), DashboardChartCalculator.weeklyBuckets(cumulative))
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
