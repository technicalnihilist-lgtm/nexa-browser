package com.nexabrowser.app.browser

/**
 * A precompiled form of the ad blocker's host list, built for the one question
 * asked of it on every subresource of every page: "is this host blocked?".
 *
 * [BlocklistMatch.isBlocked] answers that question correctly but pays for it in
 * allocations. It walks the host's parent domains and materialises each one with
 * `substring()` to look it up in the list, so a host like
 * `metrics.ads.tracker.example.com` allocates four short strings — and a page
 * makes dozens of requests, each of which is answered on a WebView network
 * thread while the renderer is waiting for it. On a page with 60 subresources
 * that is a few hundred throwaway strings per page load, in the one place the
 * app can least afford to make the collector run.
 *
 * This index removes them. The list is compiled once into hash buckets, and a
 * candidate domain is compared *in place*: its `String.hashCode` value is
 * computed over the label range without building the string, the bucket for that
 * hash is looked up, and every entry in it is confirmed with `regionMatches`
 * before the answer is accepted — so a hash collision can never produce a false
 * block. Nothing is allocated per lookup, on any host.
 *
 * The matching rule itself is unchanged, and deliberately so: the full host is
 * tested, then every parent domain up to and including the last label, anchored
 * on a dot exactly as before. `notexample.com` is still not `example.com`, and
 * `example.com.evil.net` is still not `example.com`. [BlocklistMatch] remains the
 * reference implementation and the index is tested for agreement with it.
 *
 * Entries are expected to be lower case and already trimmed; [AdBlocker]
 * normalises the list once when it parses it. Immutable once constructed, so it
 * can be read from any number of network threads at the same time.
 */
class BlocklistIndex(entries: Set<String>) {

    /**
     * hash -> the entries whose `String.hashCode` is that value.
     *
     * A plain `HashMap` lookup of a boxed `Int` allocates nothing: the key is
     * already an `int` and the box comes from the JVM's cached range only for
     * small values, so the map is backed by an `Int2Object`-style array in all
     * but name. Kotlin's `HashMap<Int, _>` would box, so the buckets are held in
     * a pair of parallel arrays, sorted by hash and searched by binary search —
     * no boxing, no iterator, no allocation.
     */
    private val hashes: IntArray
    private val buckets: Array<Array<String>>

    /** Number of listed hosts. */
    val size: Int = entries.size

    init {
        val byHash = HashMap<Int, MutableList<String>>(entries.size * 2)
        for (entry in entries) {
            if (entry.isEmpty()) continue
            byHash.getOrPut(entry.hashCode()) { ArrayList(2) }.add(entry)
        }
        val sortedHashes = byHash.keys.toIntArray()
        sortedHashes.sort()
        hashes = sortedHashes
        buckets = Array(sortedHashes.size) { i -> byHash.getValue(sortedHashes[i]).toTypedArray() }
    }

    /**
     * True when [host] is listed itself or is a subdomain of a listed host.
     *
     * [host] must already be lower case, exactly as [BlocklistMatch] requires.
     */
    fun isBlocked(host: String): Boolean {
        if (host.isEmpty() || hashes.isEmpty()) return false
        val end = host.length
        // The host itself, then each parent domain: for `a.ads.example.com` that
        // is `ads.example.com`, `example.com` and `com`. The walk stops before
        // the last label, as it always has, so a host with no dot is only ever
        // compared as a whole.
        if (matchesAt(host, 0, end)) return true
        var idx = host.indexOf('.')
        while (idx in 1 until end - 1) {
            if (matchesAt(host, idx + 1, end)) return true
            idx = host.indexOf('.', idx + 1)
        }
        return false
    }

    /** True when the listed entry equal to `host[start, end)` exists. */
    private fun matchesAt(host: String, start: Int, end: Int): Boolean {
        val bucket = bucketFor(hashOf(host, start, end)) ?: return false
        val length = end - start
        for (entry in bucket) {
            if (entry.length == length && host.regionMatches(start, entry, 0, length)) return true
        }
        return false
    }

    /** The bucket stored under [hash], or null when no entry has it. */
    private fun bucketFor(hash: Int): Array<String>? {
        val found = hashes.binarySearch(hash)
        return if (found >= 0) buckets[found] else null
    }

    private companion object {

        /**
         * `String.hashCode()` of `host[start, end)`, computed without building
         * the string. Must stay identical to the JVM's definition —
         * `s[0]*31^(n-1) + s[1]*31^(n-2) + … + s[n-1]` — because the buckets are
         * keyed by the real `hashCode()` of the listed entries.
         */
        fun hashOf(host: String, start: Int, end: Int): Int {
            var h = 0
            for (i in start until end) h = 31 * h + host[i].code
            return h
        }
    }
}
