package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [BlocklistIndex] has to answer exactly what [BlocklistMatch] answers — it is
 * the same rule, compiled for a hot path — so most of these tests check it
 * against the reference implementation rather than restating the rule by hand.
 */
class BlocklistIndexTest {

    private val blocked = setOf("example.com", "ads.example.com", "doubleclick.net")

    private fun indexOf(entries: Set<String>) = BlocklistIndex(entries)

    @Test
    fun `it agrees with the reference rule on a corpus of hosts`() {
        val index = indexOf(blocked)
        val hosts = listOf(
            "", "com", "net", "localhost", "127.0.0.1",
            "example.com", "EXAMPLE.com".lowercase(),
            "ads.example.com", "a.ads.example.com", "stats.ads.example.com",
            "notexample.com", "myexample.com", "example.com.evil.net",
            "example.co.uk", "example.com.", "ads.example.com.",
            "doubleclick.net", "cdn.doubleclick.net", "baddoubleclick.net",
            "tracker.example.com", "example.com:8080"
        )
        for (host in hosts) {
            assertThat(index.isBlocked(host)).isEqualTo(BlocklistMatch.isBlocked(host, blocked))
        }
    }

    @Test
    fun `it agrees with the reference rule on generated hosts`() {
        // Every combination of the list's labels and a set of near-misses, so a
        // label-boundary mistake cannot hide behind a hand-picked case.
        val index = indexOf(blocked)
        val prefixes = listOf("", "a.", "b.c.", "not", "ads.", "x.y.z.")
        val bases = listOf(
            "example.com", "ads.example.com", "doubleclick.net",
            "example.co", "com", "example.com.evil.net", "doubleclick.network"
        )
        for (prefix in prefixes) {
            for (base in bases) {
                val host = prefix + base
                assertThat(index.isBlocked(host)).isEqualTo(BlocklistMatch.isBlocked(host, blocked))
            }
        }
    }

    @Test
    fun `an exact host is blocked`() {
        val index = indexOf(blocked)
        assertThat(index.isBlocked("example.com")).isTrue()
        assertThat(index.isBlocked("doubleclick.net")).isTrue()
    }

    @Test
    fun `a subdomain of a listed host is blocked`() {
        val index = indexOf(blocked)
        assertThat(index.isBlocked("ads.example.com")).isTrue()
        assertThat(index.isBlocked("stats.ads.example.com")).isTrue()
        assertThat(index.isBlocked("a.b.c.example.com")).isTrue()
    }

    @Test
    fun `a host that merely ends with a listed name is not blocked`() {
        val index = indexOf(blocked)
        assertThat(index.isBlocked("notexample.com")).isFalse()
        assertThat(index.isBlocked("myexample.com")).isFalse()
        assertThat(index.isBlocked("example.com.evil.net")).isFalse()
    }

    @Test
    fun `an empty list blocks nothing`() {
        val index = indexOf(emptySet())
        assertThat(index.isBlocked("example.com")).isFalse()
        assertThat(index.isBlocked("")).isFalse()
        assertThat(index.size).isEqualTo(0)
    }

    @Test
    fun `a hash collision cannot produce a false block`() {
        // "Aa" and "BB" share a String.hashCode. They are different hosts, so a
        // lookup that trusted the hash alone would block one of them.
        assertThat("Aa".hashCode()).isEqualTo("BB".hashCode())
        val index = indexOf(setOf("Aa.com"))
        assertThat(index.isBlocked("Aa.com")).isTrue()
        assertThat(index.isBlocked("BB.com")).isFalse()
    }

    @Test
    fun `a host with no dot is only matched as a whole`() {
        val index = indexOf(setOf("com"))
        assertThat(index.isBlocked("com")).isTrue()
        assertThat(index.isBlocked("localhost")).isFalse()
        assertThat(index.isBlocked("")).isFalse()
    }

    @Test
    fun `the reported size is the number of listed hosts`() {
        assertThat(indexOf(blocked).size).isEqualTo(3)
    }
}
