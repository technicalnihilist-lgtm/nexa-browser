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
    private val hosts = HashSet<String>(512)
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        val line = raw.trim()
                        if (line.isEmpty() || line.startsWith("#")) return@forEach
                        hosts.add(line.lowercase(Locale.ROOT))
                    }
                }
            }.onFailure { Log.w(TAG, "Could not read the blocklist", it) }
            loaded = true
            Log.i(TAG, "Blocklist loaded: ${hosts.size} hosts")
        }
    }

    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase(Locale.ROOT)
        if (h in hosts) return true
        var idx = h.indexOf('.')
        while (idx in 1 until h.length - 1) {
            if (h.substring(idx + 1) in hosts) return true
            idx = h.indexOf('.', idx + 1)
        }
        return false
    }

    fun blockedCount(): Int = hosts.size

    /** An empty response used to satisfy (and thus drop) a blocked request. */
    fun emptyResponse(): android.webkit.WebResourceResponse =
        android.webkit.WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
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
