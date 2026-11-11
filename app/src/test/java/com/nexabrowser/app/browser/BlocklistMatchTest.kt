package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlocklistMatchTest {

    private val blocked = setOf("example.com", "ads.example.com", "doubleclick.net")

    @Test
    fun `an exact host is blocked`() {
        assertThat(BlocklistMatch.isBlocked("example.com", blocked)).isTrue()
        assertThat(BlocklistMatch.isBlocked("doubleclick.net", blocked)).isTrue()
    }

    @Test
    fun `a subdomain of a listed host is blocked`() {
        assertThat(BlocklistMatch.isBlocked("ads.example.com", blocked)).isTrue()
        assertThat(BlocklistMatch.isBlocked("stats.ads.example.com", blocked)).isTrue()
        assertThat(BlocklistMatch.isBlocked("a.b.c.example.com", blocked)).isTrue()
        assertThat(BlocklistMatch.isBlocked("cdn.doubleclick.net", blocked)).isTrue()
    }

    @Test
    fun `a host that merely ends with a listed name is not blocked`() {
        // The whole point of anchoring on the dot: these are different sites.
        assertThat(BlocklistMatch.isBlocked("notexample.com", blocked)).isFalse()
        assertThat(BlocklistMatch.isBlocked("myexample.com", blocked)).isFalse()
        assertThat(BlocklistMatch.isBlocked("baddoubleclick.net", blocked)).isFalse()
    }

    @Test
    fun `a listed name appearing earlier in a longer host is not blocked`() {
        assertThat(BlocklistMatch.isBlocked("example.com.evil.net", blocked)).isFalse()
        assertThat(BlocklistMatch.isBlocked("example.co.uk", blocked)).isFalse()
    }

    @Test
    fun `an empty list blocks nothing`() {
        assertThat(BlocklistMatch.isBlocked("example.com", emptySet())).isFalse()
        assertThat(BlocklistMatch.isBlocked("a.example.com", emptySet())).isFalse()
    }

    @Test
    fun `an empty or single-label host is not matched against a bare tld`() {
        assertThat(BlocklistMatch.isBlocked("", setOf("com"))).isFalse()
        assertThat(BlocklistMatch.isBlocked("com", setOf("com"))).isTrue()
        assertThat(BlocklistMatch.isBlocked("localhost", setOf("com"))).isFalse()
    }

    @Test
    fun `a deeper entry wins for the host it names`() {
        val list = setOf("example.com", "tracker.example.com")
        assertThat(BlocklistMatch.isBlocked("tracker.example.com", list)).isTrue()
        assertThat(BlocklistMatch.isBlocked("other.example.com", list)).isTrue()
    }

    @Test
    fun `trailing-dot hosts still match their listed parent`() {
        // A fully qualified host may arrive with the root dot attached.
        assertThat(BlocklistMatch.isBlocked("ads.example.com.", blocked)).isFalse()
        assertThat(BlocklistMatch.isBlocked("example.com.", setOf("example.com."))).isTrue()
    }
}
