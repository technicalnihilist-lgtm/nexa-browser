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
    fun `scheme checks ignore case without touching the url`() {
        assertThat(UrlUtils.isHttps("HTTPS://Example.com/A")).isTrue()
        assertThat(UrlUtils.isHttps("Https://example.com")).isTrue()
        assertThat(UrlUtils.isHttpUrl("HTTP://example.com")).isTrue()
        assertThat(UrlUtils.isHttpUrl("https://example.com")).isTrue()
        assertThat(UrlUtils.isHttpUrl("ftp://example.com")).isFalse()
        assertThat(UrlUtils.isHttpUrl("")).isFalse()
        // A url shorter than the scheme itself must not throw.
        assertThat(UrlUtils.isHttps("htt")).isFalse()
        assertThat(UrlUtils.isHttpUrl("h")).isFalse()
    }

    @Test
    fun `display url strips a mixed case https scheme too`() {
        // Used to keep the scheme, because removePrefix is case-sensitive while
        // isHttps is not.
        assertThat(UrlUtils.displayUrl("HTTPS://example.com/x")).isEqualTo("example.com/x")
        assertThat(UrlUtils.displayUrl("https://example.com")).isEqualTo("example.com")
        assertThat(UrlUtils.displayUrl("https://")).isEqualTo("")
        assertThat(UrlUtils.displayUrl("")).isEqualTo("")
        assertThat(UrlUtils.displayUrl("about:blank")).isEqualTo("about:blank")
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

    // The host is read straight out of the authority now, so the cases below are
    // the ones where that scanner could disagree with the URI parser it replaced.
    // Each of them feeds the favicon store (once per completed page load) and the
    // site-permission key (once per prompt), so a disagreement would be visible.

    @Test
    fun `host ignores userinfo, port, query and fragment`() {
        assertThat(UrlUtils.hostOf("https://user:pw@example.com:8443/a?b#c"))
            .isEqualTo("example.com")
        assertThat(UrlUtils.hostOf("http://example.com?q=1")).isEqualTo("example.com")
        assertThat(UrlUtils.hostOf("http://example.com#top")).isEqualTo("example.com")
        assertThat(UrlUtils.hostOf("http://example.com")).isEqualTo("example.com")
    }

    @Test
    fun `host keeps case and ipv6 brackets, as the uri parser did`() {
        assertThat(UrlUtils.hostOf("https://Example.COM/Path")).isEqualTo("Example.COM")
        assertThat(UrlUtils.hostOf("http://[::1]:8080/x")).isEqualTo("[::1]")
        assertThat(UrlUtils.hostOf("http://[::1]/x")).isEqualTo("[::1]")
        assertThat(UrlUtils.hostOf("http://127.0.0.1:9000/x")).isEqualTo("127.0.0.1")
    }

    @Test
    fun `host survives a path the uri parser rejects`() {
        // java.net.URI refuses the whole string when the path holds a raw space
        // or a non-ASCII character, and the old hostOf turned that into "".
        assertThat(UrlUtils.hostOf("https://example.com/a b/c")).isEqualTo("example.com")
        assertThat(UrlUtils.hostOf("https://example.com/\u0939\u093f\u0928\u094d\u0926\u0940"))
            .isEqualTo("example.com")
        assertThat(UrlUtils.originOf("https://example.com/\u0ba4\u0bae\u0bbf\u0bb4\u0bcd"))
            .isEqualTo("https://example.com")
    }

    @Test
    fun `host is empty where there is no authority`() {
        assertThat(UrlUtils.hostOf("about:blank")).isEqualTo("")
        assertThat(UrlUtils.hostOf("mailto:a@b.com")).isEqualTo("")
        assertThat(UrlUtils.hostOf("file:///data/x.html")).isEqualTo("")
        assertThat(UrlUtils.hostOf("")).isEqualTo("")
        assertThat(UrlUtils.hostOf("http://")).isEqualTo("")
        assertThat(UrlUtils.originOf("about:blank")).isNull()
    }

    @Test
    fun `origin keeps the port only when there is one`() {
        assertThat(UrlUtils.originOf("http://example.com:80/x")).isEqualTo("http://example.com:80")
        assertThat(UrlUtils.originOf("http://example.com:8080/x")).isEqualTo("http://example.com:8080")
        assertThat(UrlUtils.originOf("http://example.com:/x")).isEqualTo("http://example.com")
        assertThat(UrlUtils.originOf("https://example.com")).isEqualTo("https://example.com")
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

    // schemeOf used to run a regular expression (`^[a-zA-Z][a-zA-Z0-9+.\-]*:`).
    // It now scans the prefix in place, so the cases below pin down that the
    // answer is the same for every shape of input the address bar can be handed.

    @Test
    fun `scheme is read from the prefix in lower case`() {
        assertThat(UrlUtils.schemeOf("https://example.com/x")).isEqualTo("https")
        assertThat(UrlUtils.schemeOf("http://example.com")).isEqualTo("http")
        assertThat(UrlUtils.schemeOf("about:blank")).isEqualTo("about")
        assertThat(UrlUtils.schemeOf("javascript:void(0)")).isEqualTo("javascript")
        assertThat(UrlUtils.schemeOf("data:text/plain,hi")).isEqualTo("data")
        assertThat(UrlUtils.schemeOf("file:///data/x.html")).isEqualTo("file")
        assertThat(UrlUtils.schemeOf("mailto:a@b.com")).isEqualTo("mailto")
    }

    @Test
    fun `an upper case scheme is folded to lower case`() {
        assertThat(UrlUtils.schemeOf("HTTPS://example.com")).isEqualTo("https")
        assertThat(UrlUtils.schemeOf("HtTp://example.com")).isEqualTo("http")
        assertThat(UrlUtils.schemeOf("MAILTO:a@b.com")).isEqualTo("mailto")
    }

    @Test
    fun `scheme allows the characters the spec allows`() {
        assertThat(UrlUtils.schemeOf("svn+ssh://host/x")).isEqualTo("svn+ssh")
        assertThat(UrlUtils.schemeOf("web+ap://host/x")).isEqualTo("web+ap")
        assertThat(UrlUtils.schemeOf("ms-appx://host/x")).isEqualTo("ms-appx")
        assertThat(UrlUtils.schemeOf("a.b://host/x")).isEqualTo("a.b")
        assertThat(UrlUtils.schemeOf("chrome-extension://abc/x")).isEqualTo("chrome-extension")
    }

    @Test
    fun `strings without a scheme have none`() {
        assertThat(UrlUtils.schemeOf("")).isEmpty()
        assertThat(UrlUtils.schemeOf("example.com/x")).isEmpty()
        assertThat(UrlUtils.schemeOf("how does android work")).isEmpty()
        assertThat(UrlUtils.schemeOf("1http://example.com")).isEmpty()
        assertThat(UrlUtils.schemeOf(":no-scheme")).isEmpty()
        assertThat(UrlUtils.schemeOf("http//example.com")).isEmpty()
        assertThat(UrlUtils.schemeOf("has space:here")).isEmpty()
        // A scheme must be followed by a colon: a bare word is not one.
        assertThat(UrlUtils.schemeOf("localhost")).isEmpty()
    }

    @Test
    fun `a single letter scheme is still a scheme`() {
        assertThat(UrlUtils.schemeOf("a:b")).isEqualTo("a")
        assertThat(UrlUtils.schemeOf("A:b")).isEqualTo("a")
    }

    @Test
    fun `resolving an upper case cleartext url keeps it cleartext`() {
        // resolveInput passes anything with a scheme through untouched, and the
        // scheme check behind it is now the in-place scan rather than the regex.
        assertThat(UrlUtils.resolveInput("HTTP://example.com/x", google))
            .isEqualTo("HTTP://example.com/x")
    }
}
