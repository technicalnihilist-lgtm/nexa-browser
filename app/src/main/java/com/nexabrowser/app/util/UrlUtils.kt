package com.nexabrowser.app.util

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * Pure URL/query helpers. No Android dependencies, so this object is unit
 * testable on the JVM.
 */
object UrlUtils {

    private val DOMAIN_RE = Regex("^([a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$")
    private val IPV4_RE = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val LOCALHOST_RE = Regex("^localhost$", RegexOption.IGNORE_CASE)
    private val HOST_PORT_RE = Regex("^[a-zA-Z0-9.\\-]+:\\d{1,5}$")

    private const val HTTP_SCHEME = "http://"
    private const val HTTPS_SCHEME = "https://"

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

    /**
     * Scheme checks, answered without allocating.
     *
     * Both used to lower-case the whole url first (`url.lowercase(Locale.ROOT)`),
     * which builds a second copy of the entire string — and they are called on the
     * paths the UI hits most: `displayUrl()` asks [isHttps] for every row of the
     * history, bookmark, reading-list, start-page and tab-overview lists, and the
     * address bar asks it on every tab state change. A 200-character url meant a
     * 400-byte string plus its char array, per row, per recomposition, for an
     * answer that is decided by the first eight characters.
     *
     * `startsWith(prefix, ignoreCase = true)` compares in place, so nothing is
     * allocated and the case-insensitivity is unchanged.
     */
    fun isHttpUrl(url: String): Boolean =
        url.startsWith(HTTP_SCHEME, ignoreCase = true) || url.startsWith(HTTPS_SCHEME, ignoreCase = true)

    fun isHttps(url: String): Boolean = url.startsWith(HTTPS_SCHEME, ignoreCase = true)

    /**
     * Scheme of [url] in lower case, or "" when there is none.
     *
     * This used to run the `SCHEME_RE` regular expression over the whole url and
     * then `substringBefore(':')`. A `Regex` match allocates a `Matcher`, its
     * match state and an `IntArray` of group offsets before it answers, and the
     * answer only ever depends on the first few characters: a scheme is a letter,
     * then letters, digits, `+`, `-` or `.`, then a colon. [schemeEnd] scans
     * exactly those and stops at the first character that cannot be part of a
     * scheme, so the common case — a url with no scheme, or an `https` one — is
     * decided in a handful of character comparisons and allocates nothing.
     *
     * It is called on every address-bar submit (twice: once to resolve the input,
     * once to check the resulting scheme), for every tab of a restored session,
     * and for every site-permission prompt, so it is worth not running a regex
     * engine for it. The returned value is identical to the regex's for every
     * input, including upper-case schemes (`HTTP://x` -> `http`) and strings whose
     * first character cannot start a scheme (`1http://x` -> "").
     */
    fun schemeOf(url: String): String {
        val end = schemeEnd(url)
        if (end <= 0) return ""
        // A url's scheme is almost always already lower case, so only pay for the
        // copy and the locale walk when there is actually something to fold.
        var upper = false
        for (i in 0 until end) {
            if (url[i] in 'A'..'Z') {
                upper = true
                break
            }
        }
        return if (upper) url.substring(0, end).lowercase(Locale.ROOT) else url.substring(0, end)
    }

    /**
     * Index of the `:` that ends a scheme at the start of [url], or -1 when the
     * string does not begin with one. Equivalent to `^[a-zA-Z][a-zA-Z0-9+.\-]*:`
     * and allocation-free.
     */
    private fun schemeEnd(url: String): Int {
        if (url.isEmpty()) return -1
        val first = url[0]
        if (first !in 'a'..'z' && first !in 'A'..'Z') return -1
        for (i in 1 until url.length) {
            val c = url[i]
            if (c == ':') return i
            val ok = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '+' || c == '-' || c == '.'
            if (!ok) return -1
        }
        return -1
    }

    /**
     * Origin (scheme://host[:port]) used as the key for site permissions.
     *
     * Answered by [authorityOf], which is the same answer `java.net.URI` gives
     * for every URL this app meets, without constructing a URI object.
     */
    fun originOf(url: String): String? {
        val (host, port) = authorityOf(url) ?: return null
        val scheme = schemeOf(url).ifEmpty { "https" }
        return if (port > 0) "$scheme://$host:$port" else "$scheme://$host"
    }

