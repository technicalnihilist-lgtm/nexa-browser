package com.nexabrowser.app.util

import java.security.MessageDigest
import java.util.Locale

/**
 * File names for the on-disk favicon cache.
 *
 * One PNG per host lives in the app cache directory, and the name used to be
 * `"${host.hashCode()}.png"` — a 32-bit `String.hashCode()`. That is not a
 * fingerprint: two different hosts that collide produce the same file name, so
 * the second host's icon overwrote the first's, and every later visit to the
 * first host showed the *other* site's icon. `pathForHost` could not notice,
 * because the path it resolved genuinely existed. Collisions are not
 * hypothetical — `"Aa"` and `"BB"` hash alike, and so do real host names — and
 * they are silent and permanent, since the wrong icon stays cached for as long
 * as the file survives.
 *
 * The name is now a truncated SHA-256 of the host: 128 bits, deterministic
 * across processes and app versions (so a cached icon is found again after a
 * restart), and short enough for any filesystem. The hash is computed only when
 * a host is looked up for the first time in a session or when its icon is
 * written, both of which already run off the main thread.
 */
object FaviconNaming {

    /** Hex characters kept from the digest: 32 chars, 128 bits. */
    private const val HEX_LENGTH = 32

    /** The file name for [host]'s stored icon. Pure and deterministic. */
    fun fileNameFor(host: String): String = digestOf(host) + ".png"

    /**
     * True for a file name written by the old `hashCode()` scheme: a decimal
     * integer, possibly negative, then `.png`. Used to clean those up, since
     * nothing can resolve them any more.
     */
    fun isLegacyName(name: String): Boolean {
        if (!name.endsWith(".png")) return false
        val stem = name.substring(0, name.length - 4)
        if (stem.isEmpty() || stem.length > 11) return false
        var i = 0
        if (stem[0] == '-') {
            if (stem.length == 1) return false
            i = 1
        }
        while (i < stem.length) {
            if (stem[i] !in '0'..'9') return false
            i++
        }
        return true
    }

    /**
     * Lower-cased first, so `Example.com` and `example.com` — the same host, and
     * the same icon — share one file instead of writing two.
     *
     * The digest is reused per thread rather than created per call.
     * `MessageDigest.getInstance` is not a cheap constructor: it resolves the
     * provider, instantiates the SPI object and allocates its padding and state
     * buffers — several microseconds and half a dozen objects, every time. This
     * runs once per stored icon and once per host looked up for the first time
     * in a session, so on a long browsing session it was a few thousand of those
     * for a hash of a 20-byte string. A `MessageDigest` is stateful and not
     * thread safe, so one instance is kept per thread and [MessageDigest.reset]
     * clears it before each use — the same pattern the formatters in
     * [com.nexabrowser.app.util.DateLabels] use, and the digests it produces are
     * byte-for-byte the ones it produced before.
     */
    private fun digestOf(host: String): String {
        val digest: MessageDigest = sha256.get()
        digest.reset()
        val bytes = digest.digest(host.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(HEX_LENGTH)
        var i = 0
        while (sb.length < HEX_LENGTH && i < bytes.size) {
            val v = bytes[i].toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
            i++
        }
        return sb.toString()
    }

    /**
     * One SHA-256 digest per thread, reused in place. See [digestOf].
     *
     * A digest that is only ever touched through `reset()` and `digest()` on the
     * thread that owns it cannot be observed half-updated by another thread, and
     * `MessageDigest` is specified to be reset by `digest()` as well, so a call
     * that fails in the middle cannot poison the next one.
     */
    private val sha256: ThreadLocal<MessageDigest> = object : ThreadLocal<MessageDigest>() {
        override fun initialValue(): MessageDigest = MessageDigest.getInstance("SHA-256")
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
