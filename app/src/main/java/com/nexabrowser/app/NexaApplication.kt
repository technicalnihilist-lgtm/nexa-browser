package com.nexabrowser.app

import android.app.Application
import android.webkit.WebView
import com.nexabrowser.app.browser.AdBlocker

/**
 * Application entry point. Keeps the process-wide WebView setup and the
 * blocklist load in one place, and does nothing else: anything expensive here
 * runs on the main thread before the first frame.
 */
class NexaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Read the bundled blocklist once, off the first page load — and now
        // off the main thread as well. The parse used to run here, in onCreate,
        // which is before the first frame: an asset open plus a lowercasing pass
        // over every line stood between the user and the window. The context is
        // registered immediately so a request that arrives first can finish the
        // job on its own network thread.
        AdBlocker.attach(this)
        Thread({ AdBlocker.ensureLoaded(this) }, "nexa-blocklist").apply {
            isDaemon = true
            start()
        }

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    // There is deliberately no WebView "warm-up" here any more.
    //
    // It constructed a throwaway WebView in onCreate — provider init, ~20
    // WebSettings writes, then destroy() — to warm the provider before the first
    // tab. But the first real tab is created a few milliseconds later, on the
    // same main thread, by BrowserViewModel.restoreSession(): the warm-up bought
    // nothing and cost a second full WebView construction plus a Chromium
    // init/teardown cycle on every cold start, before the first frame could be
    // drawn. The provider is initialised exactly once now, by the WebView that
    // is actually shown.
}
