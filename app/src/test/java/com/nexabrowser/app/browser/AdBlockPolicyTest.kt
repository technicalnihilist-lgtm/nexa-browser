package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The ad blocker is a subresource filter, and must never answer the page itself.
 *
 * Answering the top-level document with the empty response gave the user a blank
 * white screen with no error, no overlay and no way to tell the navigation had
 * been blocked — and reloading it repeated the same blank screen.
 */
class AdBlockPolicyTest {

    @Test
    fun `a blocked subresource is answered by the blocker`() {
        assertThat(
            AdBlockPolicy.shouldIntercept(isForMainFrame = false, adBlockEnabled = true, blocked = true)
        ).isTrue()
    }

    @Test
    fun `a blocked top-level document is left to the network`() {
        assertThat(
            AdBlockPolicy.shouldIntercept(isForMainFrame = true, adBlockEnabled = true, blocked = true)
        ).isFalse()
    }

    @Test
    fun `nothing is intercepted when the preference is off`() {
        assertThat(
            AdBlockPolicy.shouldIntercept(isForMainFrame = false, adBlockEnabled = false, blocked = true)
        ).isFalse()
        assertThat(
            AdBlockPolicy.shouldIntercept(isForMainFrame = true, adBlockEnabled = false, blocked = true)
        ).isFalse()
    }

    @Test
    fun `a host that is not on the list is never intercepted`() {
        assertThat(
            AdBlockPolicy.shouldIntercept(isForMainFrame = false, adBlockEnabled = true, blocked = false)
        ).isFalse()
        assertThat(
            AdBlockPolicy.shouldIntercept(isForMainFrame = true, adBlockEnabled = true, blocked = false)
        ).isFalse()
    }
}
