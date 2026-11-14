package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import com.nexabrowser.app.data.prefs.BrowserSettings
import com.nexabrowser.app.domain.model.AccentPreset
import com.nexabrowser.app.domain.model.AddressBarStyle
import com.nexabrowser.app.domain.model.CornerStyle
import com.nexabrowser.app.domain.model.ThemeMode
import org.junit.Test

class WebViewSettingsDiffTest {

    private val base = BrowserSettings()

    @Test
    fun `nothing applied yet always reaches the webview`() {
        assertThat(WebViewSettingsDiff.affectsWebView(null, base)).isTrue()
    }

    @Test
    fun `identical settings cost no webview work`() {
        assertThat(WebViewSettingsDiff.affectsWebView(base, base.copy())).isFalse()
    }

    @Test
    fun `purely visual preferences never reach the webview`() {
        val visualOnly = listOf(
            base.copy(themeMode = ThemeMode.DARK),
            base.copy(accent = AccentPreset.TEAL),
            base.copy(corners = CornerStyle.SQUARE),
            base.copy(addressBarStyle = AddressBarStyle.SOLID_LIGHT),
            base.copy(startPageBackground = "aurora"),
            base.copy(showClockOnStartPage = false),
            base.copy(homepageUrl = "https://example.com"),
            base.copy(searchEngineId = "duckduckgo"),
            base.copy(cleanLinksEnabled = false),
            base.copy(saveHistoryEnabled = false),
            base.copy(sessionRestoreEnabled = false),
            base.copy(downloadOverWifiOnly = true),
            base.copy(showDownloadNotifications = false),
            base.copy(forceHttpsEnabled = false),
            base.copy(adBlockEnabled = false),
            base.copy(darkPagesEnabled = true),
            base.copy(customCssEnabled = true),
            base.copy(customCss = "body{color:red}")
        )
        visualOnly.forEach { changed ->
            assertThat(WebViewSettingsDiff.affectsWebView(base, changed)).isFalse()
        }
    }

    @Test
    fun `every field the webview reads is detected`() {
        val webViewFields = listOf(
            base.copy(javaScriptEnabled = false),
            base.copy(popupsEnabled = true),
            base.copy(cookiesEnabled = false),
            base.copy(dataSaverEnabled = true),
            base.copy(textZoom = 150),
            base.copy(desktopSiteByDefault = true),
            base.copy(doNotTrackEnabled = false)
        )
        webViewFields.forEach { changed ->
            assertThat(WebViewSettingsDiff.affectsWebView(base, changed)).isTrue()
        }
    }

    @Test
    fun `an unconfigured webview takes the whole snapshot`() {
        val changes = WebViewSettingsDiff.changes(null, base)
        assertThat(changes.everything).isTrue()
        assertThat(changes.any).isTrue()
    }

    @Test
    fun `identical settings produce no field writes at all`() {
        val changes = WebViewSettingsDiff.changes(base, base.copy())
        assertThat(changes.everything).isFalse()
        assertThat(changes.any).isFalse()
    }

    @Test
    fun `each webview field maps to exactly one group`() {
        val cases = mapOf(
            base.copy(javaScriptEnabled = false) to { c: WebViewSettingChanges -> c.javaScript },
            base.copy(popupsEnabled = true) to { c: WebViewSettingChanges -> c.popups },
            base.copy(dataSaverEnabled = true) to { c: WebViewSettingChanges -> c.images },
            base.copy(textZoom = 150) to { c: WebViewSettingChanges -> c.textZoom },
            base.copy(desktopSiteByDefault = true) to { c: WebViewSettingChanges -> c.userAgent },
            base.copy(cookiesEnabled = false) to { c: WebViewSettingChanges -> c.cookies },
            base.copy(doNotTrackEnabled = false) to { c: WebViewSettingChanges -> c.doNotTrack }
        )
        cases.forEach { (changed, flag) ->
            val changes = WebViewSettingsDiff.changes(base, changed)
            assertThat(flag(changes)).isTrue()
            // Exactly one group moved: that is what makes a single preference
            // one native write per tab instead of a whole snapshot.
            val moved = listOf(
                changes.javaScript,
                changes.popups,
                changes.images,
                changes.textZoom,
                changes.userAgent,
                changes.cookies,
                changes.doNotTrack
            ).count { it }
            assertThat(moved).isEqualTo(1)
        }
    }

    @Test
    fun `purely visual preferences produce no field writes`() {
        val visualOnly = base.copy(
            themeMode = ThemeMode.DARK,
            accent = AccentPreset.TEAL,
            corners = CornerStyle.SQUARE,
            addressBarStyle = AddressBarStyle.SOLID_LIGHT,
            startPageBackground = "aurora",
            showClockOnStartPage = false,
            homepageUrl = "https://example.com",
            adBlockEnabled = false,
            darkPagesEnabled = true,
            customCssEnabled = true,
            customCss = "body{color:red}",
            forceHttpsEnabled = false
        )
        assertThat(WebViewSettingsDiff.changes(base, visualOnly).any).isFalse()
    }

    @Test
    fun `the desktop user agent follows the tab's own choice`() {
        assertThat(BrowserWebViewFactory.userAgentFor(true))
            .isEqualTo(BrowserWebViewFactory.DESKTOP_UA)
        assertThat(BrowserWebViewFactory.userAgentFor(false)).isNull()
    }
}
