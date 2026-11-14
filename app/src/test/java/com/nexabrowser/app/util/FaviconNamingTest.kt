package com.nexabrowser.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaviconNamingTest {

    @Test
    fun `the colliding hosts that broke the old scheme get different files`() {
        // "Aa" and "BB" are the textbook String.hashCode() collision: the old
        // "${host.hashCode()}.png" naming gave both hosts the same file, so one
        // site's icon silently replaced the other's.
        assertEquals("Aa".hashCode(), "BB".hashCode())
        assertNotEquals(FaviconNaming.fileNameFor("Aa"), FaviconNaming.fileNameFor("BB"))
    }

    @Test
    fun `near-identical hosts get different files`() {
        val names = listOf(
            "example.com", "www.example.com", "example.org", "example.co",
            "m.example.com", "example.com.evil.test"
        ).map { FaviconNaming.fileNameFor(it) }.toSet()
        assertEquals(6, names.size)
    }

    @Test
    fun `the same host always maps to the same file`() {
        assertEquals(
            FaviconNaming.fileNameFor("news.example.com"),
            FaviconNaming.fileNameFor("news.example.com")
        )
    }

    @Test
    fun `host case does not produce a second copy of the same icon`() {
        assertEquals(
            FaviconNaming.fileNameFor("Example.COM"),
            FaviconNaming.fileNameFor("example.com")
        )
    }

    @Test
    fun `names are safe, bounded and end in png`() {
        val name = FaviconNaming.fileNameFor("xn--80ak6aa92e.example.co.in")
        assertEquals("${name.dropLast(4)}.png", name)
        assertEquals(36, name.length)
        assertTrue(name.dropLast(4).all { it in "0123456789abcdef" })
    }

    @Test
    fun `legacy hashCode names are recognised`() {
        assertTrue(FaviconNaming.isLegacyName("123456.png"))
        assertTrue(FaviconNaming.isLegacyName("-2147483648.png"))
        assertTrue(FaviconNaming.isLegacyName("0.png"))
    }

    @Test
    fun `new names and temporary files are not mistaken for legacy ones`() {
        assertFalse(FaviconNaming.isLegacyName(FaviconNaming.fileNameFor("example.com")))
        assertFalse(FaviconNaming.isLegacyName("0123456789abcdef0123456789abcdef.png.tmp"))
        assertFalse(FaviconNaming.isLegacyName("123456.png.tmp"))
        assertFalse(FaviconNaming.isLegacyName("abc.png"))
        assertFalse(FaviconNaming.isLegacyName("-"))
        assertFalse(FaviconNaming.isLegacyName(""))
    }

    /**
     * The digest is now reused per thread instead of being created per call, so
     * the name must still be exactly the truncated SHA-256 of the lower-cased
     * host — recomputed here with a fresh `MessageDigest` for comparison.
     */
    @Test
    fun `the reused digest produces the same name as a fresh one`() {
        val hosts = listOf(
            "example.com", "www.example.com", "Example.COM", "news.ycombinator.com",
            "xn--80ak6aa92e.example.co.in", "a", "a.b.c.d.e.f"
        )
        for (host in hosts) {
            assertEquals(sha256Name(host), FaviconNaming.fileNameFor(host))
        }
    }

    /**
     * A reused digest that is not reset would fold the previous host into the
     * next hash; calling the same host again, interleaved with others, is what
     * catches that.
     */
    @Test
    fun `repeated lookups are independent of what came before`() {
        val first = FaviconNaming.fileNameFor("example.com")
        FaviconNaming.fileNameFor("other.example.org")
        FaviconNaming.fileNameFor("third.test")
        assertEquals(first, FaviconNaming.fileNameFor("example.com"))
        assertEquals(sha256Name("example.com"), first)
    }

    /**
     * The digest is per thread, so threads hashing at the same time must not see
     * each other's state.
     */
    @Test
    fun `concurrent lookups agree with the single threaded ones`() {
        val hosts = (0 until 64).map { "host$it.example.com" }
        val expected = hosts.associateWith { FaviconNaming.fileNameFor(it) }
        val errors = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val threads = (0 until 4).map {
            Thread {
                // Reversed order, so a digest shared between threads would fold
                // one host into another's hash and be caught here.
                for (host in hosts.reversed()) {
                    val actual = FaviconNaming.fileNameFor(host)
                    if (actual != expected[host]) errors.add("$host -> $actual")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue("wrong names: $errors", errors.isEmpty())
    }

    private fun sha256Name(host: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(host.lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        val sb = StringBuilder(32)
        var i = 0
        while (sb.length < 32 && i < bytes.size) {
            val v = bytes[i].toInt() and 0xFF
            sb.append(hex[v ushr 4]).append(hex[v and 0x0F])
            i++
        }
        return "$sb.png"
    }
}