    /**
     * Host of [url], or "" when it has none.
     *
     * This used to be `URI(url).host`, and that had two costs:
     *
     *  * **Allocations.** A `java.net.URI` parse builds the URI, its internal
     *    string fields and (for the common hierarchical case) the parsed
     *    authority. This runs once per completed page load — to name the stored
     *    favicon — once per permission prompt, and once per row of the start
     *    page, history, bookmark and reading-list lists through
     *    `LetterAvatar`. The answer only ever depends on the characters up to
     *    the end of the authority, so [authorityOf] scans exactly those and
     *    returns one small pair plus the host substring it needs — against a
     *    URI object, its parsed fields and, for anything it dislikes, a thrown
     *    and caught `URISyntaxException`.
     *  * **Silent failures.** `URI` rejects a string with a space or a non-ASCII
     *    character anywhere in it, path included, and the old code turned that
     *    rejection into "". A page whose URL carries a Devanagari, Tamil or
     *    Chinese path — or any URL the browser got away with loading while
     *    containing a raw space — therefore had no host, so no favicon was
     *    stored for it and a site permission was keyed on the whole URL instead
     *    of its origin. The fast path reads the authority and stops before the
     *    path, so those URLs now resolve normally.
     *
     * Anything the fast path is not certain about (a non-ASCII host, a port
     * outside 1..65535, an authority with characters `URI` would reject) falls
     * back to `URI`, so the behaviour for exotic input is exactly what it was.
     */
    fun hostOf(url: String): String = authorityOf(url)?.first.orEmpty()

    /**
     * The authority of [url] as (host, port), or null when it has none.
     *
     * Host keeps its original case and an IPv6 literal keeps its brackets, both
     * of which is what `java.net.URI.host` returns, so callers cannot tell the
     * two paths apart. Port is 0 when the URL carries none.
     */
    private fun authorityOf(url: String): Pair<String, Int>? {
        val fast = fastAuthorityOf(url)
        if (fast != null) return fast
        return try {
            val u = URI(url)
            val host = u.host ?: return null
            host to (if (u.port > 0) u.port else 0)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads `scheme://host[:port]` straight out of [url].
     *
     * Returns null — never a guess — for anything that is not a plain ASCII
     * hierarchical URL, so [authorityOf] can hand those to `URI`.
     */
    private fun fastAuthorityOf(url: String): Pair<String, Int>? {
        val schemeEnd = url.indexOf(':')
        if (schemeEnd <= 0) return null
        for (i in 0 until schemeEnd) {
            val c = url[i]
            val ok = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '+' || c == '-' || c == '.'
            if (!ok) return null
        }
        // Only the hierarchical form has an authority; mailto:, data: and
        // about: do not, and URI reports no host for them either.
        var start = schemeEnd + 1
        if (start + 1 >= url.length || url[start] != '/' || url[start + 1] != '/') return null
        start += 2

        var end = url.length
        for (i in start until url.length) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#') {
                end = i
                break
            }
        }
        // userinfo@ is dropped: the host starts after the last '@'.
        var hostStart = start
        for (i in start until end) if (url[i] == '@') hostStart = i + 1
        if (hostStart >= end) return null

        // IPv6 literal: keep the brackets, as URI does.
        if (url[hostStart] == '[') {
            val close = url.indexOf(']', hostStart)
            if (close < 0 || close >= end) return null
            val host = url.substring(hostStart, close + 1)
            val after = close + 1
            if (after == end) return host to 0
            if (url[after] != ':') return null
            val port = readPort(url, after + 1, end) ?: return null
            return host to port
        }

        var hostEnd = end
        var port = 0
        val colon = url.lastIndexOf(':', end - 1)
        if (colon > hostStart) {
            port = readPort(url, colon + 1, end) ?: return null
            hostEnd = colon
        }
        if (hostEnd <= hostStart) return null
        // A host is ASCII letters, digits, '-' and '.'; anything else (an IDN
        // host, a stray space, a control character) goes to URI, which either
        // knows better or rejects it exactly as before.
        for (i in hostStart until hostEnd) {
            val c = url[i]
            val ok = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '.' || c == '_'
            if (!ok) return null
        }
        return url.substring(hostStart, hostEnd) to port
    }

    /** Digits between [from] and [to] as a port, or null when they are not one. */
    private fun readPort(url: String, from: Int, to: Int): Int? {
        if (from >= to) return null
        var value = 0
        for (i in from until to) {
            val c = url[i]
            if (c !in '0'..'9') return null
            value = value * 10 + (c - '0')
            if (value > 65535) return null
        }
        return value
    }

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

    /**
     * Compact form shown in the address bar: scheme hidden for https.
     *
     * `removePrefix("https://")` is case-sensitive, so a url written as
     * `HTTPS://example.com` — which [isHttps] correctly reports as secure — was
     * shown with its scheme intact while every other secure url had it hidden.
     * The scheme is now stripped by length when the case-insensitive check above
     * matched, which also drops the second `removePrefix` (always a no-op on an
     * https url) and the copy it could make.
     */
    fun displayUrl(url: String): String = when {
        url.isBlank() -> ""
        isHttps(url) -> url.substring(HTTPS_SCHEME.length)
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
