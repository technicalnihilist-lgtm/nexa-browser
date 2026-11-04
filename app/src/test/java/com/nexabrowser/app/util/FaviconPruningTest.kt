package com.nexabrowser.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The on-disk favicon cache is capped once per process. These tests pin the
 * eviction rule: the newest icons survive, the directory never shrinks below the
 * cap for no reason, and the oldest files are the ones that go.
 */
class FaviconPruningTest {

    @Test
    fun `a directory at or under the cap keeps every file`() {
        val entries = listOf("a.png" to 1L, "b.png" to 2L, "c.png" to 3L)
        assertThat(FaviconPruning.victims(entries, 3)).isEmpty()
        assertThat(FaviconPruning.victims(entries, 400)).isEmpty()
    }

    @Test
    fun `only the excess is removed`() {
        val entries = (1..10).map { "icon$it.png" to it.toLong() }
        val victims = FaviconPruning.victims(entries, 4)
        assertThat(victims).hasSize(6)
        assertThat(victims).containsExactly(
            "icon1.png", "icon2.png", "icon3.png", "icon4.png", "icon5.png", "icon6.png"
        )
    }

    @Test
    fun `the newest files are the ones kept`() {
        val entries = listOf(
            "newest.png" to 900L,
            "oldest.png" to 100L,
            "middle.png" to 500L
        )
        val victims = FaviconPruning.victims(entries, 1)
        assertThat(victims).containsExactly("oldest.png", "middle.png")
    }

    @Test
    fun `identical timestamps still evict a deterministic set`() {
        val entries = listOf("b.png" to 7L, "a.png" to 7L, "c.png" to 7L)
        val victims = FaviconPruning.victims(entries, 1)
        assertThat(victims).hasSize(2)
        // Sorted by name among equal timestamps, so the result cannot vary.
        assertThat(victims).containsExactly("a.png", "b.png")
    }

    @Test
    fun `an empty directory has nothing to evict`() {
        assertThat(FaviconPruning.victims(emptyList(), 400)).isEmpty()
    }

    @Test
    fun `an empty directory list survives a zero cap`() {
        assertThat(FaviconPruning.victims(emptyList(), 0)).isEmpty()
        assertThat(FaviconPruning.victims(listOf("a.png" to 1L), 0)).containsExactly("a.png")
    }
}
