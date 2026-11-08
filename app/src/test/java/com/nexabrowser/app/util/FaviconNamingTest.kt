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
}
