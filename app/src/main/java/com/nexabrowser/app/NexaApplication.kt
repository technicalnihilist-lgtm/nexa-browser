package com.nexabrowser.app

import android.app.Application
import android.webkit.WebView
import com.nexabrowser.app.browser.AdBlocker
import com.nexabrowser.app.browser.MemoryTrim
import com.nexabrowser.app.util.FaviconPathCache

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

    /**
     * Releases the process-wide caches when the system reports real pressure.
     *
     * `ComponentCallbacks2` is delivered to the Activity *and* to the
     * Application, and the two do not overlap: once the last Activity is
     * destroyed — which is the ordinary state of a browser the user has left,
     * and the state in which Android trims a cached process hardest before it
     * kills it — only the Application keeps receiving these callbacks.
     *
     * Until now nothing answered them at this level, so a browser sitting in the
     * background still held the ad blocker's host → decision table (up to 1024
     * host strings and their booleans) and the favicon `stat()` memo (up to 512
     * paths) for as long as the process lived, however short of memory the
     * system said it was. Neither is worth keeping there: both rebuild
     * themselves on the next request, and the next request can only happen once
     * the user comes back, i.e. when there is a frame to spare. Dropping them is
     * also the cheapest way to give the platform a reason to keep the process —
     * and the tab session it holds — alive.
     *
     * The decision of *which* levels count as pressure is [MemoryTrim]'s, so the
     * Application and the Activity release exactly the same memory at exactly
     * the same levels, and the routine `UI_HIDDEN`/`BACKGROUND` lifecycle
     * notifications still cost nothing.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        dropRebuildableCaches(level)
    }

    /**
     * The legacy low-memory callback, mapped onto the trim level that means the
     * same thing, exactly as `MainActivity` does for its own copy of it.
     */
    override fun onLowMemory() {
        super.onLowMemory()
        dropRebuildableCaches(MemoryTrim.RUNNING_CRITICAL)
    }

    private fun dropRebuildableCaches(level: Int) {
        if (!MemoryTrim.dropCaches(level)) return
        AdBlocker.trimCaches()
        FaviconPathCache.clear()
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
