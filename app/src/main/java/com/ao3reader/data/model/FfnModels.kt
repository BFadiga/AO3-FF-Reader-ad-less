package com.ao3reader.data.model

/** FanFiction.net's top-level media sections; [path] is the URL segment, e.g. /anime/. */
enum class FfnMedia(val label: String, val path: String) {
    ANIME("Anime/Manga", "anime"),
    BOOKS("Books", "book"),
    CARTOONS("Cartoons", "cartoon"),
    COMICS("Comics", "comic"),
    GAMES("Games", "game"),
    MISC("Misc", "misc"),
    MOVIES("Movies", "movie"),
    PLAYS("Plays/Musicals", "play"),
    TV("TV Shows", "tv"),
}

/** A FanFiction.net fandom ("category"), e.g. Dragon Ball Z at /anime/Dragon-Ball-Z/. */
data class FfnFandom(val name: String, val path: String, val count: String = "")

/** A choice in one of FanFiction.net's filter dropdowns: [value] is what goes in the URL. */
data class FfnOption(val value: String, val label: String)

/** FanFiction.net's genres. Ids are the values its filter form uses. */
object FfnGenres {
    val all = listOf(
        FfnOption("2", "Romance"), FfnOption("3", "Humor"), FfnOption("4", "Drama"), FfnOption("5", "Poetry"),
        FfnOption("6", "Adventure"), FfnOption("7", "Mystery"), FfnOption("8", "Horror"), FfnOption("9", "Parody"),
        FfnOption("10", "Angst"), FfnOption("11", "Supernatural"), FfnOption("12", "Suspense"),
        FfnOption("13", "Sci-Fi"), FfnOption("14", "Fantasy"), FfnOption("15", "Spiritual"),
        FfnOption("16", "Tragedy"), FfnOption("17", "Western"), FfnOption("18", "Crime"), FfnOption("19", "Family"),
        FfnOption("20", "Hurt/Comfort"), FfnOption("21", "Friendship"), FfnOption("1", "General"),
    )

    fun byName(name: String) = all.firstOrNull { it.label.equals(name, ignoreCase = true) }
}

enum class FfnRating(val label: String, val short: String, val value: String) {
    ALL("All ratings", "", "10"),
    K_T("K to T", "", "103"),
    K("K (all ages)", "K", "1"),
    K_PLUS("K+ (9+)", "K+", "2"),
    T("T (teens)", "T", "3"),
    M("M (mature)", "M", "4"),
}

enum class FfnSort(val label: String, val value: String) {
    UPDATED("Updated", "1"),
    PUBLISHED("Published", "2"),
    REVIEWS("Reviews", "3"),
    FAVORITES("Favorites", "4"),
    FOLLOWS("Follows", "5"),
}

enum class FfnStatus(val label: String, val value: String) {
    ANY("Any", "0"),
    IN_PROGRESS("In progress", "1"),
    COMPLETE("Complete", "2"),
}

/**
 * Filters for FanFiction.net. Browsing a fandom uses the site's own filter form; whatever it can't
 * express (more than one excluded genre, word counts, blocked tags) is applied to the results here.
 */
data class FfnFilter(
    val keywords: String = "",
    val fandom: FfnFandom? = null,
    val sort: FfnSort = FfnSort.UPDATED,
    val rating: FfnRating = FfnRating.ALL,
    val status: FfnStatus = FfnStatus.ANY,
    val languageId: String = "",
    val includeGenres: List<FfnOption> = emptyList(),
    val excludeGenres: List<FfnOption> = emptyList(),
    val includeCharacters: List<FfnOption> = emptyList(),
    val excludeCharacters: List<FfnOption> = emptyList(),
    val minWords: Int? = null,
    val maxWords: Int? = null,
)

/** Choices offered by a fandom page's filter form (characters and languages differ per fandom). */
data class FfnFilterOptions(
    val characters: List<FfnOption> = emptyList(),
    val languages: List<FfnOption> = emptyList(),
    val genres: List<FfnOption> = FfnGenres.all,
)

data class Review(val author: String, val meta: String, val text: String)

data class ReviewPage(val reviews: List<Review>, val page: Int, val totalPages: Int)

/** A Wattpad search: free text plus tags; tags to exclude and completion are applied in the app. */
data class WattpadFilter(
    val query: String = "",
    val includeTags: List<String> = emptyList(),
    val excludeTags: List<String> = emptyList(),
    val mature: Boolean = true,
    val completeOnly: Boolean = false,
    val sort: WattpadSort = WattpadSort.BEST_MATCH,
    /** Only stories updated in the last N days; null for any time. */
    val updatedWithinDays: Int? = null,
    val length: WattpadLength = WattpadLength.ANY,
) {
    val isEmpty: Boolean get() = query.isBlank() && includeTags.isEmpty()
}

enum class WattpadSort(val label: String) {
    BEST_MATCH("Best match"),
    MOST_VOTES("Most votes"),
    MOST_READS("Most reads"),
    /** Wattpad's own trending list for a tag. */
    HOT("Hot"),
}

/** Wattpad doesn't give word counts in search, so length goes by number of parts. */
enum class WattpadLength(val label: String, val parts: IntRange) {
    ANY("Any length", 0..Int.MAX_VALUE),
    SHORT("Under 10 parts", 0..9),
    MEDIUM("10–30 parts", 10..30),
    LONG("Over 30 parts", 31..Int.MAX_VALUE),
}
