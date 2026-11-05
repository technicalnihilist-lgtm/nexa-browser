package com.nexabrowser.app.browser

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.ByteArrayInputStream
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

    /**
     * The blocklist is published as an immutable set once it has been parsed, so
     * readers never see a half-filled list and never need a lock. Requests are
     * matched against this from several WebView network threads at once.
     */
    @Volatile
    private var hosts: Set<String> = emptySet()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
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
        // Runs for every subresource on the network thread. A URL host is almost
        // always already lower case, so only allocate when it is not.
        val h = if (host.hasAsciiUpper()) host.lowercase(Locale.ROOT) else host
        decisions[h]?.let { return it }

        val blocked = computeBlocked(h)
        if (decisions.size >= MAX_CACHED_DECISIONS) decisions.clear()
        decisions[h] = blocked
        return blocked
    }

    private fun computeBlocked(h: String): Boolean {
        val hosts = this.hosts
        if (hosts.isEmpty()) return false
        if (h in hosts) return true
        var idx = h.indexOf('.')
        while (idx in 1 until h.length - 1) {
            if (h.substring(idx + 1) in hosts) return true
            idx = h.indexOf('.', idx + 1)
        }
        return false
    }

    fun blockedCount(): Int = hosts.size

    /**
     * Drops the host → decision memo. Called when the system reports memory
     * pressure: every entry is one boolean and rebuilds itself on the next
     * request for that host, so nothing is lost by letting it go.
     */
    fun trimCaches() {
        decisions.clear()
    }

    /** An empty response used to satisfy (and thus drop) a blocked request. */
    fun emptyResponse(): android.webkit.WebResourceResponse =
        android.webkit.WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(EMPTY_BODY)
        )

    private val EMPTY_BODY = ByteArray(0)

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

    /** Builds the style text for a page from the enabled extensions. */
    fun cssFor(settings: com.nexabrowser.app.data.prefs.BrowserSettings): String {
        val parts = mutableListOf<String>()
        if (settings.darkPagesEnabled) parts.add(DARK_PAGES_CSS)
        if (settings.customCssEnabled && settings.customCss.isNotBlank()) parts.add(settings.customCss)
        return parts.joinToString("\n")
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
