package com.example.friendminder.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [HomeLastTouchFormatter.bucketFor] (FRM-181): pure day-count
 * classification with no Android [android.content.Context] dependency,
 * factored out of [HomeLastTouchFormatter.format] specifically so it's
 * testable in plain JUnit - same rationale as
 * [com.example.friendminder.ui.dashboard.DashboardChartCalculatorTest] (FRM-55).
 *
 * Regression coverage for the reported bug: days 28-29 used to compute
 * `days / 30 == 0` and render "0 months ago". The fix rounds the weeks
 * bucket instead of flooring it (so 27-29 days read "4 weeks ago") and
 * starts the months bucket at day 30.
 */
class HomeLastTouchFormatterTest {

    @Test
    fun `day 0 is today`() {
        assertEquals(HomeLastTouchFormatter.Bucket.Today, HomeLastTouchFormatter.bucketFor(0))
    }

    @Test
    fun `day 1 is yesterday`() {
        assertEquals(HomeLastTouchFormatter.Bucket.Yesterday, HomeLastTouchFormatter.bucketFor(1))
    }

    @Test
    fun `days 2 through 13 are the days bucket`() {
        assertEquals(HomeLastTouchFormatter.Bucket.Days(2), HomeLastTouchFormatter.bucketFor(2))
        assertEquals(HomeLastTouchFormatter.Bucket.Days(13), HomeLastTouchFormatter.bucketFor(13))
    }

    @Test
    fun `day 14 is the first day of the weeks bucket`() {
        assertEquals(HomeLastTouchFormatter.Bucket.Weeks(2), HomeLastTouchFormatter.bucketFor(14))
    }

    @Test
    fun `days 27, 28 and 29 all round to 4 weeks ago`() {
        // FRM-181 evidence case: Brenda Bishop at day 28 used to show "0 months
        // ago" because 28 / 30 == 0. 27 and 29 are covered too since they're
        // closer to 4 weeks (28 days) than to 3 weeks (21) or 1 month (30).
        assertEquals(HomeLastTouchFormatter.Bucket.Weeks(4), HomeLastTouchFormatter.bucketFor(27))
        assertEquals(HomeLastTouchFormatter.Bucket.Weeks(4), HomeLastTouchFormatter.bucketFor(28))
        assertEquals(HomeLastTouchFormatter.Bucket.Weeks(4), HomeLastTouchFormatter.bucketFor(29))
    }

    @Test
    fun `days 30 and 31 are 1 month ago`() {
        assertEquals(HomeLastTouchFormatter.Bucket.Months(1), HomeLastTouchFormatter.bucketFor(30))
        assertEquals(HomeLastTouchFormatter.Bucket.Months(1), HomeLastTouchFormatter.bucketFor(31))
    }

    @Test
    fun `day 59 is still 1 month ago`() {
        assertEquals(HomeLastTouchFormatter.Bucket.Months(1), HomeLastTouchFormatter.bucketFor(59))
    }

    @Test
    fun `day 60 rolls over to 2 months ago`() {
        assertEquals(HomeLastTouchFormatter.Bucket.Months(2), HomeLastTouchFormatter.bucketFor(60))
    }

    @Test
    fun `day 364 is the last day of the months bucket`() {
        assertEquals(HomeLastTouchFormatter.Bucket.Months(12), HomeLastTouchFormatter.bucketFor(364))
    }

    @Test
    fun `day 365 rolls over to over a year ago`() {
        assertEquals(HomeLastTouchFormatter.Bucket.OverAYear, HomeLastTouchFormatter.bucketFor(365))
    }
}
