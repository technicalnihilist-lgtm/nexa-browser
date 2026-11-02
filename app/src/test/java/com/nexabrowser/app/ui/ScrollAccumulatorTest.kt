package com.nexabrowser.app.ui

import com.google.common.truth.Truth.assertThat
import com.nexabrowser.app.ui.components.ScrollAccumulator
import org.junit.Test

/**
 * Pins down the toolbar hide/show decision that used to live inline in
 * BrowserScreen, so moving it out of snapshot state provably did not change
 * when the chrome slides away — only how often it is asked to.
 */
class ScrollAccumulatorTest {

    private val hide = 26
    private val show = 12
    private fun accumulator() = ScrollAccumulator(hideThresholdPx = hide, showThresholdPx = show)

    @Test
    fun `small scrolls below the threshold report no change`() {
        val acc = accumulator()
        var changes = 0
        repeat(5) { changes += if (acc.onScroll(scrollY = 10 * it + 10, delta = 5)) 1 else 0 }
        assertThat(changes).isEqualTo(0)
        assertThat(acc.hidden).isFalse()
    }

    @Test
    fun `a downward gesture past the threshold hides once`() {
        val acc = accumulator()
        assertThat(acc.onScroll(scrollY = 10, delta = 10)).isFalse()
        assertThat(acc.onScroll(scrollY = 20, delta = 10)).isFalse()
        // 30 > 26: this is the only event that has to reach the UI.
        assertThat(acc.onScroll(scrollY = 30, delta = 10)).isTrue()
        assertThat(acc.hidden).isTrue()
        // Still hidden: further downward scrolling must not re-notify.
        assertThat(acc.onScroll(scrollY = 60, delta = 30)).isFalse()
        assertThat(acc.onScroll(scrollY = 90, delta = 30)).isFalse()
    }

    @Test
    fun `reversing direction starts counting again from zero`() {
        val acc = accumulator()
        acc.onScroll(scrollY = 10, delta = 20) // 20, still below the hide threshold
        // 20 down then 12 up must not be treated as 8 net pixels of scrolling up.
        assertThat(acc.onScroll(scrollY = 0 + 5, delta = -12)).isFalse()
        assertThat(acc.hidden).isFalse()
    }

    @Test
    fun `scrolling up from a hidden toolbar shows it once`() {
        val acc = accumulator()
        acc.onScroll(scrollY = 10, delta = 40)
        assertThat(acc.hidden).isTrue()

        assertThat(acc.onScroll(scrollY = 40, delta = -5)).isFalse()
        assertThat(acc.onScroll(scrollY = 35, delta = -8)).isTrue() // -13 < -12
        assertThat(acc.hidden).isFalse()
        assertThat(acc.onScroll(scrollY = 20, delta = -40)).isFalse()
    }

    @Test
    fun `reaching the top always shows the toolbar`() {
        val acc = accumulator()
        acc.onScroll(scrollY = 10, delta = 40)
        assertThat(acc.hidden).isTrue()

        assertThat(acc.onScroll(scrollY = 0, delta = -40)).isTrue()
        assertThat(acc.hidden).isFalse()
        // Already shown: another event at the top reports nothing.
        assertThat(acc.onScroll(scrollY = 0, delta = -5)).isFalse()
    }

    @Test
    fun `reset forgets the gesture and the hidden state`() {
        val acc = accumulator()
        acc.onScroll(scrollY = 10, delta = 40)
        acc.reset()
        assertThat(acc.hidden).isFalse()
        // A leftover +20 must not carry into the next page.
        assertThat(acc.onScroll(scrollY = 5, delta = 20)).isFalse()
    }
}
