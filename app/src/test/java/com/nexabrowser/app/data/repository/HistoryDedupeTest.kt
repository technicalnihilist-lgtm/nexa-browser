package com.nexabrowser.app.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the rule a repeat visit is folded into an existing history row by.
 *
 * The bug this guards is the icon: the fold used to write the timestamp and the
 * title only, so the icon a reload had just written to disk was dropped and the
 * row kept the `null` it was inserted with. A site visited once and then
 * reloaded therefore kept a letter avatar in history for good.
 */
class HistoryDedupeTest {

    @Test
    fun `a visit inside the window is a repeat`() {
        assertThat(HistoryDedupe.isRepeat(latestVisitedAt = 1_000L, now = 1_000L)).isTrue()
        assertThat(HistoryDedupe.isRepeat(latestVisitedAt = 1_000L, now = 1_000L + 29_999L)).isTrue()
    }

    @Test
    fun `a visit at or past the window starts a new row`() {
        assertThat(HistoryDedupe.isRepeat(latestVisitedAt = 0L, now = HistoryDedupe.WINDOW_MS)).isFalse()
        assertThat(HistoryDedupe.isRepeat(latestVisitedAt = 0L, now = HistoryDedupe.WINDOW_MS + 1L)).isFalse()
    }

    /**
     * A clock that moved backwards (a time-zone change, an NTP correction) leaves
     * the difference negative. Folding is the safe answer: inserting would write
     * a timestamp in the past, which lands the row in the wrong place in a list
     * sorted by `visitedAt`.
     */
    @Test
    fun `a visit stamped before the stored one is still a repeat`() {
        assertThat(HistoryDedupe.isRepeat(latestVisitedAt = 5_000L, now = 4_000L)).isTrue()
    }

    @Test
    fun `the window is thirty seconds`() {
        assertThat(HistoryDedupe.WINDOW_MS).isEqualTo(30_000L)
    }

    // ------------------------------------------------------------------ title

    @Test
    fun `an empty incoming title keeps the stored one`() {
        assertThat(HistoryDedupe.titleFor(existing = "Example", incoming = "")).isEqualTo("Example")
        assertThat(HistoryDedupe.titleFor(existing = "Example", incoming = "   ")).isEqualTo("Example")
    }

    @Test
    fun `a real incoming title wins`() {
        assertThat(HistoryDedupe.titleFor(existing = "Old", incoming = "New")).isEqualTo("New")
    }

    // ------------------------------------------------------------------- icon

    /**
     * The regression this file exists for: the first load of a page finishes
     * before the site delivers its icon, so the row is inserted with no icon. The
     * reload that follows has the icon — and it must reach the row.
     */
    @Test
    fun `an incoming icon fills a row that has none`() {
        assertThat(HistoryDedupe.iconFor(existing = null, incoming = "/cache/favicons/a.png"))
            .isEqualTo("/cache/favicons/a.png")
    }

    @Test
    fun `a load with no icon does not erase the stored one`() {
        assertThat(HistoryDedupe.iconFor(existing = "/cache/favicons/a.png", incoming = null))
            .isEqualTo("/cache/favicons/a.png")
    }

    @Test
    fun `a newer icon replaces the stored one`() {
        assertThat(HistoryDedupe.iconFor(existing = "/cache/favicons/old.png", incoming = "/cache/favicons/new.png"))
            .isEqualTo("/cache/favicons/new.png")
    }

    @Test
    fun `two loads with no icon leave the row without one`() {
        assertThat(HistoryDedupe.iconFor(existing = null, incoming = null)).isNull()
    }
}
