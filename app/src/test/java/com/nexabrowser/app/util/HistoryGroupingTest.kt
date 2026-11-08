package com.nexabrowser.app.util

import com.nexabrowser.app.domain.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HistoryGroupingTest {

    private fun at(daysAgo: Int, hour: Int, minute: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun entry(
        id: Long,
        title: String = "Page $id",
        url: String = "https://site$id.example/page",
        at: Long
    ) = HistoryEntry(id = id, title = title, url = url, faviconUrl = null, visitedAt = at)

    private val now = System.currentTimeMillis()

    @Test
    fun `blank query returns the same list without copying`() {
        val rows = listOf(entry(1, at = at(0, 9)))
        assertSame(rows, HistoryGrouping.filter(rows, ""))
        assertSame(rows, HistoryGrouping.filter(rows, "   "))
    }

    @Test
    fun `filter matches title or url, ignoring case`() {
        val rows = listOf(
            entry(1, title = "Kotlin Coroutines", url = "https://kotlinlang.org", at = at(0, 9)),
            entry(2, title = "Android", url = "https://developer.android.com", at = at(0, 8)),
            entry(3, title = "News", url = "https://news.example", at = at(1, 8))
        )
        assertEquals(listOf(1L), HistoryGrouping.filter(rows, "coroutines").map { it.id })
        // "Android" is matched in the title of one row and, as part of
        // developer.android.com, in the url of the same one.
        assertEquals(listOf(2L), HistoryGrouping.filter(rows, "ANDROID").map { it.id })
        assertEquals(listOf(3L), HistoryGrouping.filter(rows, "example").map { it.id })
        assertTrue(HistoryGrouping.filter(rows, "nothing here").isEmpty())
    }

    @Test
    fun `rows of one day share a section and keep their order`() {
        val rows = listOf(
            entry(1, at = at(0, 18)),
            entry(2, at = at(0, 12)),
            entry(3, at = at(0, 7)),
            entry(4, at = at(1, 20)),
            entry(5, at = at(3, 11))
        )
        val sections = HistoryGrouping.sections(rows, now)

        assertEquals(3, sections.size)
        assertEquals(listOf(1L, 2L, 3L), sections[0].entries.map { it.id })
        assertEquals(listOf(4L), sections[1].entries.map { it.id })
        assertEquals(listOf(5L), sections[2].entries.map { it.id })
        assertEquals("Today", sections[0].label)
        assertEquals("Yesterday", sections[1].label)
        // The label of the third section is whatever DateLabels draws for that
        // day; grouping must not invent its own wording.
        assertEquals(DateLabels.dayLabel(rows[4].visitedAt, now), sections[2].label)
    }

    @Test
    fun `labels match DateLabels for every row`() {
        val rows = (0 until 40).map { i -> entry(i.toLong(), at = at(i / 2, 9 + i % 12)) }
        val sections = HistoryGrouping.sections(rows, now)
        for (section in sections) {
            for (row in section.entries) {
                assertEquals(DateLabels.dayLabel(row.visitedAt, now), section.label)
            }
        }
    }

    @Test
    fun `a day that reappears later falls back to one section per day`() {
        // Not the order the query produces, but nothing may draw two headings
        // for the same day if it ever happens.
        val rows = listOf(
            entry(1, at = at(0, 9)),
            entry(2, at = at(1, 9)),
            entry(3, at = at(0, 10))
        )
        val sections = HistoryGrouping.sections(rows, now)

        assertEquals(2, sections.size)
        assertEquals("Today", sections[0].label)
        assertEquals(listOf(1L, 3L), sections[0].entries.map { it.id })
        assertEquals(listOf(2L), sections[1].entries.map { it.id })
    }

    @Test
    fun `build filters first and then groups`() {
        val rows = listOf(
            entry(1, title = "Kotlin", at = at(0, 9)),
            entry(2, title = "Android", at = at(0, 8)),
            entry(3, title = "Kotlin docs", at = at(2, 8))
        )
        val sections = HistoryGrouping.build(rows, "kotlin", now)

        assertEquals(2, sections.size)
        assertEquals(2, HistoryGrouping.count(sections))
        assertEquals(listOf(1L), sections[0].entries.map { it.id })
        assertEquals(listOf(3L), sections[1].entries.map { it.id })
    }

    @Test
    fun `empty input produces no sections`() {
        assertTrue(HistoryGrouping.sections(emptyList(), now).isEmpty())
        assertTrue(HistoryGrouping.build(emptyList(), "anything", now).isEmpty())
        assertEquals(0, HistoryGrouping.count(emptyList()))
    }
}
