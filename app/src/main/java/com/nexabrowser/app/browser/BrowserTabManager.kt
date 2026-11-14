package com.nexabrowser.app.browser

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.nexabrowser.app.data.prefs.BrowserSettings
import com.nexabrowser.app.data.repository.BrowserRepository
import com.nexabrowser.app.domain.model.NewTabBehavior
import com.nexabrowser.app.util.FaviconPathCache
import com.nexabrowser.app.util.FaviconSizing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /**
     * id -> tab, kept in step with [_tabs] by [setTabs]. Every WebView callback
     * resolves its tab through this instead of scanning the list.
     */
    private val byId = TabLookup<BrowserTab>()

    /** Publishes a new tab list and re-indexes it in one step. */
    private fun setTabs(tabs: List<BrowserTab>) {
        byId.setAll(tabs) { it.id }
        _tabs.value = tabs
    }

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

    fun activeTab(): BrowserTab? = byId[_activeTabId.value]

    fun tabById(id: String): BrowserTab? = byId[id]

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
        setTabs(_tabs.value + tab)
        if (isPrivate && !_privateMode.value) _privateMode.value = true
        if (switchTo) _activeTabId.value = tab.id
        syncTabVisibility()
        publishInfos()
        syncPrivateCount()
        return tab
    }

    private fun createTabInternal(
        url: String?,
        isPrivate: Boolean,
        deferLoad: Boolean = false,
        title: String = ""
    ): BrowserTab {
        val id = "tab-${++idCounter}-${System.currentTimeMillis()}"
        // The WebView is built when this tab is first drawn or first touched,
        // not here. A restored background tab therefore costs one TabInfo until
        // the user selects it instead of a full WebView construction on the main
        // thread during cold start; the provider runs on the same thread the tab
        // manager owns, with the settings that are current at that moment.
        val tab = BrowserTab(id, isPrivate) {
            BrowserWebViewFactory.create(
                context = webViewContext(),
                tabId = id,
                settings = settingsProvider(),
                sink = this,
                host = host,
                settingsProvider = settingsProvider
            )
        }
        if (title.isNotEmpty()) tab.restore(tab.info.copy(title = title))
        if (deferLoad && !url.isNullOrBlank()) {
            // A restored tab the user is not looking at yet: no WebView is
            // created and nothing is fetched. The url is carried in the tab's
            // metadata so the tab strip, the session write and a later
            // selection all see the page the user left there.
            pendingLoads[id] = url
            tab.restore(tab.info.copy(url = url, isLoading = false, progress = 0))
            // No renderer is running for this tab, which is exactly the state
            // syncTabVisibility() would demote it to anyway.
            rendererVisible[id] = false
            return tab
        }
        rendererVisible[id] = true
        val webView = tab.webView
        if (url.isNullOrBlank()) {
            webView.loadUrl("about:blank")
        } else {
            runCatching { webView.loadUrl(url) }
                .onFailure { Log.w(TAG, "Rejected start URL: $url"); webView.loadUrl("about:blank") }
        }
        return tab
    }

    /**
     * tabId -> the url a restored background tab has not fetched yet.
     *
     * Nothing is requested for these tabs at restore: a session of ten tabs used
     * to start ten page loads (ten parses, ten sets of subresource fetches, ten
     * scripts) in the same moment the visible tab was fetching and painting its
     * first frame. The entry is consumed — and the page loaded — the first time
     * the tab is selected. Only read and written on the main thread.
     */
    private val pendingLoads = HashMap<String, String>()

    /** Starts a deferred tab's load once it is on screen. No-op for other tabs. */
    private fun loadIfDeferred(tab: BrowserTab) {
        val url = pendingLoads.remove(tab.id) ?: return
        runCatching { tab.webView.loadUrl(url) }
            .onFailure {
                Log.w(TAG, "Rejected restored URL: $url")
                runCatching { tab.webView.loadUrl("about:blank") }
            }
    }

    /**
     * Re-creates a saved session as one batch.
     *
     * Restoring used to call [newTab] once per saved tab. Each of those rebuilt
     * and published the whole tab list, walked every tab to re-sync renderer
     * priority, and launched its own private-count coroutine — so a ten-tab
     * session cost ten list rebuilds, ten full visibility sweeps and ten
     * coroutines before the first frame could be drawn, on top of the ten
     * WebViews being constructed. Here the WebViews are still created one at a
     * time (they have to be), but the published list, the renderer priorities
     * and the private-tab count are settled exactly once at the end.
     *
     * The first restored tab becomes active, which is what the caller used to do
     * with an explicit selectTab() straight afterwards.
     */
    fun restoreTabs(entries: List<RestoreEntry>) {
        if (entries.isEmpty()) return
        val created = ArrayList<BrowserTab>(entries.size)
        // Every tab is built with its saved title but no network work; the tab
        // that ends up on screen is the only one that loads now.
        for (entry in entries) {
            created.add(createTabInternal(entry.url, false, deferLoad = true, title = entry.title))
        }
        setTabs(_tabs.value + created)
        if (_activeTabId.value == null) _activeTabId.value = created.first().id
        syncTabVisibility()
        publishInfos()
        syncPrivateCount()
        activeTab()?.let { loadIfDeferred(it) }
    }

    /**
     * The host Activity went to the background. Every WebView is paused, so no
     * renderer keeps running timers, CSS animations, video or script work while
     * the app is not on screen. Without this a page keeps burning CPU — and
     * holding a full renderer resident — until the Activity is destroyed.
     */
    fun onHostStopped() {
        _tabs.value.forEach { tab ->
            // A tab that has never been shown has no WebView and no renderer,
            // so there is nothing to pause.
            if (!tab.hasWebView) {
                rendererVisible[tab.id] = false
                return@forEach
            }
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
        _tabs.value.firstOrNull { it.hasWebView }?.let { BrowserWebViewFactory.pauseTimers(it.webView) }
    }

    /**
     * The system is short of memory.
     *
     * Until now nothing here answered that call: the ad blocker's host → decision
     * table, the favicon stat() memo and one renderer process per open tab were
     * all kept until the platform had to kill the process, which costs the user
     * every open tab. The rebuildable caches are dropped whenever the system
     * reports real pressure — not on the lifecycle levels a plain app switch
     * produces, which used to cost a rebuild of both caches on the way back —
     * and the memory behind background renderers is only released under the
     * same real pressure,
     * so an ordinary trip to the background never repaints a page on return.
     *
     * The active tab is skipped deliberately: freeMemory() on the page the user
     * is looking at would throw away its decoded images and make it redraw for no
     * gain while it is the one renderer that must stay warm.
     */
    @Suppress("DEPRECATION")
    fun onTrimMemory(level: Int) {
        if (MemoryTrim.dropCaches(level)) {
            AdBlocker.trimCaches()
            FaviconPathCache.clear()
        }
        if (!MemoryTrim.freeRendererMemory(level)) return
        val active = _activeTabId.value
        _tabs.value.forEach { tab ->
            if (tab.id == active) return@forEach
            // A tab with no WebView holds no renderer memory to give back.
            if (!tab.hasWebView) return@forEach
            // freeMemory() is a hint that the platform honours on older WebView
            // versions and treats as a no-op on newer ones, which is exactly why
            // it is only asked for when the process is already under pressure.
            runCatching { tab.webView.freeMemory() }
        }
    }

    /** The host Activity is visible again: only the active tab resumes. */
    fun onHostStarted() {
        // Timer pause/resume acts on the whole process, so one live WebView is
        // enough to turn timers back on for every tab.
        _tabs.value.firstOrNull { it.hasWebView }?.let { BrowserWebViewFactory.resumeTimers(it.webView) }
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
            // A background tab that has never been shown has no WebView to
            // demote, and touching it here would construct one — which is the
            // cost this whole path exists to avoid. The one case that must
            // create it is the tab becoming visible, handled just below.
            if (!tab.hasWebView && !visible) {
                rendererVisible[tab.id] = false
                return@forEach
            }
            BrowserWebViewFactory.setTabVisible(tab.webView, visible)
            rendererVisible[tab.id] = visible
            // A tab that becomes visible is the one the user is now looking at,
            // so a load deferred at restore has to start here.
            //
            // Only selectTab() and setPrivateMode() used to consume that
            // deferral. Every other way of arriving at a tab left it blank for
            // good: closing the active tab hands the screen to a neighbour that
            // may still be holding its url unfetched, and the same is true for
            // closeAllTabs() and for the first tab of a restored session. The
            // tab then showed an empty WebView with the saved title in the
            // strip — the page only appeared if the user switched away and
            // back. Doing it here covers every path at once, and it is a no-op
            // for a tab that has no deferral or has already been loaded.
            if (visible) loadIfDeferred(tab)
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
        loadIfDeferred(tab)
    }

    fun closeTab(id: String) {
        val tab = tabById(id) ?: return
        val wasActive = _activeTabId.value == id
        detach(tab)
        setTabs(_tabs.value.filterNot { it.id == id })
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
        val closing = targets.mapTo(HashSet(targets.size)) { it.id }
        setTabs(_tabs.value.filterNot { it.id in closing })
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
        setTabs(_tabs.value + copy)
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
            loadIfDeferred(candidate)
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
            // A tab whose WebView has not been built yet needs no writes: the
            // provider hands the then-current settings to the factory when the
            // view is finally created.
            if (!tab.hasWebView) return@forEach
            // Only the fields WebView actually reads are worth a write. The
            // settings flow re-emits for every preference write, including the
            // ones that are purely visual (theme, accent, corners, wallpaper,
            // clock), and pushing those into every tab cost ~20 WebSettings
            // writes and a JavaScript evaluation per tab for no effect at all.
            // Page extensions are still handled below, so toggling one takes
            // effect immediately as before.
            if (WebViewSettingsDiff.affectsWebView(previous, settings)) {
                // Only the fields that changed are written. The snapshot used to
                // be pushed whole, so one preference rewrote all twenty fields
                // of every open tab; see WebViewSettingsDiff.changes.
                BrowserWebViewFactory.applySettingsChanges(
                    tab.webView,
                    previous,
                    settings,
                    userAgentFor(tab, settings)
                )
                if (tab.info.url.isNotBlank()) applyDoNotTrack(tab)
            }
            // Re-apply page extensions so toggling one takes effect immediately.
            // Skipped when the styles are the ones already on the page.
            if (tab.info.url.isNotBlank()) applyPageCss(tab, css)
        }
    }

    /** tabId -> the settings snapshot last pushed into that tab's WebView. */
    private val appliedSettings = HashMap<String, BrowserSettings>()

    /**
     * tabId -> how often that tab has lost its renderer inside the current
     * window, so [RendererRecovery] can tell one unlucky tab from a page that
     * kills its own renderer on every attempt.
     */
    private val rendererLosses = HashMap<String, RendererRecovery.LossRecord>()

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
        // Nothing to inject into until the tab has a WebView; the memo stays
        // empty so the first page this tab loads gets the styles normally.
        if (!tab.hasWebView) return
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

    /**
     * Tabs whose current document already carries the Do Not Track shim.
     *
     * The shim was only ever injected from [applySettings], i.e. when the
     * preference itself changed — never when a page loaded. Since every
     * navigation replaces the document, the shim was thrown away by the very next
     * page and the setting (on by default) did nothing at all: a site script that
     * reads `navigator.doNotTrack` always saw `undefined`. It is now delivered
     * once per document, when the document exists, and the memo stops a settings
     * re-emission or a repeat page-finished callback from sending it twice.
     */
    private val dntInjected = HashSet<String>()

    /** Injects the Do Not Track shim into the tab's current document, once. */
    private fun applyDoNotTrack(tab: BrowserTab) {
        if (!tab.hasWebView) return
        if (!settingsProvider().doNotTrackEnabled) {
            // Turning the preference off must let a later re-enable inject again.
            dntInjected.remove(tab.id)
            return
        }
        if (!dntInjected.add(tab.id)) return
        runCatching { BrowserWebViewFactory.injectDoNotTrack(tab.webView, true) }
            .onFailure { dntInjected.remove(tab.id) }
    }

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

    /**
     * The session written by this process last, or null before the first write.
     * Only touched from [persistSession], which the caller runs off the main
     * thread; a racing second write at worst repeats a write that was needed.
     */
    @Volatile
    private var lastSavedSession: List<Triple<String, String, Int>>? = null

    suspend fun saveSession() = persistSession(sessionSnapshot())

    /**
     * Stores [snapshot] unless it is the session that is already stored.
     *
     * Saving happens on every `onStop()` and again from `onCleared()`, so a
     * background/foreground cycle used to write the same rows twice — each write
     * a clear plus an insert inside one transaction, and one fsync per
     * transaction — for a table that already held exactly those rows. See
     * [SessionWrite]. The snapshot is still taken synchronously by the caller;
     * only the redundant write is skipped.
     */
    suspend fun persistSession(snapshot: List<Triple<String, String, Int>>) {
        if (SessionWrite.isRedundant(lastSavedSession, snapshot)) return
        repository.saveSessionTabs(snapshot)
        lastSavedSession = snapshot
    }

    // ------------------------------------------------------------ event sink

    override fun onPageStarted(tabId: String, url: String) {
        val blank = url == "about:blank"
        // A new document always reports its first progress tick, whatever the
        // previous page left behind. A new document also starts with no injected
        // styles, so the memo of what is on the page is dropped with the old one.
        if (!blank) {
            lastProgress[tabId] = START_PROGRESS
            // A new document is the one thing that may start a load again after
            // the user stopped one; see ProgressGate.
            stoppedLoads.remove(tabId)
            appliedCss.remove(tabId)
            dntInjected.remove(tabId)
            faviconSeen.remove(tabId)
        }
        updateTab(tabId) {
            it.copy(
                url = if (blank) it.url else url,
                isLoading = !blank,
                progress = if (blank) it.progress else 5,
                // A blank document is not a navigation: it is what a tab that
                // has nothing to show loads to stay reloadable. Clearing the
                // error here used to wipe the failure [rebuildTab] had just
                // published for a tab whose renderer died and could not be
                // reloaded, so the "page stopped responding" overlay never
                // appeared and the user was left with an empty page.
                error = if (blank) it.error else null
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
                // WebView reports onPageFinished for its own error page as
                // well, so clearing the error here used to erase the failure
                // that had just been reported and the "Try Again" overlay never
                // showed. A network or TLS failure survives the finish; an HTTP
                // status error does not, because that page really did render.
                // A blank document keeps whatever the tab is showing, for the
                // same reason as in onPageStarted: it is not a navigation.
                error = if (blank) it.error else LoadOutcome.errorAfterFinish(it.error)
            )
        }
        if (blank) return
        // A document that loads to the end proves the renderer is healthy, so
        // the recovery budget starts over for this tab.
        rendererLosses.remove(tabId)
        val tab = tabById(tabId) ?: return
        // Page extensions are delivered once the document exists. Doing it here
        // rather than in the WebViewClient keeps the per-tab memo of what has
        // already been injected in one place.
        applyPageCss(tab, PageInjections.cssFor(settingsProvider()))
        applyDoNotTrack(tab)
        _pageFinished.tryEmit(tab.info)
    }

    // WebView reports progress dozens of times per page. Publishing every tick
    // re-renders the progress bar and the tab list for no visible gain, so only
    // meaningful jumps are published.
    private val lastProgress = HashMap<String, Int>()

    /**
     * Tabs whose current load the user stopped, and which no new document has
     * replaced yet.
     *
     * Kept apart from [lastProgress] because a completed progress value does not
     * actually suppress a late tick — a cancelled load reporting 50 % is a
     * 50-point jump from the 100 the stop published, and the throttle rule
     * accepts it. See [ProgressGate]. Only read and written on the main thread.
     */
    private val stoppedLoads = HashSet<String>()

    /**
     * The user pressed stop on a page that was still loading.
     *
     * WebView does not reliably deliver `onPageFinished` for a cancelled load,
     * so the tab stayed `isLoading = true` with the progress bar frozen at its
     * last tick: the toolbar kept offering "stop" for a page that had already
     * stopped, and nothing short of a reload or a navigation cleared it. The
     * tab is settled here instead, and the load is recorded as stopped so that a
     * late tick from it cannot flip the tab back — a completed progress value
     * alone does not do that, because a tick that jumps 5 points or more is
     * published. See [ProgressGate].
     */
    fun onLoadStopped(tabId: String?) {
        val id = tabId ?: return
        if (byId[id] == null) return
        lastProgress[id] = 100
        stoppedLoads.add(id)
        updateTab(id) { it.copy(isLoading = false, progress = 100) }
    }

    override fun onProgress(tabId: String, progress: Int) {
        if (!ProgressGate.isMeaningful(lastProgress[tabId], stoppedLoads.contains(tabId), progress)) return
        lastProgress[tabId] = progress
        updateTab(tabId) { it.copy(progress = progress, isLoading = progress < 100) }
    }

    override fun onTitle(tabId: String, title: String) {
        // Sites fire onReceivedTitle several times with the same string.
        if (tabById(tabId)?.info?.title == title) return
        updateTab(tabId) { it.copy(title = title) }
    }

    /**
     * A page can hand back a 512px apple-touch-icon, which costs ~1 MB of heap
     * for as long as the tab is open — multiplied by every open tab. The icon is
     * drawn at 24–28dp, so it is capped at [FaviconSizing.MAX_ICON_EDGE] on the
     * way in. The source bitmap is not recycled: WebView may still be using it.
     *
     * The rescale used to run inline. This callback is delivered on the main
     * thread at the moment the page has just finished painting, and rescaling a
     * 512px bitmap is several milliseconds of pixel work — a dropped frame on
     * every page whose icon is larger than the cap. Only the (free) size check
     * stays on the main thread now; the pixel work moves to the default
     * dispatcher and the result is published when it is ready. A normal 16–48px
     * favicon still takes the fast path below and never leaves the main thread.
     */
    override fun onFavicon(tabId: String, favicon: Bitmap?) {
        if (favicon == null) return
        // The same icon is reported more than once per page — onPageStarted hands
        // back the document's favicon and onReceivedIcon follows with the same
        // instance, and a site that badges its icon re-fires the callback with it
        // on every change of the badge. See FaviconDedupe: a repeat is neither
        // rescaled (a wasted bitmap allocation) nor published (a new tab list and
        // a recomposition of the tab strip) for a picture already on screen.
        val signature = FaviconDedupe.signature(
            System.identityHashCode(favicon),
            favicon.width,
            favicon.height
        )
        // The icon belongs to the WebView, not to us. Publishing one it has
        // already recycled used to leave the tab strip drawing a dead bitmap,
        // which is a hard crash in the renderer's canvas. The check lives inside
        // shouldAccept rather than after the memo is written: remembering a
        // signature the tab could not actually draw suppressed the real report
        // that follows it (the same instance arrives again from onReceivedIcon),
        // so the tab kept its letter avatar for the whole life of the page.
        if (!FaviconDedupe.shouldAccept(faviconSeen[tabId], signature, favicon.isRecycled)) return
        if (!FaviconSizing.needsScale(favicon.width, favicon.height)) {
            // A newer icon supersedes any scale still in flight for this tab.
            faviconToken.remove(tabId)
            faviconSeen[tabId] = signature
            updateTab(tabId) { it.copy(favicon = favicon) }
            return
        }
        faviconSeen[tabId] = signature
        val (w, h) = FaviconSizing.targetSize(favicon.width, favicon.height)
        val token = Any()
        faviconToken[tabId] = token
        scope.launch {
            // A scale that fails (an icon the WebView recycled while the work
            // was queued, an out-of-memory bitmap) publishes nothing at all. It
            // used to fall back to the source bitmap, which in that case is
            // exactly the dead bitmap the failure was about.
            val scaled = withContext(Dispatchers.Default) {
                runCatching { Bitmap.createScaledBitmap(favicon, w, h, true) }.getOrNull()
            }
            // A scale that finishes after the tab has moved on — a newer icon,
            // a rebuilt WebView, a closed tab — must not publish over it.
            val superseded = faviconToken[tabId] !== token || byId[tabId] == null ||
                scaled == null || scaled.isRecycled
            if (superseded) {
                // Nothing was published, so this signature must not stay
                // remembered: the next report of the same icon — which is what
                // a failed scale usually gets a moment later — has to be taken.
                // A newer icon has already written its own signature here, and
                // only the one this attempt claimed is dropped.
                if (faviconSeen[tabId] == signature) faviconSeen.remove(tabId)
                return@launch
            }
            updateTab(tabId) { it.copy(favicon = scaled) }
        }
    }

    /**
     * Per-tab identity of the icon currently being prepared off the main thread,
     * so a late result cannot replace a newer one.
     */
    private val faviconToken = HashMap<String, Any?>()

    /**
     * tabId -> the packed signature of the icon this tab has already accepted.
     *
     * Only read and written on the main thread, like the rest of the per-tab
     * memos here. Dropped with the rest of a tab's state when it is closed or its
     * WebView is rebuilt, and when a new document starts, so the first icon of the
     * next page is always taken.
     */
    private val faviconSeen = HashMap<String, Long>()

    override fun onUrlChanged(tabId: String, url: String) {
        if (url == "about:blank") return
        if (tabById(tabId)?.info?.url == url) return
        updateTab(tabId) { it.copy(url = url) }
        val tab = tabById(tabId) ?: return
        if (tab.isPrivate) privateManager.recordPrivateVisit(url)
    }

    override fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        // Three callbacks report this per page load (start, finish, history
        // update) and each one used to allocate a copy of the tab's state, plus
        // a scan of the published list, only for [updateTab] to find the values
        // unchanged and drop it. Reading the tab first makes a repeat cost one
        // comparison.
        val tab = byId[tabId] ?: return
        val info = tab.info
        if (info.canGoBack == canGoBack && info.canGoForward == canGoForward) return
        updateTab(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    override fun onError(tabId: String, error: PageError) {
        updateTab(tabId) { it.copy(error = error, isLoading = false, progress = 100) }
    }

    /**
     * The renderer behind one tab died.
     *
     * Nothing can bring that WebView back: once its renderer process is gone the
     * view is a dead rectangle, and the only recovery the platform offers is a
     * new WebView. The tab itself — its id, url, title, private flag and place
     * in the strip — is kept, so the user sees the page come back rather than a
     * tab that silently vanished. The reload budget in [RendererRecovery] keeps
     * a page that kills its own renderer from looping; past it the tab stays put
     * with the normal "couldn't be loaded" overlay and its Try Again button.
     */
    override fun onRendererGone(tabId: String, didCrash: Boolean) {
        val tab = tabById(tabId) ?: return
        val info = tab.info
        val record = RendererRecovery.record(rendererLosses[tabId], System.currentTimeMillis())
        rendererLosses[tabId] = record

        val canReload = RendererRecovery.shouldReload(record) &&
            info.url.isNotBlank() && info.url != "about:blank"
        Log.w(
            TAG,
            "Renderer gone for $tabId (crash=$didCrash, loss ${record.count}); " +
                if (canReload) "rebuilding and reloading" else "rebuilding without a reload"
        )
        rebuildTab(
            tab = tab,
            urlToLoad = if (canReload) info.url else null,
            error = if (canReload) null else PageError(
                PageError.UNKNOWN,
                "The page stopped responding and had to be restarted.",
                info.url,
                false
            )
        )
    }

    /**
     * Swaps a tab's dead WebView for a fresh one, keeping its metadata.
     *
     * The old view is detached and destroyed first — a WebView whose renderer
     * has gone must never be attached, drawn or reused. Everything the tab
     * manager memoises for that tab (last progress, injected CSS, applied
     * settings) belonged to the old view, so it is dropped with it.
     */
    private fun rebuildTab(tab: BrowserTab, urlToLoad: String?, error: PageError?) {
        val index = _tabs.value.indexOfFirst { it.id == tab.id }
        if (index < 0) return
        val info = tab.info

        runCatching { (tab.webView.parent as? android.view.ViewGroup)?.removeView(tab.webView) }
        runCatching { tab.webView.destroy() }
        lastProgress.remove(tab.id)
        stoppedLoads.remove(tab.id)
        // A rebuild loads the tab's url on the fresh WebView, so any deferral
        // held for the old one is spent: keeping it would load the page twice.
        pendingLoads.remove(tab.id)
        appliedCss.remove(tab.id)
        appliedSettings.remove(tab.id)
        faviconToken.remove(tab.id)
        faviconSeen.remove(tab.id)
        dntInjected.remove(tab.id)

        val settings = settingsProvider()
        // A tab may carry its own desktop-site choice, which must survive the
        // rebuild: the factory is told about it here instead of the whole
        // settings block being written a second time over the new view. That
        // second pass used to run on every renderer recovery — about twenty
        // WebSettings writes plus a CookieManager round trip through the
        // WebView provider, on the main thread, at the moment the system is
        // already short of memory and this is the one path that must stay
        // cheap.
        val webView = BrowserWebViewFactory.create(
            context = webViewContext(),
            tabId = tab.id,
            settings = settings,
            sink = this,
            host = host,
            settingsProvider = settingsProvider,
            userAgent = userAgentFor(tab, settings)
        )

        val fresh = BrowserTab(tab.id, tab.isPrivate) { webView }
        fresh.restore(
            info.copy(
                isLoading = urlToLoad != null,
                progress = if (urlToLoad != null) START_PROGRESS else 100,
                canGoBack = false,
                canGoForward = false,
                error = error
            )
        )
        // A freshly created WebView is running; syncTabVisibility() demotes it
        // again at once if this is a background tab.
        rendererVisible[tab.id] = true

        val updated = _tabs.value.toMutableList()
        updated[index] = fresh
        setTabs(updated)

        if (urlToLoad.isNullOrBlank()) webView.loadUrl("about:blank")
        else runCatching { webView.loadUrl(urlToLoad) }
            .onFailure { webView.loadUrl("about:blank") }

        syncTabVisibility()
        publishInfos()
    }

    // ----------------------------------------------------------------- private

    private fun detach(tab: BrowserTab) {
        lastProgress.remove(tab.id)
        stoppedLoads.remove(tab.id)
        pendingLoads.remove(tab.id)
        rendererVisible.remove(tab.id)
        appliedCss.remove(tab.id)
        appliedSettings.remove(tab.id)
        desktopOverrides.remove(tab.id)
        rendererLosses.remove(tab.id)
        faviconToken.remove(tab.id)
        faviconSeen.remove(tab.id)
        dntInjected.remove(tab.id)
        tab.destroy()
    }

    /** Rebuilds the whole published list. Used for structural changes only. */
    private fun publishInfos() {
        backgroundDirty.clear()
        _tabInfos.value = _tabs.value.map { it.info }
    }

    /**
     * Tabs whose published entry is stale but which the user cannot see.
     *
     * A WebView fires progress, title, url, favicon and navigation callbacks
     * dozens of times per page load, and every one of them publishes a new tab
     * list, which recomposes the tab strip and the tab overview. For the tab on
     * screen that is exactly right. For a tab loading in the background it is
     * work the user cannot see — and the worst case is the common one: a
     * restored session starts every saved tab at once, so the main thread spent
     * the first seconds of a cold start rebuilding and recomposing the tab list
     * for tabs that were not on screen while the visible page was trying to
     * paint its first frame.
     *
     * Background tabs are therefore collected here and published together, at
     * most once per [BACKGROUND_PUBLISH_DELAY_MS]. Selecting a tab, closing one
     * or any structural change still publishes immediately through
     * [publishInfos], so nothing that is on screen is ever stale.
     */
    private val backgroundDirty = HashSet<String>()
    private var backgroundFlush: Job? = null

    private fun markBackgroundDirty(id: String) {
        backgroundDirty.add(id)
        if (backgroundFlush?.isActive == true) return
        backgroundFlush = scope.launch {
            delay(BACKGROUND_PUBLISH_DELAY_MS)
            if (backgroundDirty.isEmpty()) return@launch
            // One list rebuild for every tab that changed in the window.
            publishInfos()
        }
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
        val tab = byId[id] ?: return
        val before = tab.info
        tab.update(transform)
        if (tab.info == before) return
        // The visible tab is published at once; a background one is folded into
        // the next batch so it cannot recompose the chrome the user is looking at.
        if (id == _activeTabId.value) publishInfo(tab) else markBackgroundDirty(id)
    }

    private fun rememberClosed(tab: BrowserTab) {
        val info = tab.info
        if (info.url.isBlank() || info.url == "about:blank") return
        val list = _closedTabs.value + ClosedTab(info.url, info.displayTitle, tab.isPrivate)
        _closedTabs.value = if (list.size > MAX_CLOSED_TABS) list.takeLast(MAX_CLOSED_TABS) else list
    }

    /**
     * The private-tab count this manager last handed to [PrivateBrowsingManager],
     * or -1 before the first hand-over. Only touched on the main thread.
     */
    private var lastPrivateCount = -1

    private fun syncPrivateCount() {
        val count = _tabs.value.count { it.isPrivate }
        // Every structural tab operation — a new tab, a close, a duplicate, a
        // restore — ends here, and each of them used to launch a coroutine that
        // read the settings and walked the tab list to find a live WebView, for a
        // count that had usually not moved at all. The private session only ever
        // changes when that count changes, so an unchanged count is a no-op.
        if (count == lastPrivateCount) return
        lastPrivateCount = count
        // A live WebView is handed over so that ending a private session can
        // clear the HTTP cache through a view that already exists, instead of
        // constructing and destroying a throwaway WebView — which starts and
        // tears down a whole renderer process — on the main thread at the
        // moment the last private tab closes.
        val live = (_tabs.value.firstOrNull { it.id == _activeTabId.value && it.hasWebView }
            ?: _tabs.value.firstOrNull { it.hasWebView })?.webView
        scope.launch { privateManager.syncOpenPrivateTabs(count, settingsProvider().cookiesEnabled, live) }
    }

    /** Destroys every WebView and stops all observation. */
    fun shutdown() {
        _tabs.value.forEach { runCatching { it.destroy() } }
        setTabs(emptyList())
        _tabInfos.value = emptyList()
        _activeTabId.value = null
        lastProgress.clear()
        stoppedLoads.clear()
        pendingLoads.clear()
        rendererVisible.clear()
        appliedCss.clear()
        appliedSettings.clear()
        desktopOverrides.clear()
        rendererLosses.clear()
        faviconToken.clear()
        faviconSeen.clear()
        dntInjected.clear()
        scope.cancel()
    }

    companion object {
        private const val TAG = "NexaTabs"
        private const val MAX_CLOSED_TABS = 20
        private const val START_PROGRESS = 5

        /**
         * How long background-tab changes wait before they are published
         * together. Short enough that a tab strip title never looks wrong when
         * the user glances at it, long enough to fold a whole page load's worth
         * of progress ticks into a single list write.
         */
        private const val BACKGROUND_PUBLISH_DELAY_MS = 250L
    }
}
