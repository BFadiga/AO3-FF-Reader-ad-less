package com.ao3reader.data.model

/** The archives the app can read from. */
enum class Site(val label: String, val shortLabel: String, val baseUrl: String) {
    AO3("Archive of Our Own", "AO3", "https://archiveofourown.org"),
    FFN("FanFiction.net", "FF.net", "https://www.fanfiction.net"),
    WATTPAD("Wattpad", "Wattpad", "https://www.wattpad.com"),
}

/**
 * Works from both sites share one id space so the library, routes, downloads and notifications
 * can keep using a single Long: AO3 work ids are stored as-is, FanFiction.net story ids negated,
 * Wattpad story ids shifted above [WATTPAD_OFFSET] (far beyond any AO3 id).
 */
object WorkIds {
    const val WATTPAD_OFFSET = 1_000_000_000_000L

    fun ffn(storyId: Long): Long = -storyId
    fun wattpad(storyId: Long): Long = WATTPAD_OFFSET + storyId
    fun site(id: Long): Site = when {
        id < 0 -> Site.FFN
        id >= WATTPAD_OFFSET -> Site.WATTPAD
        else -> Site.AO3
    }

    /** The id the site itself uses. */
    fun remote(id: Long): Long = when {
        id < 0 -> -id
        id >= WATTPAD_OFFSET -> id - WATTPAD_OFFSET
        else -> id
    }
}

val WorkSummary.site: Site get() = WorkIds.site(id)
