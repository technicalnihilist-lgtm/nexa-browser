package com.nexabrowser.app

import android.app.Application
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import com.nexabrowser.app.browser.AdBlocker

/**
 * Application entry point. Keeps global WebView setup in one place and performs
 * a cheap warm-up so the first page load is faster.
 */
class NexaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Read the bundled blocklist once, off the first page load.
        AdBlocker.ensureLoaded(this)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Warm up the WebView provider so the first real tab does not pay for it.
        // Only the provider initialisation is wanted here: constructing the view
        // starts Chromium, which is the expensive part. The old version also
        // called loadUrl("about:blank") and destroyed the view immediately, which
        // starts a navigation on an instance that is about to be torn down — the
        // documented cause of "destroy() called while still loading" crashes on
        // several OEM builds — for no benefit at all.
        runCatching {
            WebView(this).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = false
                destroy()
            }
        }
    }
}
