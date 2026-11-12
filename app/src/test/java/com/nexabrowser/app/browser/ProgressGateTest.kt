package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rules that decide whether a progress tick is published.
 *
 * Both of these used to be an inline expression in
 * [BrowserTabManager.onProgress], and the second one was wrong there: a tick
 * from a load the user had already stopped was accepted whenever it jumped five
 * points or more, which put the tab back into "loading" with the stop button
 * showing again.
 */
class ProgressGateTest {

    @Test
    fun `the first tick of a document is always published`() {
        assertThat(ProgressGate.isMeaningful(previous = null, stopped = false, progress = 5)).isTrue()
    }

    @Test
    fun `a tick that does not move the bar enough is dropped`() {
        // 12 -> 14 is two points on a 3dp bar the user cannot see.
        assertThat(ProgressGate.isMeaningful(previous = 12, stopped = false, progress = 14)).isFalse()
    }

    @Test
    fun `a tick that moves the bar by the step is published`() {
        assertThat(ProgressGate.isMeaningful(previous = 10, stopped = false, progress = 15)).isTrue()
        assertThat(ProgressGate.isMeaningful(previous = 60, stopped = false, progress = 55)).isTrue()
    }

    @Test
    fun `completion and restart are always published`() {
        assertThat(ProgressGate.isMeaningful(previous = 99, stopped = false, progress = 100)).isTrue()
        assertThat(ProgressGate.isMeaningful(previous = 100, stopped = false, progress = 0)).isTrue()
    }

    @Test
    fun `a late tick from a stopped load never revives it`() {
        // This is the bug: after a stop the last published value is 100, so a
        // cancelled load reporting 50 is a 50-point jump and the plain throttle
        // rule publishes it — the tab went back to loading.
        assertThat(ProgressGate.isMeaningful(previous = 100, stopped = false, progress = 50)).isTrue()
        assertThat(ProgressGate.isMeaningful(previous = 100, stopped = true, progress = 50)).isFalse()
    }

    @Test
    fun `a stopped load cannot be revived even by a completion`() {
        assertThat(ProgressGate.isMeaningful(previous = 100, stopped = true, progress = 100)).isFalse()
        assertThat(ProgressGate.isMeaningful(previous = 100, stopped = true, progress = 0)).isFalse()
    }

    @Test
    fun `a new document is published again once the stop is cleared`() {
        // onPageStarted drops the stop and seeds the memo with START_PROGRESS, so
        // the next page's ticks flow normally.
        assertThat(ProgressGate.isMeaningful(previous = 5, stopped = false, progress = 20)).isTrue()
    }
}
