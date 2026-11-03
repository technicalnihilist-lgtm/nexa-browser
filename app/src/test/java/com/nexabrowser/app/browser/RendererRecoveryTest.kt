package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The reload budget that keeps a renderer crash from becoming a crash loop.
 * These tests pin the rule: recover a tab that dies, stop recovering one that
 * keeps dying.
 */
class RendererRecoveryTest {

    private val first = 1_000_000L

    @Test
    fun `first loss starts a run and is reloaded`() {
        val record = RendererRecovery.record(null, first)
        assertThat(record.count).isEqualTo(1)
        assertThat(record.firstLossAt).isEqualTo(first)
        assertThat(RendererRecovery.shouldReload(record)).isTrue()
    }

    @Test
    fun `a second loss inside the window is still reloaded`() {
        val second = RendererRecovery.record(RendererRecovery.record(null, first), first + 5_000)
        assertThat(second.count).isEqualTo(2)
        assertThat(second.firstLossAt).isEqualTo(first)
        assertThat(RendererRecovery.shouldReload(second)).isTrue()
    }

    @Test
    fun `a tab that keeps dying stops being reloaded`() {
        var record = RendererRecovery.record(null, first)
        repeat(RendererRecovery.MAX_AUTOMATIC_RELOADS) {
            record = RendererRecovery.record(record, first + 1_000)
        }
        assertThat(record.count).isEqualTo(RendererRecovery.MAX_AUTOMATIC_RELOADS + 1)
        assertThat(RendererRecovery.shouldReload(record)).isFalse()
    }

    @Test
    fun `a loss after the window starts a fresh budget`() {
        var record = RendererRecovery.record(null, first)
        repeat(5) { record = RendererRecovery.record(record, first + 1_000) }
        assertThat(RendererRecovery.shouldReload(record)).isFalse()

        val later = RendererRecovery.record(record, first + RendererRecovery.RETRY_WINDOW_MS + 1)
        assertThat(later.count).isEqualTo(1)
        assertThat(later.firstLossAt).isEqualTo(first + RendererRecovery.RETRY_WINDOW_MS + 1)
        assertThat(RendererRecovery.shouldReload(later)).isTrue()
    }

    @Test
    fun `a loss exactly on the window edge still counts as the same run`() {
        val edge = RendererRecovery.record(
            RendererRecovery.LossRecord(firstLossAt = first, count = 1),
            first + RendererRecovery.RETRY_WINDOW_MS
        )
        assertThat(edge.count).isEqualTo(2)
        assertThat(edge.firstLossAt).isEqualTo(first)
    }

    @Test
    fun `a clock that jumps backwards does not extend the run`() {
        val record = RendererRecovery.record(
            RendererRecovery.LossRecord(firstLossAt = first, count = 3),
            first - 10_000
        )
        assertThat(record.count).isEqualTo(1)
        assertThat(record.firstLossAt).isEqualTo(first - 10_000)
    }
}
