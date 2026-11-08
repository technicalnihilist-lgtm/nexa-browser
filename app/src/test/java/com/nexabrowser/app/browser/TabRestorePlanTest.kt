package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Restore planning: strip order, unloadable urls, and how many tabs wait. */
class TabRestorePlanTest {

    private fun loadable(url: String) = url.startsWith("http")

    @Test
    fun `keeps the saved strip order`() {
        val saved = listOf(
            Triple("https://c.example", "C", 2),
            Triple("https://a.example", "A", 0),
            Triple("https://b.example", "B", 1)
        )

        val plan = TabRestorePlan.plan(saved, ::loadable)

        assertThat(plan.entries.map { it.url })
            .containsExactly("https://a.example", "https://b.example", "https://c.example").inOrder()
        assertThat(plan.entries.map { it.title }).containsExactly("A", "B", "C").inOrder()
    }

    @Test
    fun `drops urls that cannot be loaded`() {
        val saved = listOf(
            Triple("about:blank", "Blank", 0),
            Triple("https://a.example", "A", 1),
            Triple("", "Empty", 2)
        )

        val plan = TabRestorePlan.plan(saved, ::loadable)

        assertThat(plan.entries.map { it.url }).containsExactly("https://a.example")
    }

    @Test
    fun `only the first tab loads at restore`() {
        val saved = (0 until 5).map { Triple("https://site$it.example", "S$it", it) }

        val plan = TabRestorePlan.plan(saved, ::loadable)

        assertThat(plan.entries).hasSize(5)
        assertThat(plan.deferred).isEqualTo(4)
    }

    @Test
    fun `a single tab is not deferred`() {
        val plan = TabRestorePlan.plan(listOf(Triple("https://a.example", "A", 0)), ::loadable)

        assertThat(plan.entries).hasSize(1)
        assertThat(plan.deferred).isEqualTo(0)
    }

    @Test
    fun `nothing to restore plans nothing`() {
        val plan = TabRestorePlan.plan(listOf(Triple("about:blank", "Blank", 0)), ::loadable)

        assertThat(plan.entries).isEmpty()
        assertThat(plan.deferred).isEqualTo(0)
    }
}
