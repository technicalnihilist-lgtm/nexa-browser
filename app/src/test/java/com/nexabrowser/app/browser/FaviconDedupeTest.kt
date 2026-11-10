package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaviconDedupeTest {

    @Test
    fun `the same bitmap at the same size is a repeat`() {
        val first = FaviconDedupe.signature(identityHashCode = 42, width = 32, height = 32)
        val second = FaviconDedupe.signature(identityHashCode = 42, width = 32, height = 32)

        assertThat(FaviconDedupe.isRepeat(first, second)).isTrue()
    }

    @Test
    fun `a different bitmap instance is not a repeat`() {
        val first = FaviconDedupe.signature(identityHashCode = 42, width = 32, height = 32)
        val second = FaviconDedupe.signature(identityHashCode = 43, width = 32, height = 32)

        assertThat(FaviconDedupe.isRepeat(first, second)).isFalse()
    }

    @Test
    fun `a recycled bitmap refilled at another size is not a repeat`() {
        val first = FaviconDedupe.signature(identityHashCode = 7, width = 32, height = 32)
        val second = FaviconDedupe.signature(identityHashCode = 7, width = 512, height = 512)

        assertThat(FaviconDedupe.isRepeat(first, second)).isFalse()
    }

    @Test
    fun `nothing is a repeat of the first icon of a tab`() {
        val signature = FaviconDedupe.signature(identityHashCode = 1, width = 16, height = 16)

        assertThat(FaviconDedupe.isRepeat(null, signature)).isFalse()
    }

    @Test
    fun `width and height are not interchangeable`() {
        val landscape = FaviconDedupe.signature(identityHashCode = 9, width = 48, height = 16)
        val portrait = FaviconDedupe.signature(identityHashCode = 9, width = 16, height = 48)

        assertThat(FaviconDedupe.isRepeat(landscape, portrait)).isFalse()
    }

    @Test
    fun `the identity hash is never lost to the packed dimensions`() {
        val a = FaviconDedupe.signature(identityHashCode = 0x7FFF_FFFF, width = 0xFFFF, height = 0xFFFF)
        val b = FaviconDedupe.signature(identityHashCode = 0x7FFF_FFFE, width = 0xFFFF, height = 0xFFFF)

        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `an oversized edge is clamped instead of overflowing into the hash`() {
        val clamped = FaviconDedupe.signature(identityHashCode = 5, width = 70000, height = 70000)
        val max = FaviconDedupe.signature(identityHashCode = 5, width = 0xFFFF, height = 0xFFFF)

        assertThat(clamped).isEqualTo(max)
    }
}
