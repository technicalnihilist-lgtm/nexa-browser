package com.nexabrowser.app.util

/**
 * Picks the accent colour for a letter avatar from a fixed palette.
 *
 * Kept free of Android and Compose types so the mapping is unit tested. The
 * mapping itself is unchanged from the inline version it replaces: the absolute
 * value of the host's hash code, modulo the palette size. Only the arithmetic
 * changed — it is done in `Long`, so a host whose hash code is `Int.MIN_VALUE`
 * can no longer produce a negative index (which would have thrown on a palette
 * of any size that does not divide 2^31).
 */
object AvatarPalette {

    /**
     * Index into a palette of [size] entries for [host]. [host] is the site
     * host, or the raw url when the host could not be parsed; an empty host
     * always gets the first entry.
     */
    fun indexFor(host: String, size: Int): Int {
        if (size <= 0) return 0
        if (host.isEmpty()) return 0
        val hash = host.hashCode().toLong()
        val magnitude = if (hash < 0L) -hash else hash
        return (magnitude % size).toInt()
    }
}
