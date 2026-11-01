package com.nexabrowser.app.browser

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.nexabrowser.app.data.prefs.BrowserSettings
import com.nexabrowser.app.data.repository.BrowserRepository
import com.nexabrowser.app.domain.model.NewTabBehavior
import com.nexabrowser.app.util.FaviconSizing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A tab that was closed and can be restored. */
data class ClosedTab(val url: String, val title: String, val isPrivate: Boolean)

/**
 * Owns every live [BrowserTab] and its WebViews, and is the single source of
 * truth for the tab list, the active tab, and private/regular separation.
 *
 * Lifecycle rules that keep this leak-free:
 *   * Exactly one WebView is created per tab, with the Activity context when one
 *     is alive so that native controls work.
 *   * Every tab is destroyed in [closeTab], [closeAllTabs] and [shutdown].
 *   * WebViews are never referenced from a long-lived singleton; the manager
 *     itself is owned by the ViewModel and released in onCleared().
 */
class BrowserTabManager(
    private val appContext: Context,
    private val repository: BrowserRepository,
    private val settingsProvider: () -> BrowserSettings,
    private val webViewContext: () -> Context,
    private val host: BrowserActivityHost,
    private val privateManager: PrivateBrowsingManager
) : TabEventSink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    private val _tabInfos = MutableStateFlow<List<TabInfo>>(emptyList())
    val tabInfos: StateFlow<List<TabInfo>> = _tabInfos.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    private val _privateMode = MutableStateFlow(false)
    val privateMode: StateFlow<Boolean> = _privateMode.asStateFlow()

    private val _closedTabs = MutableStateFlow<List<ClosedTab>>(emptyList())
    val closedTabs: StateFlow<List<ClosedTab>> = _closedTabs.asStateFlow()

    private var idCounter = 0

    /**
     * tabId -> whether that tab's renderer is currently resumed at full
     * priority. A freshly created WebView is active, so a new tab starts as
     * visible and is paused by the first sync if it is not the active one.
     *
     * Only read and written on the main thread.
     */
    private val rendererVisible = HashMap<String, Boolean>()

    /** Emits once per completed page load so callers can record history etc. */
    private val _pageFinished = MutableSharedFlow<TabInfo>(extraBufferCapacity = 16)
    val pageFinished: SharedFlow<TabInfo> = _pageFinished.asSharedFlow()

    fun visibleTabs(): List<TabInfo> = _tabInfos.value.filter { it.isPrivate == _privateMode.value }

    fun activeTab(): BrowserTab? = _tabs.value.firstOrNull { it.id == _activeTabId.value }

    fun tabById(id: String): BrowserTab? = _tabs.value.firstOrNull { it.id == id }

    fun activeInfo(): TabInfo? = activeTab()?.info

    // ------------------------------------------------------------------ tab ops

    fun newTab(url: String? = null, isPrivate: Boolean = _privateMode.value, switchTo: Boolean = true): BrowserTab {
        val settings = settingsProvider()
        val target = when {
            url != null -> url
            isPrivate -> ""
            settings.newTabBehavior == NewTabBehavior.HOMEPAGE && settings.homepageUrl.isNotBlank() -> settings.homepageUrl
            else -> ""
        }
        val tab = createTabInternal(target, isPrivate)
        _tabs.value = _tabs.value + tab
        if (isPrivate && !_privateMode.value) _privateMode.value = true
        if (switchTo) _activeTabId.value = tab.id
        syncTabVisibility()
        publishInfos()
        syncPrivateCount()
        return tab
    }

    private fun createTabInternal(url: String?, isPrivate: Boolean): BrowserTab {
        val id = "tab-${++idCounter}-${System.currentTimeMillis()}"
        val webView = BrowserWebViewFactory.create(
            context = webViewContext(),
            tabId = id,
            settings = settingsProvider(),
            sink = this,
            host = host,
            settingsProvider = settingsProvider
        )
        val tab = BrowserTab(id, isPrivate, webView)
        rendererVisible[id] = true
        if (url.isNullOrBlank()) {
            webView.loadUrl("about:blank")
        } else {
            runCatching { webView.loadUrl(url) }
                .onFailure { Log.w(TAG, "Rejected start URL: $url"); webView.loadUrl("about:blank") }
        }
        return tab
    }

    /**
     * The host Activity went to the background. Every WebView is paused, so no
     * renderer keeps running timers, CSS animations, video or script work while
     * the app is not on screen. Without this a page keeps burning CPU — and
     * holding a full renderer resident — until the Activity is destroyed.
     */
    fun onHostStopped() {
        _tabs.value.forEach { tab ->
            // Nothing is visible while the Activity is away, so every tab is
            // marked paused and the state stays in step with the WebView.
            if (rendererVisible[tab.id] != false) {
                runCatching { tab.webView.onPause() }
                rendererVisible[tab.id] = false
            }
        }
    }

    /** The host Activity is visible again: only the active tab resumes. */
    fun onHostStarted() {
        if (_tabs.value.isNotEmpty()) syncTabVisibility()
    }

    /**
     * Keeps renderer priority and pause state in step with the active tab.
     *
     * Only tabs whose state actually differs are touched. Every
     * setRendererPriorityPolicy call crosses into the renderer process and every
     * onPause/onResume pair restarts or parks page timers, so switching tabs with
     * ten of them open used to make twenty of those calls for two tabs that
     * changed. Now a switch costs exactly two.
     */
    private fun syncTabVisibility() {
        val active = _activeTabId.value
        _tabs.value.forEach { tab ->
            val visible = tab.id == active
            if (rendererVisible[tab.id] == visible) return@forEach
            BrowserWebViewFactory.setTabVisible(tab.webView, visible)
            rendererVisible[tab.id] = visible
        }
    }

    fun selectTab(id: String) {
        val previous = _activeTabId.value
        val tab = tabById(id) ?: return
        if (tab.isPrivate && !_privateMode.value) _privateMode.value = true
        if (!tab.isPrivate && _privateMode.value) _privateMode.value = false
        _activeTabId.value = id
        syncTabVisibility()
        publishInfos()
    }

    fun closeTab(id: String) {
        val tab = tabById(id) ?: return
        val wasActive = _activeTabId.value == id
        detach(tab)
        _tabs.value = _tabs.value.filterNot { it.id == id }
        rememberClosed(tab)
        if (wasActive) {
            val sameMode = _tabs.value.filter { it.isPrivate == tab.isPrivate }
            _activeTabId.value = (sameMode.lastOrNull() ?: _tabs.value.lastOrNull())?.id
        }
        if (_tabs.value.isEmpty()) {
            _privateMode.value = false
            newTab(url = "", isPrivate = false)
        }
        syncTabVisibility()
        publishInfos()
        syncPrivateCount()
    }

    fun closeAllTabs(onlyPrivate: Boolean = false) {
        val targets = _tabs.value.filter { !onlyPrivate || it.isPrivate }
        if (targets.isEmpty()) return
        targets.forEach { tab ->
            detach(tab)
            rememberClosed(tab)
        }
        _tabs.value = _tabs.value.filterNot { t -> targets.any { it.id == t.id } }
        if (_tabs.value.isEmpty()) {
            _privateMode.value = false
            newTab(url = "", isPrivate = false)
        } else {
            if (onlyPrivate) _privateMode.value = false
            _activeTabId.value = _tabs.value.last().id
        }
        syncTabVisibility()
        publishInfos()
        syncPrivateCount()
    }

    fun duplicateTab(id: String) {
        val tab = tabById(id) ?: return
        val url = tab.info.url.takeIf { it.isNotBlank() && it != "about:blank" }
        val copy = createTabInternal(url, tab.isPrivate)
        _tabs.value = _tabs.value + copy
        _activeTabId.value = copy.id
        // The source tab must now be paused and waived, exactly as it would be
        // if the user had switched tabs by hand.
        syncTabVisibility()
        publishInfos()
        syncPrivateCount()
    }

    fun setPrivateMode(enabled: Boolean) {
        if (_privateMode.value == enabled) return
        _privateMode.value = enabled
        val candidate = _tabs.value.lastOrNull { it.isPrivate == enabled }
        if (candidate != null) {
            _activeTabId.value = candidate.id
            syncTabVisibility()
        } else {
            newTab(url = "", isPrivate = enabled)
        }
        publishInfos()
    }

    /** Re-opens the most recently closed tab. Returns true when one was restored. */
    fun reopenLastClosed(): Boolean {
        val closed = _closedTabs.value.lastOrNull() ?: return false
        _closedTabs.value = _closedTabs.value.dropLast(1)
        newTab(url = closed.url, isPrivate = closed.isPrivate, switchTo = true)
        return true
    }

    // ---------------------------------------------------------------- settings

    /** Re-applies WebView settings after a preference change. */
    fun applySettings(settings: BrowserSettings) {
        _tabs.value.forEach { tab ->
            BrowserWebViewFactory.applySettings(tab.webView, settings)
            if (settings.doNotTrackEnabled && tab.info.url.isNotBlank()) {
                BrowserWebViewFactory.injectDoNotTrack(tab.webView, true)
            }
            // Re-apply page extensions so toggling one takes effect immediately.
            if (tab.info.url.isNotBlank()) {
                runCatching {
                    tab.webView.evaluateJavascript(
                        PageInjections.applyScript(PageInjections.cssFor(settings)),
                        null
                    )
                }
            }
        }
    }

    /** Toggles the desktop user agent on one tab and reloads it. */
    fun setDesktopSite(tabId: String, enabled: Boolean) {
        val tab = tabById(tabId) ?: return
        tab.webView.settings.userAgentString = if (enabled) BrowserWebViewFactory.DESKTOP_UA else null
        tab.webView.reload()
    }

    // ----------------------------------------------------------------- session

    /**
     * The tabs worth restoring, captured synchronously. Reading the tab list is
     * a snapshot of in-memory state, so it is safe to call on the main thread
     * right before shutdown; the database write can then happen anywhere.
     */
    fun sessionSnapshot(): List<Triple<String, String, Int>> =
        _tabs.value.filter { !it.isPrivate }
            .mapIndexedNotNull { index, tab ->
                val url = tab.info.url
                if (url.isBlank() || url == "about:blank") null
                else Triple(url, tab.info.title, index)
            }

    suspend fun saveSession() = repository.saveSessionTabs(sessionSnapshot())

    // ------------------------------------------------------------ event sink

    override fun onPageStarted(tabId: String, url: String) {
        val blank = url == "about:blank"
        // A new document always reports its first progress tick, whatever the
        // previous page left behind.
        if (!blank) lastProgress[tabId] = START_PROGRESS
        updateTab(tabId) {
            it.copy(
                url = if (blank) it.url else url,
                isLoading = !blank,
                progress = if (blank) it.progress else 5,
                error = null
            )
        }
        if (!blank && tabById(tabId)?.isPrivate == true) privateManager.recordPrivateVisit(url)
    }

    override fun onPageFinished(tabId: String, url: String) {
        val blank = url == "about:blank"
        if (!blank) lastProgress[tabId] = 100
        updateTab(tabId) {
            it.copy(
                isLoading = false,
                progress = 100,
                url = if (blank) it.url else url,
                error = null
            )
        }
        if (!blank) tabById(tabId)?.let { _pageFinished.tryEmit(it.info) }
    }

    // WebView reports progress dozens of times per page. Publishing every tick
    // re-renders the progress bar and the tab list for no visible gain, so only
    // meaningful jumps are published.
    private val lastProgress = HashMap<String, Int>()

    override fun onProgress(tabId: String, progress: Int) {
        val previous = lastProgress[tabId] ?: -1
        val meaningful = progress == 0 || progress >= 100 || abs(progress - previous) >= 5
        if (!meaningful) return
        lastProgress[tabId] = progress
        updateTab(tabId) { it.copy(progress = progress, isLoading = progress < 100) }
    }

    override fun onTitle(tabId: String, title: String) {
        // Sites fire onReceivedTitle several times with the same string.
        if (tabById(tabId)?.info?.title == title) return
        updateTab(tabId) { it.copy(title = title) }
    }

    override fun onFavicon(tabId: String, favicon: Bitmap?) {
        if (favicon == null) return
        updateTab(tabId) { it.copy(favicon = downsampleFavicon(favicon)) }
    }

    /**
     * A page can hand back a 512px apple-touch-icon, which costs ~1 MB of heap
     * for as long as the tab is open — multiplied by every open tab. The icon is
     * drawn at 24–28dp, so it is capped at [FaviconSizing.MAX_ICON_EDGE] on the
     * way in. Icons that are already small (the common case) are returned
     * untouched, so this costs nothing for a normal 16–48px favicon. The source
     * bitmap is not recycled: WebView may still be using it.
     */
    private fun downsampleFavicon(bitmap: Bitmap): Bitmap {
        val (w, h) = FaviconSizing.targetSize(bitmap.width, bitmap.height)
        if (w == bitmap.width && h == bitmap.height) return bitmap
        return runCatching { Bitmap.createScaledBitmap(bitmap, w, h, true) }.getOrDefault(bitmap)
    }

    override fun onUrlChanged(tabId: String, url: String) {
        if (url == "about:blank") return
        if (tabById(tabId)?.info?.url == url) return
        updateTab(tabId) { it.copy(url = url) }
        val tab = tabById(tabId) ?: return
        if (tab.isPrivate) privateManager.recordPrivateVisit(url)
    }

    override fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    override fun onError(tabId: String, error: PageError) {
        updateTab(tabId) { it.copy(error = error, isLoading = false, progress = 100) }
    }

    // ----------------------------------------------------------------- private

    private fun detach(tab: BrowserTab) {
        lastProgress.remove(tab.id)
        rendererVisible.remove(tab.id)
        tab.destroy()
    }

    /** Rebuilds the whole published list. Used for structural changes only. */
    private fun publishInfos() {
        _tabInfos.value = _tabs.value.map { it.info }
    }

    /**
     * Republished list with exactly one entry swapped.
     *
     * Every WebView event — progress, title, url, favicon, navigation state —
     * used to run through a per-tab collector coroutine that rebuilt the entire
     * tab list. A page load fires dozens of these, so with several tabs open the
     * cost was O(tabs) allocations per event, dispatched through the main
     * dispatcher, for a change that touches a single entry. The state write now
     * publishes just that entry, and only when it actually changed.
     */
    private fun publishInfo(tab: BrowserTab) {
        val info = tab.info
        val current = _tabInfos.value
        val index = current.indexOfFirst { it.id == tab.id }
        if (index < 0) {
            publishInfos()
            return
        }
        if (current[index] == info) return
        val updated = ArrayList<TabInfo>(current.size)
        for (i in current.indices) updated.add(if (i == index) info else current[i])
        _tabInfos.value = updated
    }

    private fun updateTab(id: String, transform: (TabInfo) -> TabInfo) {
        val tab = _tabs.value.firstOrNull { it.id == id } ?: return
        val before = tab.info
        tab.update(transform)
        if (tab.info != before) publishInfo(tab)
    }

    private fun rememberClosed(tab: BrowserTab) {
        val info = tab.info
        if (info.url.isBlank() || info.url == "about:blank") return
        val list = _closedTabs.value + ClosedTab(info.url, info.displayTitle, tab.isPrivate)
        _closedTabs.value = if (list.size > MAX_CLOSED_TABS) list.takeLast(MAX_CLOSED_TABS) else list
    }

    private fun syncPrivateCount() {
        val count = _tabs.value.count { it.isPrivate }
        scope.launch { privateManager.syncOpenPrivateTabs(count, settingsProvider().cookiesEnabled) }
    }

    /** Destroys every WebView and stops all observation. */
    fun shutdown() {
        _tabs.value.forEach { runCatching { it.destroy() } }
        _tabs.value = emptyList()
        _tabInfos.value = emptyList()
        _activeTabId.value = null
        lastProgress.clear()
        rendererVisible.clear()
        scope.cancel()
    }

    companion object {
        private const val TAG = "NexaTabs"
        private const val MAX_CLOSED_TABS = 20
        private const val START_PROGRESS = 5
    }
}
