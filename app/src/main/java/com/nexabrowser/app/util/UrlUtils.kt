package com.nexabrowser.app.util

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * Pure URL/query helpers. No Android dependencies, so this object is unit
 * testable on the JVM.
 */
object UrlUtils {

    private val SCHEME_RE = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*:")
    private val DOMAIN_RE = Regex("^([a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$")
    private val IPV4_RE = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val LOCALHOST_RE = Regex("^localhost$", RegexOption.IGNORE_CASE)
    private val HOST_PORT_RE = Regex("^[a-zA-Z0-9.\\-]+:\\d{1,5}$")

    private val SEARCH_SCHEMES = setOf("http", "https", "file", "about", "data", "blob", "content", "javascript", "chrome")
    private val EXTERNAL_SCHEMES = setOf("mailto", "tel", "sms", "smsto", "geo", "market", "intent", "whatsapp", "upi")

    /** Percent-encodes a user query for use in a search URL. */
    fun encodeQuery(query: String): String = URLEncoder.encode(query, "UTF-8")

    /** Builds a search URL from a template containing "%s". */
    fun searchUrl(query: String, template: String): String {
        val t = template.ifBlank { "https://www.google.com/search?q=%s" }
        val encoded = encodeQuery(query)
        return if (t.contains("%s")) t.replace("%s", encoded) else t + encoded
    }

    fun isHttpUrl(url: String): Boolean {
        val l = url.lowercase(Locale.ROOT)
        return l.startsWith("http://") || l.startsWith("https://")
    }

    fun isHttps(url: String): Boolean = url.lowercase(Locale.ROOT).startsWith("https://")

    /** Scheme of [url] in lower case, or "" when there is none. */
    fun schemeOf(url: String): String =
        if (SCHEME_RE.containsMatchIn(url)) url.substringBefore(':').lowercase(Locale.ROOT) else ""

    /** Origin (scheme://host[:port]) used as the key for site permissions. */
    fun originOf(url: String): String? = try {
        val u = URI(url)
        val host = u.host ?: return null
        val port = if (u.port > 0) ":${u.port}" else ""
        "${u.scheme?.lowercase(Locale.ROOT) ?: "https"}://$host$port"
    } catch (_: Exception) {
        null
    }

    fun hostOf(url: String): String = try { URI(url).host ?: "" } catch (_: Exception) { "" }

    /**
     * Decides whether the address-bar text is a URL or a search query and returns
     * the value to load.
     *
     *  - "https://example.com"   -> unchanged
     *  - "example.com"           -> "https://example.com"
     *  - "localhost:8080"        -> "http://localhost:8080"
     *  - "192.168.1.10"          -> "http://192.168.1.10"
     *  - "how does android work" -> search URL for the configured engine
     */
    fun resolveInput(raw: String, queryTemplate: String): String {
        val input = raw.trim()
        if (input.isEmpty()) return ""

        val authority = input.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringBefore(':')

        // "localhost:8080" looks like a scheme followed by a path, but it is a
        // host and port. Only treat the prefix as a scheme when it is not one.
        val looksLikeHostPort = authority.contains(':') &&
            authority.substringAfter(':').all { it.isDigit() } &&
            (DOMAIN_RE.matches(host) || IPV4_RE.matches(host) || LOCALHOST_RE.matches(host))

        val scheme = schemeOf(input)
        if (scheme.isNotEmpty() && !looksLikeHostPort) return input

        if (input.any { it.isWhitespace() }) return searchUrl(input, queryTemplate)

        if (IPV4_RE.matches(host) || LOCALHOST_RE.matches(host)) return "http://$input"
        if (DOMAIN_RE.matches(host)) return "https://$input"
        if (HOST_PORT_RE.matches(authority) && (DOMAIN_RE.matches(host) || IPV4_RE.matches(host) || LOCALHOST_RE.matches(host))) {
            return "https://$input"
        }
        return searchUrl(input, queryTemplate)
    }

    /** Compact form shown in the address bar: scheme hidden for https. */
    fun displayUrl(url: String): String = when {
        url.isBlank() -> ""
        isHttps(url) -> url.removePrefix("https://").removePrefix("http://")
        else -> url
    }

    /** True when the string is a well-formed absolute URL we are willing to load. */
    fun isLoadableUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val scheme = schemeOf(url)
        if (scheme.isEmpty()) return false
        if (scheme !in SEARCH_SCHEMES) return false
        if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") return true
        return try {
            val u = URI(url)
            u.host != null || scheme == "file" || scheme == "content"
        } catch (_: Exception) {
            false
        }
    }

    /** True only for the small allow-list of schemes safe to hand to an Intent. */
    fun isSafeExternalScheme(scheme: String): Boolean =
        scheme.lowercase(Locale.ROOT) in EXTERNAL_SCHEMES
}
