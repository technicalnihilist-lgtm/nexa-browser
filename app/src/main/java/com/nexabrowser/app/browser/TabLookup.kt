package com.nexabrowser.app.browser

/**
 * An id -> item table kept in step with an ordered list of items.
 *
 * The tab manager used to answer "which tab is this?" with a linear scan of the
 * tab list, and it answered that question several times for every WebView
 * callback: a page load fires progress, title, url, favicon and navigation-state
 * events dozens of times, and each one ran `firstOrNull { it.id == id }` once in
 * the event sink and again inside the update. With ten tabs open that is a
 * handful of list traversals per event on the main thread, exactly while the
 * renderer is trying to paint. This table turns each of those into one hash
 * lookup.
 *
 * It is deliberately plain Kotlin with no Android types so the invariant that
 * matters — the table never disagrees with the list — can be unit tested.
 *
 * Not thread safe: the tab manager owns it and only touches it from the main
 * thread, the same thread that writes the tab list.
 */
class TabLookup<T : Any> {

    private val items = HashMap<String, T>(16)

    /** Number of entries currently held. */
    val size: Int get() = items.size

    /** The item stored under [key], or null when the key is unknown. */
    operator fun get(key: String?): T? {
        if (key == null) return null
        return items[key]
    }

    fun contains(key: String?): Boolean = key != null && items.containsKey(key)

    /** Stores one item, replacing any entry with the same key. */
    fun put(key: String, value: T) {
        items[key] = value
    }

    fun remove(key: String?) {
        if (key != null) items.remove(key)
    }

    /**
     * Replaces the whole table with [list]. Used after any structural change to
     * the tab list, so a removed tab can never keep being found here.
     */
    fun setAll(list: List<T>, key: (T) -> String) {
        items.clear()
        for (item in list) items[key(item)] = item
    }

    fun clear() = items.clear()
}
