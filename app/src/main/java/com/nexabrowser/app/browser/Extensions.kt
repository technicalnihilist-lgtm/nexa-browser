package com.nexabrowser.app.browser

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.InputStream
import java.util.Locale

/**
 * Network-level ad and tracker blocking.
 *
 * A request is blocked when its host equals a list entry or is a subdomain of
 * one. The list is deliberately conservative — advertising, analytics and
 * attribution endpoints only — so ordinary site functionality is untouched.
 * Blocked requests are answered with an empty response rather than being
 * dropped, so the page keeps loading normally.
 */
object AdBlocker {

    private const val TAG = "NexaAdBlock"

    /** The index for a list that has not been read yet: matches nothing. */
    private val EMPTY_INDEX = BlocklistIndex(emptySet())

    /**
     * The blocklist is published as an immutable set once it has been parsed, so
     * readers never see a half-filled list and never need a lock. Requests are
     * matched against this from several WebView network threads at once.
     */
    @Volatile
    private var hosts: Set<String> = emptySet()

    /**
     * [hosts] compiled into a form that can be searched without allocating.
     *
     * Published together with [hosts] under the same lock, so a request thread
     * always sees a complete pair. See [BlocklistIndex] for why the walk over a
     * host's parent domains no longer builds a string per label.
     */
    @Volatile
    private var index: BlocklistIndex = EMPTY_INDEX

    @Volatile
    private var loaded = false

    /**
     * Application context, kept only so a request that arrives before the
     * background load has finished can still parse the list itself.
     */
    @Volatile
    private var appContext: Context? = null

    /**
     * Remembers the application context without touching the asset, so
     * [isBlocked] can finish the load itself if it has to.
     */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Reads the bundled blocklist.
     *
     * This used to be called from `Application.onCreate`, i.e. on the main
     * thread before the first frame: an asset open, a buffered read of the whole
     * file and a lowercasing pass over every line, all in front of the window the
     * user is waiting for. It now runs on a background thread started from
     * `onCreate`, and [isBlocked] loads the list on demand if a request beats it
     * there — network callbacks never run on the main thread, so the same work
     * simply happens where there is a frame to spare.
     */
    fun ensureLoaded(context: Context) {
        appContext = context.applicationContext
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val parsed = HashSet<String>(256)
            runCatching {
                context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        val line = raw.trim()
                        if (line.isEmpty() || line.startsWith("#")) return@forEach
                        parsed.add(line.lowercase(Locale.ROOT))
                    }
                }
            }.onFailure { Log.w(TAG, "Could not read the blocklist", it) }
            hosts = parsed
            // Compiled once, here, on the background thread that parsed the
            // list — never on a request thread.
            index = BlocklistIndex(parsed)
            loaded = true
            Log.i(TAG, "Blocklist loaded: ${parsed.size} hosts")
        }
    }

    /**
     * Host → blocked decisions, consulted for every subresource on the network
     * threads. This used to be a synchronised LinkedHashMap, so every request in
     * flight serialised on one monitor; a ConcurrentHashMap answers reads without
     * locking at all. The table is simply dropped when it grows past its bound —
     * rebuilding a few hundred entries is far cheaper than maintaining LRU order
     * under contention.
     */
    private val decisions = java.util.concurrent.ConcurrentHashMap<String, Boolean>(512)

    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        // Called for every subresource on a WebView network thread. If the
        // background load has not finished yet, finish it here rather than
        // letting the first requests of the first page through unblocked.
        if (!loaded) appContext?.let { ensureLoaded(it) }
        // Runs for every subresource on the network thread. A URL host is almost
        // always already lower case, so only allocate when it is not. The scan
        // is kept deliberately: `lowercase()` alone also scans, and measured on
        // this path (400 hosts, 20M lookups) the guarded form is ~10% faster
        // than calling it unconditionally — it avoids the locale plumbing for
        // the answer "nothing to do".
        val h = if (host.hasAsciiUpper()) host.lowercase(Locale.ROOT) else host
        decisions[h]?.let { return it }

        val blocked = computeBlocked(h)
        if (decisions.size >= MAX_CACHED_DECISIONS) decisions.clear()
        decisions[h] = blocked
        return blocked
    }

    /**
     * The matching rule itself lives in [BlocklistMatch], where it is tested;
     * the list is searched through [BlocklistIndex], which applies the same rule
     * without allocating a string per parent domain. Both are kept: the plain
     * rule is the reference the index is checked against in the unit tests.
     */
    private fun computeBlocked(h: String): Boolean = index.isBlocked(h)

    fun blockedCount(): Int = hosts.size

    /**
     * Drops the host → decision memo. Called when the system reports memory
     * pressure: every entry is one boolean and rebuilds itself on the next
     * request for that host, so nothing is lost by letting it go.
     */
    fun trimCaches() {
        decisions.clear()
    }

    /**
     * An empty response used to satisfy (and thus drop) a blocked request.
     *
     * This is built for every blocked subresource — an ad-heavy page blocks
     * dozens in one load — and each call used to allocate a `ByteArrayInputStream`
     * over an empty array as well. The body is a constant, so the stream is a
     * constant too: [EMPTY_STREAM] is stateless (every read reports end-of-body
     * and `close()` does nothing), which makes it safe to hand to any number of
     * concurrent requests on the WebView's network threads. Only the small
     * response object, which WebView may keep per request, is still created
     * here.
     */
    fun emptyResponse(): android.webkit.WebResourceResponse =
        android.webkit.WebResourceResponse(
            "text/plain",
            "utf-8",
            EMPTY_STREAM
        )

    /**
     * A stream over an empty body: every read is end-of-stream, and closing it is
     * a no-op so one instance can be shared between requests.
     */
    private val EMPTY_STREAM: InputStream = object : InputStream() {
        override fun read(): Int = -1
        override fun read(b: ByteArray, off: Int, len: Int): Int = -1
        override fun available(): Int = 0
        override fun skip(n: Long): Long = 0
        override fun close() = Unit
    }

    private const val MAX_CACHED_DECISIONS = 1024


    private fun String.hasAsciiUpper(): Boolean {
        for (i in indices) if (this[i] in 'A'..'Z') return true
        return false
    }
}

