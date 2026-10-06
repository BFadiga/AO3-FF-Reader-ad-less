package com.ao3reader.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ao3reader.data.model.Site
import com.ao3reader.data.model.WorkIds
import com.ao3reader.data.model.WorkSummary

/** A work the user follows and/or has downloaded, plus their reading position. */
@Entity(tableName = "library")
data class LibraryWork(
    @PrimaryKey val id: Long,
    val title: String,
    val authors: List<String>,
    val fandoms: List<String>,
    val rating: String,
    val warnings: List<String>,
    val categories: List<String>,
    val relationships: List<String>,
    val characters: List<String>,
    val freeforms: List<String>,
    val summaryHtml: String,
    val language: String,
    val words: Int,
    val chaptersPosted: Int,
    val chaptersTotal: Int?,
    val kudos: Int,
    val hits: Int,
    val updated: String,
    val complete: Boolean,
    val followed: Boolean,
    val addedAt: Long,
    /** 1-based chapter last opened in the reader; 0 if never opened. */
    val lastReadChapter: Int = 0,
    /** Scroll position within [lastReadChapter], 0..1. */
    val lastReadProgress: Float = 0f,
    /** Chapters posted since the user last opened the work. */
    val newChapters: Int = 0,
    /** Number of chapters saved for offline reading; 0 if not downloaded. */
    val downloadedChapters: Int = 0,
    val downloadedAt: Long? = null,
    val lastCheckedAt: Long = 0,
    /** FanFiction.net cover; AO3 works have none. */
    val coverUrl: String? = null,
    val authorIds: List<String> = emptyList(),
    /** Kudos left (AO3) or favorited (FanFiction.net). */
    val liked: Boolean = false,
    /** Pinned to the top of the library, above the series groups. */
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
) {
    fun toSummary() = WorkSummary(
        id = id, title = title, authors = authors, fandoms = fandoms, rating = rating,
        warnings = warnings, categories = categories, relationships = relationships,
        characters = characters, freeforms = freeforms, summaryHtml = summaryHtml,
        language = language, words = words, chaptersPosted = chaptersPosted,
        chaptersTotal = chaptersTotal, kudos = kudos, hits = hits, updated = updated, complete = complete,
        coverUrl = coverUrl, authorIds = authorIds,
    )

    val site: Site get() = WorkIds.site(id)

    /** Copies fresh metadata from AO3 while keeping the user's own state. */
    fun withMetadata(w: WorkSummary) = copy(
        coverUrl = w.coverUrl ?: coverUrl, authorIds = w.authorIds.ifEmpty { authorIds },
        title = w.title.ifBlank { title }, authors = w.authors.ifEmpty { authors }, fandoms = w.fandoms, rating = w.rating, warnings = w.warnings,
        categories = w.categories, relationships = w.relationships, characters = w.characters,
        freeforms = w.freeforms, summaryHtml = w.summaryHtml.ifBlank { summaryHtml }, language = w.language,
        words = w.words, chaptersPosted = w.chaptersPosted, chaptersTotal = w.chaptersTotal, kudos = w.kudos,
        hits = w.hits, updated = w.updated.ifBlank { updated }, complete = w.complete,
    )

    val isDownloaded get() = downloadedChapters > 0

    companion object {
        fun from(w: WorkSummary, followed: Boolean) = LibraryWork(
            id = w.id, title = w.title, authors = w.authors, fandoms = w.fandoms, rating = w.rating,
            warnings = w.warnings, categories = w.categories, relationships = w.relationships,
            characters = w.characters, freeforms = w.freeforms, summaryHtml = w.summaryHtml,
            language = w.language, words = w.words, chaptersPosted = w.chaptersPosted,
            chaptersTotal = w.chaptersTotal, kudos = w.kudos, hits = w.hits, updated = w.updated,
            complete = w.complete, followed = followed, addedAt = System.currentTimeMillis(),
            coverUrl = w.coverUrl, authorIds = w.authorIds,
        )
    }
}

/**
 * A tag pinned on the Categories tab, per site. [section] is the heading it's shown under; [target] is
 * what opening it browses: the tag itself on AO3, a fandom path (e.g. "/anime/Dragon-Ball-Z/") or
 * "genre:{id}" on FanFiction.net.
 */
@Entity(tableName = "favorite_tags", primaryKeys = ["site", "name"])
data class FavoriteTag(
    val site: Site,
    val name: String,
    val section: String,
    val target: String = name,
    val addedAt: Long = System.currentTimeMillis(),
)

/** New chapters found on a followed work: one row per check that found some, shown on the Updates tab. */
@Entity(tableName = "updates", indices = [Index(value = ["workId"])])
data class UpdateEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workId: Long,
    val title: String,
    val authors: List<String>,
    val coverUrl: String? = null,
    val rating: String = "",
    val chaptersAdded: Int,
    /** First of the new chapters, so tapping the update opens it. */
    val firstNewChapter: Int,
    val latestChapterTitle: String = "",
    val detectedAt: Long = System.currentTimeMillis(),
    val seen: Boolean = false,
)

enum class BlockKind { TAG, AUTHOR }

@Entity(tableName = "blocked", indices = [Index(value = ["site", "kind", "value"], unique = true)])
data class Blocked(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: BlockKind,
    val value: String,
    val site: Site = Site.AO3,
)
