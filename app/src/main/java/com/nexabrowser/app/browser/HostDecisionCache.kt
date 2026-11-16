package com.nexabrowser.app.browser

import java.util.concurrent.ConcurrentHashMap

/**
 * The ad blocker's bounded "host -> blocked" memo.
 *
 * It is consulted for every subresource of every page, on the WebView's network
 * threads, while the renderer is waiting for the answer. It used to be a single
 * `ConcurrentHashMap` with one eviction rule: when it reached its bound it was
 * emptied. That throws away the whole table at the moment it is most valuable.
 *
 * A page that is heavy with advertising and attribution requests asks about a
 * few hundred *distinct* hosts, and a session crosses the old bound regularly.
 * Each clear dropped every entry, including the handful of hosts that page
 * re-requests on every single navigation (its own CDN, its image host, its
 * analytics endpoint). Those are the entries that pay for the cache; the long
 * tail of one-shot tracker subdomains is what fills it. Clearing therefore cost
 * a full [BlocklistIndex] walk — the host's own label plus every parent domain
 * hashed and compared — for exactly the requests that were guaranteed to be
 * asked again, and it did it on a network thread with the renderer blocked.
 *
 * This keeps two generations instead of one. New answers go into the young
 * table; when the young table is full it becomes the old one and a fresh young
 * table starts. A lookup checks young, then old. Nothing that was hot in the
 * last generation is lost at a rollover — it is only lost when the generation
 * *after* that rolls over, by which time the page has moved on — and the bound
 * is unchanged at twice [generationSize], i.e. the same 1024 entries the single
 * table held before.
 *
 * Deliberately simple, because it runs concurrently: no LRU list to maintain, no
 * lock on the read path, and no allocation per lookup. The only synchronised
 * path is the rollover, which happens once per [generationSize] new hosts.
 *
 * Immutable-key, immutable-value, so a read never needs the monitor. Kept free
 * of Android types so the eviction rule is unit tested on the JVM.
 */
class HostDecisionCache(private val generationSize: Int = DEFAULT_GENERATION_SIZE) {

    init {
        require(generationSize > 0) { "generationSize must be positive" }
    }

    /**
     * The current generation. Volatile because [rollOver] swaps it while other
     * threads are reading it; a thread that reads the previous reference for one
     * more lookup simply gets a table that is still a valid generation.
     */
    @Volatile
    private var young: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap(generationSize)

    /** The previous generation, kept readable so a rollover is not a cliff. */
    @Volatile
    private var old: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap(generationSize)

    /** Number of entries held across both generations, at most 2 * [generationSize]. */
    val size: Int get() = young.size + old.size

    /** The remembered answer for [host], or null when it has not been asked. */
    fun get(host: String): Boolean? = young[host] ?: old[host]

    /**
     * Remembers the answer for [host].
     *
     * The young table is always preferred by [get], so a host that is asked
     * again after a rollover is re-answered from the fresh table and the stale
     * copy in the old one can never win.
     */
    fun put(host: String, blocked: Boolean) {
        // A plain size() check on the fast path: `put` is called once per
        // distinct host, not once per request, and the rollover is rare.
        if (young.size >= generationSize) rollOver()
        young[host] = blocked
    }

    /** Drops everything. Called when the system reports real memory pressure. */
    fun clear() {
        synchronized(this) {
            young = ConcurrentHashMap(generationSize)
            old = ConcurrentHashMap(generationSize)
        }
    }

    /**
     * Promotes the young table and starts a new one.
     *
     * Guarded so that two network threads reaching the bound at the same moment
     * do not roll twice — which would demote a table of brand-new entries into
     * the old slot and throw away the generation that was still warm.
     */
    private fun rollOver() {
        synchronized(this) {
            // Re-checked under the lock: another thread may have just rolled.
            if (young.size < generationSize) return
            old = young
            young = ConcurrentHashMap(generationSize)
        }
    }

    companion object {
        /**
         * Half the total bound, so the cache still holds 1024 answers overall —
         * the same memory the single table had before.
         */
        const val DEFAULT_GENERATION_SIZE = 512
    }
}
