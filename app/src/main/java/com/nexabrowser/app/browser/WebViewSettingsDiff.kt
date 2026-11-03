package com.nexabrowser.app.browser

import com.nexabrowser.app.data.prefs.BrowserSettings

/**
 * Decides which preference changes actually have to reach a live WebView.
 *
 * [BrowserTabManager.applySettings] runs on every emission of the settings flow,
 * and that flow re-emits on *every* preference write — a theme switch, an accent
 * change, a corner style, a new start-page wallpaper, the clock toggle, the
 * text-zoom slider. Pushing a snapshot into a WebView means roughly twenty
 * WebSettings writes per tab plus, when Do Not Track is on, an
 * `evaluateJavascript` call that crosses into the renderer process. With ten
 * tabs open, changing an unrelated colour used to cost ten JavaScript round
 * trips and two hundred native setting writes.
 *
 * Only the fields below are read by `WebView.settings` or by the cookie manager;
 * the rest are drawn by Compose and need no WebView work at all. Kept free of
 * Android types so the rule is unit tested rather than assumed.
 */
internal object WebViewSettingsDiff {

    /**
     * True when [next] has to be pushed into the WebView — either because
     * nothing has been applied to this tab yet ([previous] is null) or because a
     * field the WebView reads actually changed.
     */
    fun affectsWebView(previous: BrowserSettings?, next: BrowserSettings): Boolean {
        if (previous == null) return true
        return previous.javaScriptEnabled != next.javaScriptEnabled ||
            previous.popupsEnabled != next.popupsEnabled ||
            previous.cookiesEnabled != next.cookiesEnabled ||
            previous.dataSaverEnabled != next.dataSaverEnabled ||
            previous.textZoom != next.textZoom ||
            previous.desktopSiteByDefault != next.desktopSiteByDefault ||
            previous.doNotTrackEnabled != next.doNotTrackEnabled
    }
}
