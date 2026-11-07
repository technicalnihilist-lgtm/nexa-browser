package com.nexabrowser.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

/**
 * The history list used to build a Calendar and a SimpleDateFormat per row while
 * it composed. It now asks [DateLabels] for the label, which reuses one formatter
 * and one scratch Calendar per thread and decides Today/Yesterday from calendar
 * fields. These tests pin the decision, and the shape of the text the screen
 * shows, so the allocation-free version cannot drift from the old one.
 *
 * Timestamps are built in the default time zone and the assertions avoid
 * locale-specific month and AM/PM spellings, because both are properties of the
 * device rather than of the code under test.
 */
class DateLabelsTest {

    @Test
    fun `today and yesterday are named, not dated`() {
        assertThat(DateLabels.bucket(2026, 311, 2026, 311)).isEqualTo(DateLabels.Bucket.TODAY)
        assertThat(DateLabels.bucket(2026, 311, 2026, 310)).isEqualTo(DateLabels.Bucket.YESTERDAY)
    }

    @Test
    fun `an earlier day in the same year gets the weekday form`() {
        assertThat(DateLabels.bucket(2026, 311, 2026, 300)).isEqualTo(DateLabels.Bucket.SAME_YEAR)
    }

    @Test
    fun `another year gets the full date form`() {
        assertThat(DateLabels.bucket(2026, 3, 2025, 365)).isEqualTo(DateLabels.Bucket.EARLIER)
        assertThat(DateLabels.bucket(2026, 1, 2025, 365)).isEqualTo(DateLabels.Bucket.EARLIER)
    }

    @Test
    fun `today is named even when the clock has moved past the visit`() {
        val now = local(2026, Calendar.NOVEMBER, 7, 23, 59)
        val earlierToday = local(2026, Calendar.NOVEMBER, 7, 0, 1)
        assertThat(DateLabels.dayLabel(earlierToday, now)).isEqualTo("Today")
    }

    @Test
    fun `yesterday and earlier days are labelled without a time zone accident`() {
        val now = local(2026, Calendar.NOVEMBER, 7, 15, 30)
        assertThat(DateLabels.dayLabel(local(2026, Calendar.NOVEMBER, 6, 22, 45), now))
            .isEqualTo("Yesterday")

        // Same year: weekday and day, no year in the text.
        val sameYear = DateLabels.dayLabel(local(2026, Calendar.MARCH, 2, 8, 0), now)
        assertThat(sameYear).isNotEmpty()
        assertThat(sameYear).doesNotContain("2026")
        assertThat(sameYear).doesNotContain("Today")

        // Another year: the year is part of the label.
        val lastYear = DateLabels.dayLabel(local(2025, Calendar.DECEMBER, 31, 23, 59), now)
        assertThat(lastYear).contains("2025")
    }

    @Test
    fun `the row time is the visit's time of day`() {
        val label = DateLabels.timeLabel(local(2026, Calendar.NOVEMBER, 7, 9, 5))
        assertThat(label).contains("9:05")
    }

    @Test
    fun `reusing the scratch calendar does not carry state between calls`() {
        val now = local(2026, Calendar.NOVEMBER, 7, 15, 30)
        val stamp = local(2026, Calendar.JANUARY, 1, 0, 0)
        val first = DateLabels.dayLabel(stamp, now)
        assertThat(DateLabels.dayLabel(stamp, now)).isEqualTo(first)
        // Asking about "now" between two questions about the past must not move
        // the answer for the past.
        DateLabels.dayLabel(now, now)
        assertThat(DateLabels.dayLabel(stamp, now)).isEqualTo(first)
        assertThat(DateLabels.timeLabel(stamp)).isEqualTo(DateLabels.timeLabel(stamp))
    }

    private fun local(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
