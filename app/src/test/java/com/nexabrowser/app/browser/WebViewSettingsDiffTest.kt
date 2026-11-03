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
    fun `the desktop user agent follows the tab's own choice`() {
        assertThat(BrowserWebViewFactory.userAgentFor(true))
            .isEqualTo(BrowserWebViewFactory.DESKTOP_UA)
        assertThat(BrowserWebViewFactory.userAgentFor(false)).isNull()
    }
}
