package com.nexabrowser.app.browser

/**
 * The host-matching rule behind the ad blocker, kept free of Android types so it
 * can be unit tested on the JVM rather than inferred from a device log.
 *
 * A request host is blocked when it equals a list entry or is a subdomain of one:
 * `example.com` blocks `ads.example.com`, but never `notexample.com` and never
 * `example.com.evil.net`. Matching walks the host's own label boundaries, so the
 * answer is always anchored on a dot — a plain `endsWith` would have let
 * `badexample.com` through for the entry `example.com`.
 *
 * This used to live inside [AdBlocker] and was only reachable through a WebView
 * request, which meant the rule — the one thing standing between a page and a
 * tracker — had no test at all.
 */
object BlocklistMatch {

    /**
     * True when [host] is [blocked] itself or a subdomain of a [blocked] entry.
     *
     * [host] must already be lower case; [AdBlocker] normalises it once per
     * request and the result is what it memoises.
     */
    fun isBlocked(host: String, blocked: Set<String>): Boolean {
        if (host.isEmpty() || blocked.isEmpty()) return false
        if (host in blocked) return true
        // Test each parent domain: for `a.ads.example.com` that is
        // `ads.example.com`, `example.com` and `com`. The walk stops before the
        // last label, so a bare TLD can never be matched by accident.
        var idx = host.indexOf('.')
        while (idx in 1 until host.length - 1) {
            val suffix = host.substring(idx + 1)
            if (suffix in blocked) return true
            idx = host.indexOf('.', idx + 1)
        }
        return false
    }
}