/** Page-level tweaks that are delivered as injected CSS. */
object PageInjections {

    private const val STYLE_ID = "nexa-extension-style"

    /** Inverts the page while keeping photos and video looking natural. */
    val DARK_PAGES_CSS = """
        html{filter:invert(1) hue-rotate(180deg) !important;background:#fff !important}
        img,video,picture,canvas,svg,iframe,[style*="background-image"]{
          filter:invert(1) hue-rotate(180deg) !important
        }
    """.trimIndent()

    /**
     * The stylesheet last built, with the three settings that decide it.
     *
     * [cssFor] is asked on every completed page load of every tab and on every
     * settings emission, and the answer is the same string until one of those
     * three preferences changes. Remembering it turns a `MutableList`, a
     * `StringBuilder` and a fresh string per call into one reference comparison.
     */
    @Volatile
    private var cachedKey: CssKey? = null

    @Volatile
    private var cachedCss: String = ""

    private data class CssKey(val darkPages: Boolean, val customCss: String)

    /** Builds the style text for a page from the enabled extensions. */
    fun cssFor(settings: com.nexabrowser.app.data.prefs.BrowserSettings): String {
        // The default configuration — no page extensions — allocates nothing at
        // all and returns the empty string, which is the case that runs on
        // almost every page load.
        val custom = if (settings.customCssEnabled) settings.customCss else ""
        val dark = settings.darkPagesEnabled
        if (!dark && custom.isBlank()) return ""

        val key = CssKey(dark, custom)
        val cached = cachedKey
        if (cached != null && cached == key) return cachedCss

        val css = when {
            dark && custom.isNotBlank() -> DARK_PAGES_CSS + "\n" + custom
            dark -> DARK_PAGES_CSS
            else -> custom
        }
        cachedKey = key
        cachedCss = css
        return css
    }

    /** JavaScript that installs (or clears) the injected style element. */
    fun applyScript(css: String): String {
        val escaped = css
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        return "(function(){var id='$STYLE_ID';var el=document.getElementById(id);" +
            "if(!el){el=document.createElement('style');el.id=id;" +
            "(document.head||document.documentElement).appendChild(el);}" +
            "el.textContent='$escaped';})();"
    }

    /**
     * JavaScript that removes the injected style element again. Needed when the
     * last page extension is switched off: without it the previously injected
     * CSS would stay on the page until the next navigation.
     */
    fun clearScript(): String =
        "(function(){var el=document.getElementById('$STYLE_ID');" +
            "if(el&&el.parentNode){el.parentNode.removeChild(el);}})();"

    /** True for hosts that must never be upgraded to HTTPS. */
    fun isLocalHost(host: String?): Boolean {
        val h = host?.lowercase(Locale.ROOT) ?: return true
        return h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "10.0.2.2" ||
            h.startsWith("127.") || h.endsWith(".local")
    }
}

/** Strips marketing/attribution parameters from a URL. */
object LinkCleaner {

    private val trackingParams = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "utm_name", "utm_reader", "utm_social", "utm_brand",
        "gclid", "dclid", "gbraid", "wbraid", "fbclid", "msclkid", "twclid",
        "igshid", "mc_cid", "mc_eid", "yclid", "ttclid", "_hsenc", "_hsmi",
        "vero_id", "oly_anon_id", "oly_enc_id", "rb_clickid", "s_cid",
        "ref_src", "ref_url", "spm", "scm"
    )

    fun clean(url: String): String {
        if (url.isBlank() || !url.contains('?')) return url
        return runCatching {
            val uri = Uri.parse(url)
            val names = uri.queryParameterNames
            val kept = names.filterNot { it.lowercase(Locale.ROOT) in trackingParams }
            if (kept.size == names.size) return url
            val builder = uri.buildUpon().clearQuery()
            kept.forEach { name ->
                uri.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
            }
            builder.build().toString()
        }.getOrDefault(url)
    }
}
