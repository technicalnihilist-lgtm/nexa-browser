package com.nexabrowser.app.browser

import android.webkit.WebChromeClient

/**
 * The custom-view callback of the page that is currently fullscreen.
 *
 * Leaving fullscreen used to call `onCustomViewHidden()` and clear the field
 * afterwards. That callback calls straight back into
 * `WebChromeClient.onHideCustomView()`, which the app forwards to
 * `exitFullscreen()` again — and the field was still set at that point, so the
 * same call was made again, and again. The back gesture or the close button
 * while a video was fullscreen therefore recursed into itself until the process
 * died with a `StackOverflowError`: fullscreen HTML video could not be left
 * without the app going down.
 *
 * Taking the callback *out* of the session before it is called makes that second
 * entry find nothing to do, so leaving fullscreen is exactly one call however
 * many paths ask for it — the back gesture, the close button, and the page's own
 * `exitFullscreen()` all end up here.
 *
 * Kept free of Android behaviour (only the callback interface is referenced) so
 * the re-entrancy rule is unit tested rather than inferred from a device.
 */
class FullscreenSession {

    private var callback: WebChromeClient.CustomViewCallback? = null

    /** True while a fullscreen view is waiting to be hidden. */
    val isActive: Boolean get() = callback != null

    /**
     * Records the callback for the page that just went fullscreen. A page that
     * starts a second fullscreen video replaces the first one, which is what the
     * single-custom-view rule of WebChromeClient expects.
     */
    fun enter(callback: WebChromeClient.CustomViewCallback) {
        this.callback = callback
    }

    /**
     * The callback to hide, or null when fullscreen is already being left.
     *
     * The callback is cleared before it is handed back, so the re-entrant call
     * that `onCustomViewHidden()` triggers returns null instead of recursing.
     */
    fun take(): WebChromeClient.CustomViewCallback? {
        val taken = callback
        callback = null
        return taken
    }
}
