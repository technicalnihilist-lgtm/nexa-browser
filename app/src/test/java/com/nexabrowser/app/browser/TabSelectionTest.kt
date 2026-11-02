package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the tab-selection rule used when a tab closes: the tab that takes over
 * and the browsing mode must always agree, otherwise the UI ends up in private
 * mode with a regular tab on screen (or the reverse).
 */
class TabSelectionTest {

    private fun tabs(vararg specs: Pair<String, Boolean>) = specs.map { TabRef(it.first, it.second) }

    @Test
    fun `closing the last tab of a mode falls back to the other mode`() {
        val remaining = tabs("a" to false, "b" to false)
        val next = TabSelection.activeAfterClose(remaining, closedIsPrivate = true)
        assertThat(next).isEqualTo(TabRef("b", false))
    }

    @Test
    fun `closing a private tab keeps private mode while private tabs remain`() {
        val remaining = tabs("r1" to false, "p1" to true, "r2" to false)
        val next = TabSelection.activeAfterClose(remaining, closedIsPrivate = true)
        assertThat(next).isEqualTo(TabRef("p1", true))
        assertThat(next?.isPrivate).isTrue()
    }

    @Test
    fun `closing the last regular tab falls back to a private tab and private mode`() {
        val remaining = tabs("p1" to true, "p2" to true)
        val next = TabSelection.activeAfterClose(remaining, closedIsPrivate = false)
        assertThat(next).isEqualTo(TabRef("p2", true))
        assertThat(next?.isPrivate).isTrue()
    }

    @Test
    fun `the newest tab of the same mode wins`() {
        val remaining = tabs("r1" to false, "r2" to false, "r3" to false)
        assertThat(TabSelection.activeAfterClose(remaining, closedIsPrivate = false))
            .isEqualTo(TabRef("r3", false))
    }

    @Test
    fun `no tabs left asks the caller for a new tab`() {
        assertThat(TabSelection.activeAfterClose(emptyList(), closedIsPrivate = false)).isNull()
        assertThat(TabSelection.activeAfterClose(emptyList(), closedIsPrivate = true)).isNull()
    }
}
