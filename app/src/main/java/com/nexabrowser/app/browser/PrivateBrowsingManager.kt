package com.nexabrowser.app.browser

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the lifecycle of a private browsing session.
 *
 * What this really does:
 *   * Private visits are never written to the history or bookmark tables.
 *   * When the last private tab closes, the session's website data is cleared:
 *     DOM storage, the WebView cache, and (when the cookie setting is on)
 *     cookies.
 *
 * What it cannot do, and does not claim to do:
 *   * Android WebView has a single, app-wide cookie store and HTTP cache. There
 *     is no per-tab incognito profile. Clearing the private session therefore
 *     also clears data that regular tabs may have set, and cookies set inside a
 *     private tab stay visible to regular tabs until the session ends. Real
 *     isolation would require a separate process with its own WebView data
 *     directory.
 *   * Private mode does not hide traffic from websites, the network operator, an
 *     employer, or anyone who can observe the device or its traffic.
 */
class PrivateBrowsingManager(private val context: Context) {

    private var lastCount = 0
    private val sessionVisitedUrls = mutableListOf<String>()

    val activePrivateTabCount: Int get() = lastCount

    /** Records a private visit in memory only; it is never persisted. */
    fun recordPrivateVisit(url: String) {
        if (url.isBlank() || url == "about:blank") return
        sessionVisitedUrls.add(url)
    }

    fun sessionHistory(): List<String> = sessionVisitedUrls.toList()

    /**
     * Called whenever the number of open private tabs changes. The session ends
     * exactly once, when the count falls from a positive value to zero.
     */
    suspend fun syncOpenPrivateTabs(count: Int, clearCookies: Boolean) {
        val wasActive = lastCount > 0
        lastCount = count.coerceAtLeast(0)
        if (wasActive && lastCount == 0) {
            endSession(clearCookies)
        }
    }

    suspend fun endSession(clearCookies: Boolean) = withContext(Dispatchers.Main) {
        sessionVisitedUrls.clear()
        runCatching { WebStorage.getInstance().deleteAllData() }
            .onFailure { Log.w(TAG, "Could not clear DOM storage", it) }
        runCatching { WebView(context).clearCache(true) }
            .onFailure { Log.w(TAG, "Could not clear the WebView cache", it) }
        if (clearCookies) {
            runCatching {
                val cm = CookieManager.getInstance()
                cm.removeAllCookies(null)
                cm.flush()
            }.onFailure { Log.w(TAG, "Could not clear cookies", it) }
        }
        Log.i(TAG, "Private session ended; session data cleared (cookies=$clearCookies)")
    }

    companion object {
        private const val TAG = "NexaPrivate"
    }
}
