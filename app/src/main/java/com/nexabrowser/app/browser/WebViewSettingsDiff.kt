package com.nexabrowser.app.browser

import com.nexabrowser.app.data.prefs.BrowserSettings

/**
 * Which `WebView.settings` fields a preference change actually touches.
 *
 * [everything] means "the WebView has never been configured, write the lot";
 * every other flag names one field (or one closely-coupled pair) that a live
 * view has to be told about. Kept free of Android types so the decision is unit
 * tested rather than assumed.
 */
internal data class WebViewSettingChanges(
    val everything: Boolean = false,
    val javaScript: Boolean = false,
    val popups: Boolean = false,
    val images: Boolean = false,
    val textZoom: Boolean = false,
    val userAgent: Boolean = false,
    val cookies: Boolean = false,
    val doNotTrack: Boolean = false
) {
    /** True when there is anything at all to push into the WebView. */
    val any: Boolean
        get() = everything || javaScript || popups || images ||
            textZoom || userAgent || cookies || doNotTrack
}

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
 * the rest are drawn by Compose and need no WebView work at all.
 *
 * Knowing *which* field changed, rather than only *that* something changed, is
 * the second half of the same saving. The whole snapshot used to be written
 * again for a single changed field: moving the text-zoom slider one step still
 * rewrote the JavaScript flag, the popup flag, the image policy and the user
 * agent — each one a call into the WebView provider — on every open tab. Now a
 * change writes exactly the fields it changed, so one preference is one write
 * per tab instead of twenty.
 */
internal object WebViewSettingsDiff {

    /**
     * True when [next] has to be pushed into the WebView — either because
     * nothing has been applied to this tab yet ([previous] is null) or because a
     * field the WebView reads actually changed.
     */
    fun affectsWebView(previous: BrowserSettings?, next: BrowserSettings): Boolean =
        changes(previous, next).any

    /**
     * The fields that differ between [previous] and [next].
     *
     * A null [previous] means this WebView has never been configured, so every
     * field is reported.
     */
    fun changes(previous: BrowserSettings?, next: BrowserSettings): WebViewSettingChanges {
        if (previous == null) return WebViewSettingChanges(everything = true)
        return WebViewSettingChanges(
            javaScript = previous.javaScriptEnabled != next.javaScriptEnabled,
            popups = previous.popupsEnabled != next.popupsEnabled,
            images = previous.dataSaverEnabled != next.dataSaverEnabled,
            textZoom = previous.textZoom != next.textZoom,
            userAgent = previous.desktopSiteByDefault != next.desktopSiteByDefault,
            cookies = previous.cookiesEnabled != next.cookiesEnabled,
            doNotTrack = previous.doNotTrackEnabled != next.doNotTrackEnabled
        )
    }
}
