package com.nexabrowser.app.util

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * Pure URL/query helpers. No Android dependencies, so this object is unit
 * testable on the JVM.
 */
object UrlUtils {

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
     * True for a plaintext `http:` url.
     *
     * The address bar used `url.startsWith("http://")` for this, which is
     * case-sensitive — while [isHttps], right beside it, is not. A url written
     * `HTTP://example.com` is exactly what [HttpsUpgrade] exists to deal with,
     * so such urls do reach the bar, and they fell through both tests: not
     * secure, and not recognised as cleartext either. The bar then drew the
     * secure padlock (the `else` branch) for a page served over plain http.
     *
     * Compares in place and allocates nothing, like its two neighbours.
     */
    fun isCleartextHttp(url: String): Boolean = url.startsWith(HTTP_SCHEME, ignoreCase = true)

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

        // The authority is read by index, not by string surgery. This used to be
        // `substringBefore('/').substringBefore('?').substringBefore('#')` plus
        // `substringBefore(':')` — five fresh strings for a url that is often a
        // few hundred characters long, on every address-bar submit — and then up
        // to twelve `Regex.matches` calls, each of which allocates a `Matcher`,
        // its match state and its group-offset array before it answers.
        // Everything below now scans the one `input` string in place, so a submit
        // allocates nothing until the single result string is built. See
        // [isDomainName], [isIpv4Literal] and [isLocalhost].
        val authorityEnd = firstIndexOf(input, 0, '/', '?', '#')
        val hostEnd = indexOfChar(input, ':', 0, authorityEnd).let { if (it < 0) authorityEnd else it }

        // "localhost:8080" looks like a scheme followed by a path, but it is a
        // host and port. Only treat the prefix as a scheme when it is not one.
        // The port has to be all digits for that to be true, and the part before
        // it has to be something a browser can actually connect to.
        val looksLikeHostPort = hostEnd < authorityEnd &&
            allDigits(input, hostEnd + 1, authorityEnd) &&
            (isDomainName(input, 0, hostEnd) || isIpv4Literal(input, 0, hostEnd) ||
                isLocalhost(input, 0, hostEnd))

        val scheme = schemeOf(input)
        if (scheme.isNotEmpty() && !looksLikeHostPort) return input

        if (input.any { it.isWhitespace() }) return searchUrl(input, queryTemplate)

        if (isIpv4Literal(input, 0, hostEnd) || isLocalhost(input, 0, hostEnd)) return "http://$input"
        if (isDomainName(input, 0, hostEnd)) return "https://$input"
        // The old `HOST_PORT_RE` branch that stood here could never be reached:
        // it required the same three host tests that the two lines above already
        // answered negatively. A `host:port` that is not a domain, an IPv4
        // literal or localhost is a search, which is what falls out below.
        return searchUrl(input, queryTemplate)
    }

    /**
     * Host classification without a regular expression.
     *
     * The three patterns that used to live here were evaluated up to twelve
     * times per address-bar submit, and each evaluation allocated a `Matcher`,
     * a match state and a group-offset array. They are now plain scans over the
     * one input string, with the same answers:
     *
     *  * [isDomainName] is `^([a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$`
     *    — one or more dot-terminated labels that start and end alphanumeric,
     *    then a TLD of at least two letters. A trailing dot, an empty label
     *    (`a..com`) and a numeric TLD all fail, exactly as before.
     *  * [isIpv4Literal] is `^\d{1,3}(\.\d{1,3}){3}$`, and it additionally
     *    requires every octet to be ≤ 255. The pattern accepted `999.1.1.1`,
     *    so typing an out-of-range address in the bar navigated to
     *    `http://999.1.1.1` — a load that can only fail — instead of searching
     *    for it, which is the only useful answer for something that is not an
     *    address.
     *  * [isLocalhost] is `^localhost$` with IGNORE_CASE.
     */
    private fun isDomainName(s: String, from: Int, to: Int): Boolean {
        // "a.bc" is the shortest possible match: two labels, a two-letter TLD.
        if (to - from < 4) return false
        var labelStart = from
        var dots = 0
        var i = from
        while (i <= to) {
            if (i == to || s[i] == '.') {
                val length = i - labelStart
                if (length == 0) return false
                if (i == to) {
                    // The TLD: letters only, at least two of them.
                    if (length < 2) return false
                    for (j in labelStart until to) {
                        val c = s[j]
                        if (!isAsciiLetter(c)) return false
                    }
                    return dots >= 1
                }
                // Every other label starts and ends alphanumeric; a hyphen may
                // only appear inside it.
                if (!isAsciiAlphanumeric(s[labelStart])) return false
                if (!isAsciiAlphanumeric(s[i - 1])) return false
                for (j in labelStart + 1 until i - 1) {
                    val c = s[j]
                    if (!isAsciiAlphanumeric(c) && c != '-') return false
                }
                dots++
                labelStart = i + 1
            }
            i++
        }
        return false
    }

    /** `dotted.quad` of decimal octets, each 1–3 digits and 0–255. */
    private fun isIpv4Literal(s: String, from: Int, to: Int): Boolean {
        var groups = 0
        var i = from
        while (i < to) {
            var value = 0
            var digits = 0
            while (i < to && s[i] in '0'..'9') {
                if (digits == 3) return false
                value = value * 10 + (s[i] - '0')
                digits++
                i++
            }
            if (digits == 0 || value > 255) return false
            groups++
            if (i == to) break
            if (s[i] != '.') return false
            i++
            // A trailing dot is not part of a dotted quad.
            if (i == to) return false
        }
        return groups == 4
    }

    /** `localhost`, in any case. */
    private fun isLocalhost(s: String, from: Int, to: Int): Boolean {
        if (to - from != 9) return false
        val expected = "localhost"
        for (i in 0 until 9) {
            val c = s[from + i]
            val lower = if (c in 'A'..'Z') c + 32 else c
            if (lower != expected[i]) return false
        }
        return true
    }

    private fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    private fun isAsciiAlphanumeric(c: Char): Boolean = isAsciiLetter(c) || c in '0'..'9'

    /** True when every character in [from, to) is a digit; false when empty. */
    private fun allDigits(s: String, from: Int, to: Int): Boolean {
        if (from >= to) return false
        for (i in from until to) if (s[i] !in '0'..'9') return false
        return true
    }

    /** Index of the first of [a], [b] or [c] at or after [from], or [to]. */
    private fun firstIndexOf(s: String, from: Int, a: Char, b: Char, c: Char): Int {
        for (i in from until s.length) {
            val ch = s[i]
            if (ch == a || ch == b || ch == c) return i
        }
        return s.length
    }

    /** Index of [ch] in [from, to), or -1. */
    private fun indexOfChar(s: String, ch: Char, from: Int, to: Int): Int {
        for (i in from until to) if (s[i] == ch) return i
        return -1
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
