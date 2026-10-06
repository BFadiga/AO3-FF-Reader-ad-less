package com.ao3reader.data.model

/** The archives the app can read from. */
enum class Site(val label: String, val shortLabel: String, val baseUrl: String) {
    AO3("Archive of Our Own", "AO3", "https://archiveofourown.org"),
    FFN("FanFiction.net", "FF.net", "https://www.fanfiction.net"),
}

/**
 * Works from both sites share one id space so the library, routes, downloads and notifications
 * can keep using a single Long: AO3 work ids are stored as-is, FanFiction.net story ids negated.
 */
object WorkIds {
    fun ffn(storyId: Long): Long = -storyId
    fun site(id: Long): Site = if (id < 0) Site.FFN else Site.AO3

    /** The id the site itself uses. */
    fun remote(id: Long): Long = if (id < 0) -id else id
}

val WorkSummary.site: Site get() = WorkIds.site(id)
