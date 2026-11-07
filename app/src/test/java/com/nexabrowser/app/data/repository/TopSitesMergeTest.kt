package com.nexabrowser.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.nexabrowser.app.data.local.dao.TopSiteAggregateRow
import com.nexabrowser.app.data.local.dao.TopSitesMerge
import com.nexabrowser.app.data.local.dao.UrlTitleRow
import org.junit.Test

/**
 * The start page's "frequently visited" rows are assembled from two reads now:
 * an aggregate counted over the covering `(url, visitedAt)` index, and one title
 * lookup per row. These tests pin the pairing — a row must keep its own title,
 * its own counts and its position in the ranking.
 */
class TopSitesMergeTest {

    private val a = TopSiteAggregateRow(url = "https://a.example", visits = 9, lastVisit = 300L)
    private val b = TopSiteAggregateRow(url = "https://b.example", visits = 4, lastVisit = 200L)

    @Test
    fun `titles are attached by url`() {
        val merged = TopSitesMerge.merge(
            listOf(a, b),
            mapOf("https://a.example" to "A site", "https://b.example" to "B site")
        )

        assertThat(merged.map { it.url })
            .containsExactly("https://a.example", "https://b.example").inOrder()
        assertThat(merged.map { it.title }).containsExactly("A site", "B site").inOrder()
        assertThat(merged.map { it.visits }).containsExactly(9, 4).inOrder()
        assertThat(merged.map { it.lastVisit }).containsExactly(300L, 200L).inOrder()
    }

    @Test
    fun `a missing title becomes an empty string, never a null or a neighbour's title`() {
        val merged = TopSitesMerge.merge(listOf(a, b), mapOf("https://b.example" to "B site"))

        assertThat(merged[0].title).isEmpty()
        assertThat(merged[1].title).isEqualTo("B site")
    }

    @Test
    fun `a null title from the database becomes an empty string`() {
        val merged = TopSitesMerge.merge(listOf(a), mapOf("https://a.example" to null))

        assertThat(merged.single().title).isEmpty()
    }

    @Test
    fun `an empty aggregate stays empty`() {
        assertThat(TopSitesMerge.merge(emptyList(), emptyMap())).isEmpty()
    }

    @Test
    fun `one batched lookup gives each url its own title`() {
        val titles = TopSitesMerge.titlesOf(
            listOf(
                UrlTitleRow("https://a.example", "A site"),
                UrlTitleRow("https://b.example", "B site")
            )
        )

        assertThat(titles).hasSize(2)
        assertThat(titles["https://a.example"]).isEqualTo("A site")
        assertThat(titles["https://b.example"]).isEqualTo("B site")
    }

    @Test
    fun `a url with two visits at the same timestamp keeps the first row`() {
        // The batched query joins on the newest visitedAt, so an exact tie in the
        // table returns both rows. The map must still hold exactly one title per
        // url, and the merged list must keep its ranking.
        val titles = TopSitesMerge.titlesOf(
            listOf(
                UrlTitleRow("https://a.example", "First"),
                UrlTitleRow("https://a.example", "Second"),
                UrlTitleRow("https://b.example", "B site")
            )
        )

        assertThat(titles).hasSize(2)
        assertThat(titles["https://a.example"]).isEqualTo("First")

        val merged = TopSitesMerge.merge(listOf(a, b), titles)
        assertThat(merged.map { it.title }).containsExactly("First", "B site").inOrder()
    }

    @Test
    fun `a url missing from the lookup falls back to its url on screen`() {
        val merged = TopSitesMerge.merge(listOf(a, b), TopSitesMerge.titlesOf(emptyList()))

        assertThat(merged.map { it.title }).containsExactly("", "").inOrder()
    }
}
