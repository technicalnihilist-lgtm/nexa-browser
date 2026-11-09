package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rule that stops the tab session being rewritten when nothing changed.
 * A skipped write is only ever safe if it cannot leave the stored session
 * different from the open one, so every way the two can differ is covered here.
 */
class SessionWriteTest {

    private fun tab(url: String, title: String, position: Int) = Triple(url, title, position)

    @Test
    fun `nothing written yet always writes`() {
        // An empty session must still be written: otherwise the tabs stored by
        // an earlier run would survive and be restored as if they were open.
        assertThat(SessionWrite.isRedundant(null, emptyList())).isFalse()
        assertThat(SessionWrite.isRedundant(null, listOf(tab("https://a.com", "A", 0)))).isFalse()
    }

    @Test
    fun `identical session is redundant`() {
        val session = listOf(tab("https://a.com", "A", 0), tab("https://b.com", "B", 1))
        assertThat(SessionWrite.isRedundant(session, session.toList())).isTrue()
    }

    @Test
    fun `both empty is redundant`() {
        assertThat(SessionWrite.isRedundant(emptyList(), emptyList())).isTrue()
    }

    @Test
    fun `new tab is not redundant`() {
        val before = listOf(tab("https://a.com", "A", 0))
        val after = before + tab("https://b.com", "B", 1)
        assertThat(SessionWrite.isRedundant(before, after)).isFalse()
    }

    @Test
    fun `closed tab is not redundant`() {
        val before = listOf(tab("https://a.com", "A", 0), tab("https://b.com", "B", 1))
        assertThat(SessionWrite.isRedundant(before, before.take(1))).isFalse()
    }

    @Test
    fun `changed title is not redundant`() {
        // A page that finishes loading in the background changes only its title,
        // and that is exactly the state a restore has to bring back.
        val before = listOf(tab("https://a.com", "Loading…", 0))
        val after = listOf(tab("https://a.com", "Example", 0))
        assertThat(SessionWrite.isRedundant(before, after)).isFalse()
    }

    @Test
    fun `changed url is not redundant`() {
        val before = listOf(tab("https://a.com", "A", 0))
        val after = listOf(tab("https://a.com/page", "A", 0))
        assertThat(SessionWrite.isRedundant(before, after)).isFalse()
    }

    @Test
    fun `reordered tabs are not redundant`() {
        val before = listOf(tab("https://a.com", "A", 0), tab("https://b.com", "B", 1))
        val after = listOf(tab("https://b.com", "B", 0), tab("https://a.com", "A", 1))
        assertThat(SessionWrite.isRedundant(before, after)).isFalse()
    }

    @Test
    fun `moved tab keeps its position so it is not redundant`() {
        val before = listOf(tab("https://a.com", "A", 0))
        val after = listOf(tab("https://a.com", "A", 3))
        assertThat(SessionWrite.isRedundant(before, after)).isFalse()
    }
}
