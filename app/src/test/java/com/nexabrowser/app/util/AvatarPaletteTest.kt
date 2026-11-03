package com.nexabrowser.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The avatar colour must stay identical to the inline formula it replaced:
 * `abs(host.hashCode()) % paletteSize`, with an empty host taking the first
 * entry. These tests pin that mapping down, and cover the arithmetic that the
 * inline version got wrong for `Int.MIN_VALUE`.
 */
class AvatarPaletteTest {

    private val size = 8

    /** The original, pre-refactor expression, for comparison. */
    private fun legacyIndex(host: String): Int =
        if (host.isEmpty()) 0
        else (host.hashCode().let { if (it < 0) -it else it }) % size

    @Test
    fun `empty host takes the first entry`() {
        assertEquals(0, AvatarPalette.indexFor("", size))
    }

    @Test
    fun `mapping matches the previous inline formula`() {
        val hosts = listOf(
            "example.com", "www.wikipedia.org", "github.com", "news.ycombinator.com",
            "a", "in.example.co.in", "x.y.z", "mail.google.com", "192.168.1.1",
            "https://fallback.example/path", "एक उदाहरण"
        )
        hosts.forEach { host ->
            assertEquals("host=$host", legacyIndex(host), AvatarPalette.indexFor(host, size))
        }
    }

    @Test
    fun `index is always inside the palette`() {
        val hosts = listOf(
            "polygenelubricants", "GydZG_", "polygenelubricants.com", "", "a.b.c",
            "thequickbrownfoxjumpsoverthelazydog", "Int.MIN_VALUE"
        )
        hosts.forEach { host ->
            val index = AvatarPalette.indexFor(host, size)
            assertTrue("host=$host index=$index", index in 0 until size)
        }
    }

    @Test
    fun `a palette size that does not divide the hash range still yields a valid index`() {
        // 7 does not divide 2^31, which is the case the old abs() arithmetic
        // could have pushed negative for a hash code of Int.MIN_VALUE.
        listOf(1, 3, 7, 13).forEach { paletteSize ->
            listOf("polygenelubricants", "example.com", "").forEach { host ->
                val index = AvatarPalette.indexFor(host, paletteSize)
                assertTrue("size=$paletteSize host=$host", index in 0 until paletteSize)
            }
        }
    }

    @Test
    fun `a non-positive palette size is handled without throwing`() {
        assertEquals(0, AvatarPalette.indexFor("example.com", 0))
        assertEquals(0, AvatarPalette.indexFor("example.com", -4))
    }

    @Test
    fun `the mapping is deterministic`() {
        val host = "example.com"
        assertEquals(AvatarPalette.indexFor(host, size), AvatarPalette.indexFor(host, size))
    }
}
