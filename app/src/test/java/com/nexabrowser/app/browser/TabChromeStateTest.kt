package com.nexabrowser.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The chrome snapshot must be blind to page-load progress and to nothing else.
 *
 * This is what keeps a page load from recomposing the whole app tree: if a
 * progress tick changed the snapshot, every reader of the active tab would be
 * invalidated twenty times per load, which is exactly the cost this exists to
 * remove. If the snapshot dropped any other field, the address bar, the tab
 * strip or the error overlay would stop updating.
 */
class TabChromeStateTest {

    private fun tab(
        id: String = "tab-1",
        title: String = "Example",
        url: String = "https://example.com",
        progress: Int = 100,
        isLoading: Boolean = false,
        isPrivate: Boolean = false,
        canGoBack: Boolean = false,
        canGoForward: Boolean = false,
        error: PageError? = null
    ) = TabInfo(
        id = id,
        title = title,
        url = url,
        favicon = null,
        isLoading = isLoading,
        progress = progress,
        isPrivate = isPrivate,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        error = error
    )

    @Test
    fun `a progress tick does not change the chrome snapshot`() {
        val start = tab(progress = 5, isLoading = true)
        val middle = tab(progress = 65, isLoading = true)
        val end = tab(progress = 100, isLoading = false)

        assertThat(TabChromeState.of(start)).isEqualTo(TabChromeState.of(middle))
        // The end of a load also flips isLoading, which the chrome does show.
        assertThat(TabChromeState.of(middle)).isNotEqualTo(TabChromeState.of(end))
    }

    @Test
    fun `every other field still reaches the chrome`() {
        val base = tab()
        val snap = TabChromeState.of(base)!!

        assertThat(snap.id).isEqualTo(base.id)
        assertThat(snap.title).isEqualTo(base.title)
        assertThat(snap.url).isEqualTo(base.url)
        assertThat(snap.isPrivate).isEqualTo(base.isPrivate)
        assertThat(snap.showStartPage).isEqualTo(base.showStartPage)

        assertThat(TabChromeState.of(base.copy(title = "Other"))).isNotEqualTo(snap)
        assertThat(TabChromeState.of(base.copy(url = "https://other.test"))).isNotEqualTo(snap)
        assertThat(TabChromeState.of(base.copy(canGoBack = true))).isNotEqualTo(snap)
        assertThat(TabChromeState.of(base.copy(canGoForward = true))).isNotEqualTo(snap)
        assertThat(TabChromeState.of(base.copy(isPrivate = true))).isNotEqualTo(snap)
        assertThat(
            TabChromeState.of(base.copy(error = PageError(PageError.TIMEOUT, "timeout", base.url, true)))
        ).isNotEqualTo(snap)
    }

    @Test
    fun `a tab without progress is returned as it is`() {
        val idle = tab(progress = TabChromeState.IDLE_PROGRESS)
        assertThat(TabChromeState.of(idle)).isSameInstanceAs(idle)
        assertThat(TabChromeState.of(null as TabInfo?)).isNull()
    }

    @Test
    fun `a list snapshot drops progress and keeps order and size`() {
        val list = listOf(
            tab(id = "a", progress = 30, isLoading = true),
            tab(id = "b", progress = 0),
            tab(id = "c", progress = 90, isLoading = true)
        )

        val snap = TabChromeState.of(list)

        assertThat(snap.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(snap.map { it.progress }).containsExactly(0, 0, 0).inOrder()
        // Only the progress field was rewritten.
        assertThat(snap[0].title).isEqualTo(list[0].title)
        assertThat(snap[2].url).isEqualTo(list[2].url)
    }

    @Test
    fun `a list with no progress is not copied`() {
        val idle = listOf(tab(id = "a", progress = 0), tab(id = "b", progress = 0))
        assertThat(TabChromeState.of(idle)).isSameInstanceAs(idle)
        assertThat(TabChromeState.of(emptyList())).isEmpty()
    }
}
