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

        // Warm up the WebView provider off the critical path of the first tab.
        runCatching {
            WebView(this).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = false
                loadUrl("about:blank")
                destroy()
            }
        }
    }
}
