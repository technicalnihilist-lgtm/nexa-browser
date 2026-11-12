package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import com.nexabrowser.app.data.prefs.BrowserSettings
import org.junit.Test

/**
 * Pins down the stylesheet [PageInjections.cssFor] builds, and the fact that the
 * default configuration builds nothing at all.
 *
 * `cssFor` runs on every completed page load of every tab and on every settings
 * emission, so the cheap path matters: with no page extension enabled it must
 * return without allocating. What it returns must be byte-for-byte what the
 * previous list-and-join implementation produced, because it is injected into
 * the page as-is.
 */
class PageInjectionsTest {

    private val base = BrowserSettings()

    @Test
    fun `no page extension means no stylesheet`() {
        assertThat(PageInjections.cssFor(base)).isEmpty()
    }

    @Test
    fun `a custom stylesheet that is switched off is not injected`() {
        val off = base.copy(customCss = "body{color:red}", customCssEnabled = false)
        assertThat(PageInjections.cssFor(off)).isEmpty()
    }

    @Test
    fun `a custom stylesheet that is switched on is injected verbatim`() {
        val on = base.copy(customCss = "body{color:red}", customCssEnabled = true)
        assertThat(PageInjections.cssFor(on)).isEqualTo("body{color:red}")
    }

    @Test
    fun `dark pages alone inject the dark stylesheet`() {
        val dark = base.copy(darkPagesEnabled = true)
        assertThat(PageInjections.cssFor(dark)).isEqualTo(PageInjections.DARK_PAGES_CSS)
    }

    @Test
    fun `dark pages and custom css are joined with a newline`() {
        val both = base.copy(darkPagesEnabled = true, customCssEnabled = true, customCss = "body{margin:0}")
        assertThat(PageInjections.cssFor(both))
            .isEqualTo(PageInjections.DARK_PAGES_CSS + "\n" + "body{margin:0}")
    }

    @Test
    fun `a blank custom stylesheet adds nothing to dark pages`() {
        val dark = base.copy(darkPagesEnabled = true, customCssEnabled = true, customCss = "   ")
        assertThat(PageInjections.cssFor(dark)).isEqualTo(PageInjections.DARK_PAGES_CSS)
    }

    @Test
    fun `the same settings hand back the same instance`() {
        val both = base.copy(darkPagesEnabled = true, customCssEnabled = true, customCss = "body{margin:0}")
        val first = PageInjections.cssFor(both)
        val second = PageInjections.cssFor(both.copy(themeMode = base.themeMode))
        assertThat(second).isSameInstanceAs(first)
    }

    @Test
    fun `a preference that does not change the stylesheet does not rebuild it`() {
        val on = base.copy(customCssEnabled = true, customCss = "a{color:blue}")
        val first = PageInjections.cssFor(on)
        val afterThemeChange = PageInjections.cssFor(on.copy(accent = com.nexabrowser.app.domain.model.AccentPreset.TEAL))
        assertThat(afterThemeChange).isSameInstanceAs(first)
    }

    @Test
    fun `changing the stylesheet itself rebuilds it`() {
        val one = base.copy(customCssEnabled = true, customCss = "a{color:blue}")
        PageInjections.cssFor(one)
        val two = one.copy(customCss = "a{color:green}")
        assertThat(PageInjections.cssFor(two)).isEqualTo("a{color:green}")
    }

    @Test
    fun `switching every extension off clears a previously built stylesheet`() {
        PageInjections.cssFor(base.copy(darkPagesEnabled = true, customCssEnabled = true, customCss = "a{}"))
        assertThat(PageInjections.cssFor(base)).isEmpty()
    }
}
