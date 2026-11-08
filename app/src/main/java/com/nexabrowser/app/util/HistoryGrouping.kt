package com.nexabrowser.app.util

import com.nexabrowser.app.domain.model.HistoryEntry
import java.util.Calendar

/** One day's rows of the history list, in the order the user visited them. */
data class HistorySection(val label: String, val entries: List<HistoryEntry>)

/**
 * Turns a history list into the day sections the history screen draws.
 *
 * The screen used to do this inline, in composition, on the main thread:
 *
 * ```
 * val filtered = history.filter { it.title.contains(q, true) || it.url.contains(q, true) }
 * val grouped  = filtered.groupBy { DateLabels.dayLabel(it.visitedAt, now) }
 * ```
 *
 * `groupBy` evaluates its key lambda for *every* row, so a 20,000-row history
 * ran `DateLabels.dayLabel` 20,000 times on the frame thread — each call setting
 * a Calendar twice and reading four fields — and then allocated one ArrayList
 * per day plus a LinkedHashMap for the result. That ran again on every keystroke
 * in the search box, which is the one moment the list has to keep up with the
 * user, and again every time a page load appended a row.
 *
 * The list is sorted newest first, so rows of the same day are adjacent and a
 * single pass is enough: the day is worked out once per row with the cheap
 * calendar comparison, and the label is only *formatted* when the day actually
 * changes. A five-thousand-row history across a year of browsing therefore does
 * ~30 format calls instead of ~5,000, and the section lists are built directly
 * with no map and no per-day temporary lists.
 *
 * If the input is not sorted by day — anything that does not come from the
 * `ORDER BY visitedAt DESC` query — a day can appear twice, which would draw two
 * headings for it. The pass notices that and falls back to the exact
 * `groupBy` semantics, so the result never differs from what the screen drew
 * before, whatever order it is handed.
 */
object HistoryGrouping {

    /** Rows matching [query] in title or url, case-insensitively; all rows if blank. */
    fun filter(history: List<HistoryEntry>, query: String): List<HistoryEntry> {
        if (query.isBlank()) return history
        val needle = query
        var any = false
        for (entry in history) {
            if (matches(entry, needle)) {
                any = true
                break
            }
        }
        // No match at all: an empty list is the honest answer and costs no copy.
        if (!any) return emptyList()
        val out = ArrayList<HistoryEntry>(history.size)
        for (entry in history) if (matches(entry, needle)) out.add(entry)
        return out
    }

    private fun matches(entry: HistoryEntry, query: String): Boolean =
        entry.title.contains(query, ignoreCase = true) || entry.url.contains(query, ignoreCase = true)

    /** Groups rows that are already in day order into one section per day. */
    fun sections(entries: List<HistoryEntry>, now: Long = System.currentTimeMillis()): List<HistorySection> {
        if (entries.isEmpty()) return emptyList()
        val cal = calendar.get()
        val out = ArrayList<HistorySection>(4)
        // Day keys already closed, so a repeated day can be detected for free.
        val seen = HashSet<Long>(8)
        var currentKey = Long.MIN_VALUE
        var currentLabel: String? = null
        var current = ArrayList<HistoryEntry>()
        for (entry in entries) {
            val key = dayKey(cal, entry.visitedAt)
            if (key != currentKey) {
                if (!seen.add(key)) {
                    // The same day came back after another one: the list is not
                    // in day order, so fall back to grouping by label.
                    return groupByLabel(entries, now)
                }
                if (currentLabel != null) out.add(HistorySection(currentLabel, current))
                currentKey = key
                currentLabel = DateLabels.dayLabel(entry.visitedAt, now)
                current = ArrayList()
            }
            current.add(entry)
        }
        currentLabel?.let { out.add(HistorySection(it, current)) }
        return out
    }

    /** Filter and group in one call, which is what the screen needs. */
    fun build(
        history: List<HistoryEntry>,
        query: String,
        now: Long = System.currentTimeMillis()
    ): List<HistorySection> = sections(filter(history, query), now)

    /** Number of rows across [sections], without rebuilding a flat list. */
    fun count(sections: List<HistorySection>): Int {
        var total = 0
        for (section in sections) total += section.entries.size
        return total
    }

    /**
     * The old behaviour, kept as the fallback: label every row and group by it,
     * preserving first-seen order.
     */
    private fun groupByLabel(entries: List<HistoryEntry>, now: Long): List<HistorySection> {
        val byLabel = LinkedHashMap<String, ArrayList<HistoryEntry>>()
        for (entry in entries) {
            val label = DateLabels.dayLabel(entry.visitedAt, now)
            byLabel.getOrPut(label) { ArrayList() }.add(entry)
        }
        return byLabel.map { (label, rows) -> HistorySection(label, rows) }
    }

    /** Year * 1000 + day-of-year: unique per calendar day, cheap to compare. */
    private fun dayKey(cal: Calendar, timestamp: Long): Long {
        cal.timeInMillis = timestamp
        return cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
    }

    // One Calendar per thread: reused in place, so a day key allocates nothing.
    private val calendar: ThreadLocal<Calendar> =
        object : ThreadLocal<Calendar>() {
            override fun initialValue(): Calendar = Calendar.getInstance()
        }
}
