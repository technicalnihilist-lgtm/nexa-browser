package com.nexabrowser.app.data.local.dao

/**
 * Aggregate projection for the "frequently visited" list on the start page.
 *
 * The title is deliberately *not* part of the aggregate any more. Asking the
 * query for `MAX(title)` forced SQLite to read the whole `history` table, row by
 * row, because `title` is not part of any index: on a 20k-visit history the
 * start-page query cost ~74 ms and ran again on every history write. Without it
 * the same `GROUP BY url` is answered from the covering index
 * `(url, visitedAt)` alone and costs ~24 ms; the twelve titles the start page
 * actually shows are then fetched with twelve indexed lookups.
 */
data class TopSiteAggregateRow(
    val url: String,
    val visits: Int,
    val lastVisit: Long
)

/** One row of the batched title lookup: a url and the title of its newest visit. */
data class UrlTitleRow(
    val url: String,
    val title: String
)

/**
 * Pairs an aggregate row with the title of that URL's newest visit.
 *
 * Kept free of Room and Android types so the pairing is unit tested rather than
 * inferred from a database log. A URL whose title could not be read keeps an
 * empty title, which the start page already renders as the URL itself.
 */
object TopSitesMerge {

    /**
     * url -> title from the rows of one batched lookup.
     *
     * The query can return more than one row for a url when two visits share a
     * timestamp; the first row wins, so the map has exactly one title per url and
     * never depends on row order beyond that tie.
     */
    fun titlesOf(rows: List<UrlTitleRow>): Map<String, String> {
        if (rows.isEmpty()) return emptyMap()
        val titles = HashMap<String, String>(rows.size * 2)
        for (row in rows) {
            if (!titles.containsKey(row.url)) titles[row.url] = row.title
        }
        return titles
    }

    fun merge(
        aggregates: List<TopSiteAggregateRow>,
        titles: Map<String, String?>
    ): List<TopSiteAggregateRowWithTitle> = aggregates.map { row ->
        TopSiteAggregateRowWithTitle(
            url = row.url,
            title = titles[row.url].orEmpty(),
            visits = row.visits,
            lastVisit = row.lastVisit
        )
    }
}

/** One start-page row after the title has been attached. */
data class TopSiteAggregateRowWithTitle(
    val url: String,
    val title: String,
    val visits: Int,
    val lastVisit: Long
)
