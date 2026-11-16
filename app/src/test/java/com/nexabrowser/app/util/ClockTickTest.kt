package com.nexabrowser.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The start-page clock's refresh period. */
class ClockTickTest {

    private val minute = ClockTick.MINUTE_MS

    @Test
    fun `just after a boundary waits almost a full minute`() {
        // 10 ms into a minute: the next redraw is the next minute, plus the
        // margin that keeps the tick off the edge.
        assertThat(ClockTick.delayToNextMinute(10L))
            .isEqualTo(minute - 10L + ClockTick.MARGIN_MS)
    }

    @Test
    fun `just before a boundary waits only the margin`() {
        assertThat(ClockTick.delayToNextMinute(minute - 1)).isEqualTo(1L + ClockTick.MARGIN_MS)
    }

    @Test
    fun `the wait is always positive and never more than a minute plus the margin`() {
        // No input may produce a zero-length sleep: the loop would spin.
        val samples = listOf(0L, 1L, 999L, minute / 2, minute - 1, minute, minute + 1, 1_700_000_000_000L)
        for (now in samples) {
            val wait = ClockTick.delayToNextMinute(now)
            assertThat(wait).isAtLeast(ClockTick.MARGIN_MS)
            assertThat(wait).isAtMost(minute + ClockTick.MARGIN_MS)
        }
    }

    @Test
    fun `the redraw lands in the next minute, not the current one`() {
        val now = 1_700_000_042_000L
        val next = now + ClockTick.delayToNextMinute(now)
        // Past the boundary of the following minute, and within the margin of it.
        assertThat(next % minute).isEqualTo(ClockTick.MARGIN_MS)
        assertThat(next).isGreaterThan(now)
    }

    @Test
    fun `a whole minute of phases is covered`() {
        var previous = -1L
        for (offset in 0 until 60) {
            val wait = ClockTick.delayToNextMinute(offset * 1000L)
            assertThat(wait).isGreaterThan(0L)
            previous = wait
        }
        assertThat(previous).isGreaterThan(0L)
    }
}
