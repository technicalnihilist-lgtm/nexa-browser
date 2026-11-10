package com.nexabrowser.app.browser

import android.webkit.WebChromeClient
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The fullscreen hand-off has to survive being called from inside itself.
 *
 * `onCustomViewHidden()` calls back into `WebChromeClient.onHideCustomView()`,
 * which the app forwards to `exitFullscreen()` again. When the callback was
 * cleared only after the call, that second entry found it still there and called
 * it again — unbounded recursion (a StackOverflowError crash) on every attempt
 * to leave a fullscreen video with the back gesture or the close button.
 */
class FullscreenSessionTest {

    /** Counts how many times it was asked to hide the custom view. */
    private class CountingCallback : WebChromeClient.CustomViewCallback {
        var hidden = 0
        override fun onCustomViewHidden() {
            hidden++
        }
    }

    @Test
    fun `take returns the callback once and forgets it`() {
        val session = FullscreenSession()
        val callback = CountingCallback()
        session.enter(callback)

        assertThat(session.take()).isSameInstanceAs(callback)
        assertThat(session.take()).isNull()
        assertThat(session.isActive).isFalse()
    }

    @Test
    fun `re-entrant exit does not hand the callback back a second time`() {
        val session = FullscreenSession()
        val callback = CountingCallback()
        session.enter(callback)

        // Outer exit: the page's onHideCustomView() runs from inside
        // onCustomViewHidden(), i.e. before this call returns.
        val first = session.take()
        // Inner exit, as the forwarded onHideCustomView() would perform it.
        val second = session.take()

        assertThat(first).isSameInstanceAs(callback)
        assertThat(second).isNull()
    }

    @Test
    fun `a second fullscreen view replaces the first`() {
        val session = FullscreenSession()
        val first = CountingCallback()
        val second = CountingCallback()
        session.enter(first)
        session.enter(second)

        assertThat(session.take()).isSameInstanceAs(second)
        assertThat(session.take()).isNull()
        assertThat(first.hidden).isEqualTo(0)
    }

    @Test
    fun `a session that was exited can be entered again`() {
        val session = FullscreenSession()
        session.enter(CountingCallback())
        session.take()

        val next = CountingCallback()
        session.enter(next)

        assertThat(session.isActive).isTrue()
        assertThat(session.take()).isSameInstanceAs(next)
    }

    @Test
    fun `taking from an empty session is harmless`() {
        val session = FullscreenSession()
        assertThat(session.take()).isNull()
        assertThat(session.isActive).isFalse()
    }
}
