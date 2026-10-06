package com.ao3reader.data.model

/** A work as it appears in a listing (search results, tag pages, library). */
data class WorkSummary(
    val id: Long,
    val title: String,
    val authors: List<String>,
    val fandoms: List<String> = emptyList(),
    val rating: String = "",
    val warnings: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val freeforms: List<String> = emptyList(),
    val series: List<String> = emptyList(),
    val summaryHtml: String = "",
    val language: String = "",
    val words: Int = 0,
    val chaptersPosted: Int = 1,
    val chaptersTotal: Int? = null,
    val kudos: Int = 0,
    val hits: Int = 0,
    val bookmarks: Int = 0,
    val comments: Int = 0,
    val updated: String = "",
    val complete: Boolean = false,
    /** Cover image (FanFiction.net only; AO3 works have no covers). */
    val coverUrl: String? = null,
    /**
     * Parallel to [authors]: where each author's page lives. AO3: the "/users/name/pseuds/pseud" path;
     * FanFiction.net: the numeric user id.
     */
    val authorIds: List<String> = emptyList(),
    /** FanFiction.net follower count (AO3 has none). */
    val follows: Int = 0,
) {
    /** Every tag on the work, including rating/warnings/categories (which AO3 also treats as tags). */
    fun allTags(): List<String> =
        listOfNotNull(rating.ifBlank { null }) + warnings + categories + fandoms + relationships + characters + freeforms

    val chaptersLabel: String get() = "$chaptersPosted/${chaptersTotal ?: "?"}"
}

data class WorkPage(
    val works: List<WorkSummary>,
    val page: Int,
    val totalPages: Int,
    val heading: String? = null,
)

data class ChapterRef(
    val index: Int,
    val id: Long?,
    val title: String,
    val date: String? = null,
)

data class Chapter(
    val index: Int,
    /** AO3 chapter id; null on FanFiction.net, where chapters are addressed by [index]. */
    val id: Long?,
    val title: String,
    /** Empty when the chapter hasn't been fetched yet. */
    val contentHtml: String,
    val summaryHtml: String? = null,
    val notesHtml: String? = null,
    val endNotesHtml: String? = null,
)

/** An HTML form scraped from a page, ready to be submitted as-is (kudos, subscribe, ...). */
data class FormData(val action: String, val fields: List<Pair<String, String>>)

/** What the signed-in (or guest) reader can do on a work page, and what they've already done. */
data class WorkActions(
    val kudosForm: FormData? = null,
    val subscribeForm: FormData? = null,
    val subscribed: Boolean = false,
    val kudosGiven: Boolean = false,
    /** Username shown in the page header when signed in. */
    val signedInAs: String? = null,
)

data class WorkDetail(
    val summary: WorkSummary,
    val published: String?,
    val notesHtml: String?,
    /** Every chapter; on FanFiction.net only the chapters already fetched have [Chapter.contentHtml]. */
    val chapters: List<Chapter>,
    val actions: WorkActions = WorkActions(),
)

data class FandomEntry(val name: String, val count: Int)

/** Media categories AO3 groups fandoms under. [path] is the segment used in /media/{path}/fandoms. */
enum class Ao3Media(val label: String, val path: String) {
    ANIME("Anime & Manga", "Anime *a* Manga"),
    BOOKS("Books & Literature", "Books *a* Literature"),
    CARTOONS("Cartoons, Comics & Graphic Novels", "Cartoons *a* Comics *a* Graphic Novels"),
    CELEBRITIES("Celebrities & Real People", "Celebrities *a* Real People"),
    MOVIES("Movies", "Movies"),
    MUSIC("Music & Bands", "Music *a* Bands"),
    OTHER("Other Media", "Other Media"),
    THEATER("Theater", "Theater"),
    TV("TV Shows", "TV Shows"),
    GAMES("Video Games", "Video Games"),
    UNCATEGORIZED("Uncategorized Fandoms", "Uncategorized Fandoms"),
}

enum class Rating(val label: String, val ao3Id: Int) {
    GENERAL("General Audiences", 10),
    TEEN("Teen And Up Audiences", 11),
    MATURE("Mature", 12),
    EXPLICIT("Explicit", 13),
    NOT_RATED("Not Rated", 9),
}

enum class SortColumn(val label: String, val ao3Value: String) {
    BEST_MATCH("Best match", "_score"),
    UPDATED("Date updated", "revised_at"),
    POSTED("Date posted", "created_at"),
    KUDOS("Kudos", "kudos_count"),
    HITS("Hits", "hits"),
    WORDS("Word count", "word_count"),
    BOOKMARKS("Bookmarks", "bookmarks_count"),
    COMMENTS("Comments", "comments_count"),
    TITLE("Title", "title_to_sort_on"),
    AUTHOR("Author", "authors_to_sort_on"),
}

enum class CompletionFilter(val label: String, val ao3Value: String) {
    ANY("Any", ""),
    COMPLETE("Complete only", "T"),
    IN_PROGRESS("In progress only", "F"),
}

/** Filters shared by the search screen and tag listings. */
data class WorkFilter(
    val query: String = "",
    val title: String = "",
    val author: String = "",
    val includeTags: List<String> = emptyList(),
    val excludeTags: List<String> = emptyList(),
    val rating: Rating? = null,
    val completion: CompletionFilter = CompletionFilter.ANY,
    val sort: SortColumn = SortColumn.UPDATED,
    val language: String = "",
    val minWords: Int? = null,
    val maxWords: Int? = null,
    val crossovers: Boolean? = null,
)
