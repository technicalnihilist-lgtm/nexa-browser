package com.nexabrowser.app.data.local.dao

/** Projection for the "frequently visited" list on the start page. */
data class TopSiteRow(
    val url: String,
    val title: String,
    val visits: Int,
    val lastVisit: Long
)
