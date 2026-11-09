package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * `WebView.reload()` on a blank document does nothing, so a tab that was left
 * parked on `about:blank` while still holding its url — a tab whose renderer
 * died and could not be reloaded straight away — could not be recovered from
 * its error overlay at all. The rule that decides when the tab's own url is
 * loaded instead is pinned here.
 */
class ReloadTargetTest {

    @Test
    fun `a loaded page is reloaded, not re-navigated`() {
        assertThat(ReloadTarget.hasDocument("https://example.com/article")).isTrue()
        assertThat(ReloadTarget.urlFor("https://example.com/article", "https://example.com/article"))
            .isNull()
    }

    @Test
    fun `a blank document falls back to the tab's url`() {
        assertThat(ReloadTarget.urlFor("about:blank", "https://example.com/article"))
            .isEqualTo("https://example.com/article")
    }

    @Test
    fun `a webview with no url at all falls back to the tab's url`() {
        assertThat(ReloadTarget.urlFor(null, "https://example.com/article"))
            .isEqualTo("https://example.com/article")
        assertThat(ReloadTarget.urlFor("", "https://example.com/article"))
            .isEqualTo("https://example.com/article")
    }

    @Test
    fun `a genuinely empty tab has nothing to load`() {
        assertThat(ReloadTarget.urlFor(null, "")).isNull()
        assertThat(ReloadTarget.urlFor("about:blank", "")).isNull()
        assertThat(ReloadTarget.urlFor(null, "about:blank")).isNull()
    }

    @Test
    fun `blank and null are not documents`() {
        assertThat(ReloadTarget.hasDocument(null)).isFalse()
        assertThat(ReloadTarget.hasDocument("")).isFalse()
        assertThat(ReloadTarget.hasDocument("about:blank")).isFalse()
    }
}
