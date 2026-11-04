package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The tab manager answers "which tab is this?" from [TabLookup] for every
 * WebView callback, so the table must never disagree with the tab list: a stale
 * entry would deliver a progress or title event to a tab the user already
 * closed.
 */
class TabLookupTest {

    private data class Item(val id: String, val label: String = "")

    private fun lookup(vararg items: Item) = TabLookup<Item>().apply {
        setAll(items.toList()) { it.id }
    }

    @Test
    fun `finds every item by id`() {
        val lookup = lookup(Item("a"), Item("b"), Item("c"))

        assertThat(lookup["a"]?.id).isEqualTo("a")
        assertThat(lookup["b"]?.id).isEqualTo("b")
        assertThat(lookup["c"]?.id).isEqualTo("c")
        assertThat(lookup.size).isEqualTo(3)
    }

    @Test
    fun `unknown and null keys resolve to null`() {
        val lookup = lookup(Item("a"))

        assertThat(lookup["missing"]).isNull()
        assertThat(lookup[null]).isNull()
        assertThat(lookup.contains("missing")).isFalse()
        assertThat(lookup.contains(null)).isFalse()
    }

    @Test
    fun `setAll drops entries for items that are gone`() {
        val lookup = lookup(Item("a"), Item("b"))
        lookup.setAll(listOf(Item("b"), Item("c"))) { it.id }

        assertThat(lookup["a"]).isNull()
        assertThat(lookup["b"]?.id).isEqualTo("b")
        assertThat(lookup["c"]?.id).isEqualTo("c")
        assertThat(lookup.size).isEqualTo(2)
    }

    @Test
    fun `setAll with an empty list empties the table`() {
        val lookup = lookup(Item("a"), Item("b"))
        lookup.setAll(emptyList()) { it.id }

        assertThat(lookup.size).isEqualTo(0)
        assertThat(lookup["a"]).isNull()
    }

    @Test
    fun `put replaces an entry with the same id`() {
        val lookup = lookup(Item("a", "first"))
        lookup.put("a", Item("a", "second"))

        assertThat(lookup.size).isEqualTo(1)
        assertThat(lookup["a"]?.label).isEqualTo("second")
    }

    @Test
    fun `remove forgets one id and leaves the rest`() {
        val lookup = lookup(Item("a"), Item("b"))
        lookup.remove("a")
        lookup.remove(null)

        assertThat(lookup["a"]).isNull()
        assertThat(lookup["b"]?.id).isEqualTo("b")
        assertThat(lookup.size).isEqualTo(1)
    }

    @Test
    fun `clear empties the table`() {
        val lookup = lookup(Item("a"), Item("b"))
        lookup.clear()

        assertThat(lookup.size).isEqualTo(0)
        assertThat(lookup["b"]).isNull()
    }
}
