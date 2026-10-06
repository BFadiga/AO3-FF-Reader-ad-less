package com.ao3reader.data.remote

import com.ao3reader.data.model.WorkFilter
import java.net.URLEncoder

object Ao3Urls {
    const val BASE = "https://archiveofourown.org"

    /**
     * AO3 escapes a few characters in tag URLs before percent-encoding them:
     * "/" -> "*s*", "&" -> "*a*", "." -> "*d*", "?" -> "*q*", "#" -> "*h*".
     */
    fun escapeTag(tag: String): String {
        val escaped = tag
            .replace("/", "*s*")
            .replace("&", "*a*")
            .replace(".", "*d*")
            .replace("?", "*q*")
            .replace("#", "*h*")
        return encodePath(escaped)
    }

    /** Reverses [escapeTag]'s AO3-specific substitutions (input must already be percent-decoded). */
    fun unescapeTag(segment: String): String = segment
        .replace("*s*", "/")
        .replace("*a*", "&")
        .replace("*d*", ".")
        .replace("*q*", "?")
        .replace("*h*", "#")

    private fun encodePath(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun q(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun work(id: Long) = "$BASE/works/$id"

    /** The whole work on one page, skipping the adult-content interstitial. */
    fun fullWork(id: Long) = "$BASE/works/$id?view_full_work=true&view_adult=true"

    fun chapterIndex(id: Long) = "$BASE/works/$id/navigate"

    /** The first chapter only; enough for the work's metadata. */
    fun workMeta(id: Long) = "$BASE/works/$id?view_adult=true"

    const val LOGIN = "$BASE/users/login"

    /**
     * An author's works. [authorPath] is the "/users/name/pseuds/pseud" link from a byline; without one,
     * a "pseud (username)" byline or a plain username is turned into the same path.
     */
    fun authorWorks(authorPath: String?, byline: String, page: Int): String {
        val path = authorPath?.takeIf { it.startsWith("/users/") } ?: run {
            val m = Regex("""^(.*) \((.*)\)$""").find(byline.trim())
            if (m != null) "/users/${encodePath(m.groupValues[2])}/pseuds/${encodePath(m.groupValues[1])}"
            else "/users/${encodePath(byline.trim())}"
        }
        return "$BASE$path/works?page=$page"
    }

    fun subscriptions(user: String, page: Int) = "$BASE/users/${encodePath(user)}/subscriptions?type=works&page=$page"

    fun mediaFandoms(mediaPath: String) = "$BASE/media/${encodePath(mediaPath)}/fandoms"

    fun tagAutocomplete(term: String) = "$BASE/autocomplete/tag?term=${q(term)}"

    fun fandomAutocomplete(term: String) = "$BASE/autocomplete/fandom?term=${q(term)}"

    /**
     * Works for a canonical tag, filtered with AO3's "Sort and Filter" form.
     * Supports exclusions, which free-text search does not.
     */
    fun tagWorks(tag: String, filter: WorkFilter, page: Int): String {
        val p = mutableListOf(
            "commit" to "Sort and Filter",
            "tag_id" to tag,
            "work_search[sort_column]" to filter.sort.ao3Value.let { if (it == "_score") "revised_at" else it },
        )
        if (filter.includeTags.isNotEmpty()) p += "work_search[other_tag_names]" to filter.includeTags.joinToString(",")
        if (filter.excludeTags.isNotEmpty()) p += "work_search[excluded_tag_names]" to filter.excludeTags.joinToString(",")
        filter.rating?.let { p += "work_search[rating_ids][]" to it.ao3Id.toString() }
        if (filter.completion.ao3Value.isNotEmpty()) p += "work_search[complete]" to filter.completion.ao3Value
        filter.crossovers?.let { p += "work_search[crossover]" to if (it) "T" else "F" }
        if (filter.language.isNotBlank()) p += "work_search[language_id]" to filter.language
        filter.minWords?.let { p += "work_search[words_from]" to it.toString() }
        filter.maxWords?.let { p += "work_search[words_to]" to it.toString() }
        if (filter.query.isNotBlank()) p += "work_search[query]" to filter.query
        p += "page" to page.toString()
        return "$BASE/works?" + p.joinToString("&") { (k, v) -> "${q(k)}=${q(v)}" }
    }

    /** Free-text work search. Exclusions are expressed in the query with AO3's "-" operator. */
    fun search(filter: WorkFilter, page: Int): String {
        val query = buildString {
            append(filter.query.trim())
            filter.excludeTags.forEach { append(" -\"").append(it.replace("\"", "")).append('"') }
        }.trim()
        val words = when {
            filter.minWords != null && filter.maxWords != null -> "${filter.minWords}-${filter.maxWords}"
            filter.minWords != null -> ">${filter.minWords}"
            filter.maxWords != null -> "<${filter.maxWords}"
            else -> ""
        }
        val p = listOf(
            "commit" to "Search",
            "work_search[query]" to query,
            "work_search[title]" to filter.title,
            "work_search[creators]" to filter.author,
            "work_search[freeform_names]" to filter.includeTags.joinToString(","),
            "work_search[rating_ids]" to (filter.rating?.ao3Id?.toString() ?: ""),
            "work_search[complete]" to filter.completion.ao3Value,
            "work_search[crossover]" to when (filter.crossovers) { true -> "T"; false -> "F"; null -> "" },
            "work_search[word_count]" to words,
            "work_search[language_id]" to filter.language,
            "work_search[sort_column]" to filter.sort.ao3Value,
            "work_search[sort_direction]" to "desc",
            "page" to page.toString(),
        )
        return "$BASE/works/search?" + p.joinToString("&") { (k, v) -> "${q(k)}=${q(v)}" }
    }

    /** Extracts the numeric work id from any AO3 work URL, e.g. ".../works/123/chapters/456". */
    fun workIdFrom(url: String): Long? =
        Regex("""/works/(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()

    fun chapterIdFrom(url: String): Long? =
        Regex("""/chapters/(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()
}
