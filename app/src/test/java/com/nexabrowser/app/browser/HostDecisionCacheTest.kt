package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The ad blocker's memo: it has to stay bounded, keep answering for the hosts a
 * page asks about repeatedly, and never let a stale answer win.
 */
class HostDecisionCacheTest {

    @Test
    fun `an unknown host has no answer`() {
        val cache = HostDecisionCache(generationSize = 4)
        assertThat(cache.get("example.com")).isNull()
    }

    @Test
    fun `an answer is remembered`() {
        val cache = HostDecisionCache(generationSize = 4)
        cache.put("ads.example.com", true)
        cache.put("cdn.example.com", false)
        assertThat(cache.get("ads.example.com")).isTrue()
        assertThat(cache.get("cdn.example.com")).isFalse()
    }

    @Test
    fun `a host asked again after a rollover is still answered`() {
        // The point of the second generation: a page's own CDN host, remembered
        // before the table filled up with one-shot tracker subdomains, must not
        // have to walk the blocklist again on the next navigation.
        val cache = HostDecisionCache(generationSize = 4)
        cache.put("hot.example.com", true)
        // Fill and roll the young generation.
        for (i in 0 until 4) cache.put("t$i.tracker.test", true)
        cache.put("one.more.test", true)

        assertThat(cache.get("hot.example.com")).isTrue()
    }

    @Test
    fun `a generation that has rolled twice is dropped`() {
        // Bounded means the oldest answers do go: two generations are kept, so
        // a host from three generations ago is forgotten rather than growing the
        // table without limit.
        val cache = HostDecisionCache(generationSize = 2)
        cache.put("ancient.test", true)
        repeat(2) { generation ->
            repeat(2) { i -> cache.put("g$generation-$i.test", true) }
        }
        assertThat(cache.get("ancient.test")).isNull()
    }

    @Test
    fun `the table never holds more than two generations`() {
        val generationSize = 8
        val cache = HostDecisionCache(generationSize)
        repeat(200) { i -> cache.put("host$i.test", i % 2 == 0) }
        assertThat(cache.size).isAtMost(generationSize * 2)
    }

    @Test
    fun `a newer answer wins over the older generation`() {
        val cache = HostDecisionCache(generationSize = 2)
        cache.put("flip.test", true)
        for (i in 0 until 2) cache.put("t$i.test", true)
        // Rolls the generation holding flip.test into `old`.
        cache.put("roll.test", true)
        cache.put("flip.test", false)

        assertThat(cache.get("flip.test")).isFalse()
    }

    @Test
    fun `clear drops both generations`() {
        val cache = HostDecisionCache(generationSize = 2)
        cache.put("a.test", true)
        for (i in 0 until 2) cache.put("t$i.test", true)
        cache.put("b.test", true)
        assertThat(cache.size).isGreaterThan(0)

        cache.clear()

        assertThat(cache.size).isEqualTo(0)
        assertThat(cache.get("a.test")).isNull()
        assertThat(cache.get("b.test")).isNull()
    }
}
