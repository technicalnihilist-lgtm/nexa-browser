package com.nexabrowser.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The address bar's behaviour is the single most user-visible piece of pure
 * logic in the app, so it is covered directly here.
 */
class UrlUtilsTest {

    private val google = "https://www.google.com/search?q=%s"

    @Test
    fun `full https url is passed through unchanged`() {
        assertThat(UrlUtils.resolveInput("https://example.com/a?b=1", google))
            .isEqualTo("https://example.com/a?b=1")
    }

    @Test
    fun `full http url is passed through unchanged`() {
        assertThat(UrlUtils.resolveInput("http://example.com", google))
            .isEqualTo("http://example.com")
    }

    @Test
    fun `bare domain gets https`() {
        assertThat(UrlUtils.resolveInput("example.com", google)).isEqualTo("https://example.com")
    }

    @Test
    fun `bare domain with path gets https`() {
        assertThat(UrlUtils.resolveInput("example.com/docs/intro", google))
            .isEqualTo("https://example.com/docs/intro")
    }

    @Test
    fun `localhost gets http`() {
        assertThat(UrlUtils.resolveInput("localhost:8080", google)).isEqualTo("http://localhost:8080")
    }

    @Test
    fun `private ip gets http`() {
        assertThat(UrlUtils.resolveInput("192.168.1.10", google)).isEqualTo("http://192.168.1.10")
    }

    @Test
    fun `plain phrase becomes a search`() {
        assertThat(UrlUtils.resolveInput("how does android work", google))
            .isEqualTo("https://www.google.com/search?q=how+does+android+work")
    }

    @Test
    fun `single word becomes a search`() {
        assertThat(UrlUtils.resolveInput("kotlin", google))
            .isEqualTo("https://www.google.com/search?q=kotlin")
    }

    @Test
    fun `query is percent encoded`() {
        assertThat(UrlUtils.searchUrl("a&b=c", google))
            .isEqualTo("https://www.google.com/search?q=a%26b%3Dc")
    }

    @Test
    fun `custom template without placeholder still works`() {
        assertThat(UrlUtils.searchUrl("cats", "https://example.org/find?q="))
            .isEqualTo("https://example.org/find?q=cats")
    }

    @Test
    fun `mailto is kept verbatim so the platform can handle it`() {
        assertThat(UrlUtils.resolveInput("mailto:a@b.com", google)).isEqualTo("mailto:a@b.com")
        assertThat(UrlUtils.isSafeExternalScheme(UrlUtils.schemeOf("mailto:a@b.com"))).isTrue()
    }

    @Test
    fun `unknown schemes are not offered to the platform`() {
        assertThat(UrlUtils.isSafeExternalScheme("weird")).isFalse()
        assertThat(UrlUtils.isSafeExternalScheme("javascript")).isFalse()
    }

    @Test
    fun `https is recognised as secure and http is not`() {
        assertThat(UrlUtils.isHttps("https://a.com")).isTrue()
        assertThat(UrlUtils.isHttps("http://a.com")).isFalse()
        assertThat(UrlUtils.isHttps("a.com")).isFalse()
    }

    @Test
    fun `display url hides the https scheme but keeps http visible`() {
        assertThat(UrlUtils.displayUrl("https://example.com/x")).isEqualTo("example.com/x")
        assertThat(UrlUtils.displayUrl("http://example.com/x")).isEqualTo("http://example.com/x")
    }

    @Test
    fun `origin drops path and keeps port`() {
        assertThat(UrlUtils.originOf("https://example.com:8443/a/b?c=1"))
            .isEqualTo("https://example.com:8443")
        assertThat(UrlUtils.originOf("https://example.com/a")).isEqualTo("https://example.com")
    }

    @Test
    fun `host extraction`() {
        assertThat(UrlUtils.hostOf("https://sub.example.com/path")).isEqualTo("sub.example.com")
        assertThat(UrlUtils.hostOf("not a url")).isEqualTo("")
    }

    @Test
    fun `loadable url rejects nonsense and accepts web schemes`() {
        assertThat(UrlUtils.isLoadableUrl("https://example.com")).isTrue()
        assertThat(UrlUtils.isLoadableUrl("file:///data/x.html")).isTrue()
        assertThat(UrlUtils.isLoadableUrl("about:blank")).isTrue()
        assertThat(UrlUtils.isLoadableUrl("example.com")).isFalse()
        assertThat(UrlUtils.isLoadableUrl("mailto:a@b.com")).isFalse()
        assertThat(UrlUtils.isLoadableUrl("")).isFalse()
    }

    @Test
    fun `blank input resolves to blank`() {
        assertThat(UrlUtils.resolveInput("   ", google)).isEmpty()
    }
}
