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
            //
            // This goes through setTabVisible() rather than calling onPause()
            // directly: onPause() stops the page, but it left the renderer at
            // RENDERER_PRIORITY_IMPORTANT, which is the one renderer the
            // platform has been told never to waive. The tab the user was on
            // when they left the app therefore kept a full-priority renderer
            // resident — and unreclaimable — for as long as the app stayed in
            // the background. Waiving it as well lets the system take that
            // memory back; syncTabVisibility() restores the active tab to
            // IMPORTANT the moment the app returns.
            if (rendererVisible[tab.id] != false) {
                BrowserWebViewFactory.setTabVisible(tab.webView, false)
                rendererVisible[tab.id] = false
            }
        }
        // onPause() is per WebView and does not stop everything a renderer can
        // keep running — CSS animations, requestAnimationFrame and script
        // timers survive it on several WebView versions. pauseTimers() is the
        // process-wide switch the platform provides for exactly this moment, so
        // a backgrounded app costs no renderer CPU at all. Every WebView is
        // resumed individually in syncTabVisibility() when the app returns.
        _tabs.value.firstOrNull()?.let { BrowserWebViewFactory.pauseTimers(it.webView) }
    }

    /** The host Activity is visible again: only the active tab resumes. */
    fun onHostStarted() {
        // Timer pause/resume acts on the whole process, so one live WebView is
        // enough to turn timers back on for every tab.
        _tabs.value.firstOrNull()?.let { BrowserWebViewFactory.resumeTimers(it.webView) }
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
            // The tab that takes over and the browsing mode must agree. Picking
            // only a new active id left the app in private mode with a regular
            // tab on screen when the last private tab was closed (and the
            // reverse when the last regular tab was closed): the tab strip read
            // as empty, and the next new tab was created in the wrong mode.
            val next = TabSelection.activeAfterClose(
                remaining = _tabs.value.map { TabRef(it.id, it.isPrivate) },
                closedIsPrivate = tab.isPrivate
            )
            _activeTabId.value = next?.id
            if (next != null) _privateMode.value = next.isPrivate
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
        val css = PageInjections.cssFor(settings)
        _tabs.value.forEach { tab ->
            val previous = appliedSettings[tab.id]
            appliedSettings[tab.id] = settings
            // Only the fields WebView actually reads are worth a write. The
            // settings flow re-emits for every preference write, including the
            // ones that are purely visual (theme, accent, corners, wallpaper,
            // clock), and pushing those into every tab cost ~20 WebSettings
            // writes and a JavaScript evaluation per tab for no effect at all.
            // Page extensions are still handled below, so toggling one takes
            // effect immediately as before.
            if (WebViewSettingsDiff.affectsWebView(previous, settings)) {
                BrowserWebViewFactory.applySettings(tab.webView, settings, userAgentFor(tab, settings))
                if (settings.doNotTrackEnabled && tab.info.url.isNotBlank()) {
                    BrowserWebViewFactory.injectDoNotTrack(tab.webView, true)
                }
            }
            // Re-apply page extensions so toggling one takes effect immediately.
            // Skipped when the styles are the ones already on the page.
            if (tab.info.url.isNotBlank()) applyPageCss(tab, css)
        }
    }

    /** tabId -> the settings snapshot last pushed into that tab's WebView. */
    private val appliedSettings = HashMap<String, BrowserSettings>()

    /** tabId -> an explicit per-tab desktop-site choice, when the user made one. */
    private val desktopOverrides = HashMap<String, Boolean>()

    /**
     * The user agent a tab must carry: its own desktop choice when it has one,
     * otherwise the global default. Reading only the default reset a tab's
     * choice on the next settings change without reloading the page, so the
     * document stayed desktop while every later request went out with the mobile
     * user agent.
     */
    private fun userAgentFor(tab: BrowserTab, settings: BrowserSettings): String? =
        BrowserWebViewFactory.userAgentFor(desktopOverrides[tab.id] ?: settings.desktopSiteByDefault)

    /**
     * Pushes the page CSS into one tab, but only when it would change anything.
     *
     * Every injection is a JavaScript round trip across the renderer boundary
     * plus a DOM mutation, and it used to run on every page load and on every
     * settings emission — including when no page extension is enabled at all,
     * where the script only installed an empty style element. The last CSS
     * written per tab is remembered, so the default configuration now costs no
     * injection at all, and toggling an extension still takes effect at once.
     */
    private fun applyPageCss(tab: BrowserTab, css: String) {
        val previous = appliedCss[tab.id]
        if (css.isBlank()) {
            if (previous == null) return
            appliedCss.remove(tab.id)
            runCatching { tab.webView.evaluateJavascript(PageInjections.clearScript(), null) }
            return
        }
        if (previous == css) return
        appliedCss[tab.id] = css
        runCatching { tab.webView.evaluateJavascript(PageInjections.applyScript(css), null) }
    }

    /** tabId -> the page CSS currently installed in that tab's document. */
    private val appliedCss = HashMap<String, String>()

    /** Toggles the desktop user agent on one tab and reloads it. */
    fun setDesktopSite(tabId: String, enabled: Boolean) {
        val tab = tabById(tabId) ?: return
        // Recorded per tab so a later settings change cannot silently undo it.
        desktopOverrides[tabId] = enabled
        tab.webView.settings.userAgentString = userAgentFor(tab, settingsProvider())
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
        // previous page left behind. A new document also starts with no injected
        // styles, so the memo of what is on the page is dropped with the old one.
        if (!blank) {
            lastProgress[tabId] = START_PROGRESS
            appliedCss.remove(tabId)
        }
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
        if (blank) return
        val tab = tabById(tabId) ?: return
        // Page extensions are delivered once the document exists. Doing it here
        // rather than in the WebViewClient keeps the per-tab memo of what has
        // already been injected in one place.
        applyPageCss(tab, PageInjections.cssFor(settingsProvider()))
        _pageFinished.tryEmit(tab.info)
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
        appliedCss.remove(tab.id)
        appliedSettings.remove(tab.id)
        desktopOverrides.remove(tab.id)
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
        appliedCss.clear()
        appliedSettings.clear()
        desktopOverrides.clear()
        scope.cancel()
    }

    companion object {
        private const val TAG = "NexaTabs"
        private const val MAX_CLOSED_TABS = 20
        private const val START_PROGRESS = 5
    }
}
