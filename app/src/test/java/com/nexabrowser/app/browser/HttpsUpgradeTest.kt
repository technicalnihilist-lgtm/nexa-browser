package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The force-HTTPS rewrite, which used to be an inline `replaceFirst("http://",
 * "https://")`.
 *
 * The case-insensitive behaviour is the part that matters: the branch is entered
 * on a lower-cased scheme, so an upper-case url reached the rewrite too, and a
 * case-sensitive replacement returned it unchanged — which the caller then
 * handed straight back to `loadUrl()`, firing the same callback again.
 */
class HttpsUpgradeTest {

    @Test
    fun `a plain cleartext url is upgraded`() {
        assertThat(HttpsUpgrade.upgraded("http://example.com/a?b=1"))
            .isEqualTo("https://example.com/a?b=1")
    }

    @Test
    fun `an upper case scheme is upgraded as well`() {
        // The bug: `scheme` was lower-cased to decide the upgrade, but the rewrite
        // matched case-sensitively, so this came back unchanged and the navigation
        // looped instead of being upgraded.
        assertThat(HttpsUpgrade.upgraded("HTTP://example.com/page"))
            .isEqualTo("https://example.com/page")
        assertThat(HttpsUpgrade.upgraded("HtTp://example.com/page"))
            .isEqualTo("https://example.com/page")
    }

    @Test
    fun `an https url is never rewritten`() {
        assertThat(HttpsUpgrade.upgraded("https://example.com")).isNull()
        assertThat(HttpsUpgrade.upgraded("HTTPS://example.com")).isNull()
    }

    @Test
    fun `an opaque http url is left alone`() {
        // A Uri reports the scheme "http" for this but there is no authority to
        // keep, so splicing six characters off would produce "https://oo".
        assertThat(HttpsUpgrade.upgraded("http:foo")).isNull()
    }

    @Test
    fun `the path and query survive unchanged`() {
        assertThat(HttpsUpgrade.upgraded("http://example.com/p?q=1#frag"))
            .isEqualTo("https://example.com/p?q=1#frag")
    }

    @Test
    fun `userinfo and port survive unchanged`() {
        assertThat(HttpsUpgrade.upgraded("http://user:pw@example.com:8080/x"))
            .isEqualTo("https://user:pw@example.com:8080/x")
    }

    @Test
    fun `short and empty inputs are refused`() {
        assertThat(HttpsUpgrade.upgraded("")).isNull()
        assertThat(HttpsUpgrade.upgraded("http://")).isNull()
        assertThat(HttpsUpgrade.upgraded("http:/")).isNull()
        assertThat(HttpsUpgrade.upgraded("ftp://example.com")).isNull()
    }

    @Test
    fun `upgrading twice is idempotent`() {
        val once = HttpsUpgrade.upgraded("http://example.com/x")
        assertThat(once).isNotNull()
        assertThat(HttpsUpgrade.upgraded(once!!)).isNull()
    }
}
