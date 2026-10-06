package com.ao3reader.data.repo

import com.ao3reader.data.local.AppDatabase
import com.ao3reader.data.local.BlockKind
import com.ao3reader.data.local.Blocked
import com.ao3reader.data.local.FavoriteTag
import com.ao3reader.data.model.Site
import com.ao3reader.data.model.WorkSummary
import com.ao3reader.data.model.site
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Blocked tags/authors as lowercase sets, for fast matching against listings. */
data class BlockList(val tags: Set<String> = emptySet(), val authors: Set<String> = emptySet()) {
    /** Returns why [work] is hidden, or null if it isn't. */
    fun reasonFor(work: WorkSummary): String? {
        work.authors.firstOrNull { authorMatches(it) }?.let { return "Blocked author: $it" }
        work.allTags().firstOrNull { it.lowercase() in tags }?.let { return "Blocked tag: $it" }
        return null
    }

    /** Matches "pseud (username)" bylines against either the pseud or the username. */
    private fun authorMatches(byline: String): Boolean {
        val b = byline.lowercase()
        if (b in authors) return true
        val m = Regex("""^(.*) \((.*)\)$""").find(b) ?: return false
        return m.groupValues[1] in authors || m.groupValues[2] in authors
    }
}

/** Each site's block list; a work is checked against the list of the site it comes from. */
data class BlockLists(val ao3: BlockList = BlockList(), val ffn: BlockList = BlockList()) {
    fun reasonFor(work: WorkSummary): String? = forSite(work.site).reasonFor(work)
    fun forSite(site: Site) = if (site == Site.AO3) ao3 else ffn
}

class FilterRepository(db: AppDatabase) {
    private val blockedDao = db.blocked()
    private val favoritesDao = db.favoriteTags()

    val blocked: Flow<List<Blocked>> = blockedDao.observeAll()
    val blockLists: Flow<BlockLists> = blocked.map { list ->
        fun of(site: Site) = list.filter { it.site == site }.let { l ->
            BlockList(
                tags = l.filter { it.kind == BlockKind.TAG }.map { it.value.lowercase() }.toSet(),
                authors = l.filter { it.kind == BlockKind.AUTHOR }.map { it.value.lowercase() }.toSet(),
            )
        }
        BlockLists(of(Site.AO3), of(Site.FFN))
    }
    val favorites: Flow<List<FavoriteTag>> = favoritesDao.observeAll()

    fun favorites(site: Site): Flow<List<FavoriteTag>> = favorites.map { l -> l.filter { it.site == site } }

    suspend fun block(site: Site, kind: BlockKind, value: String) {
        if (value.isNotBlank()) blockedDao.insert(Blocked(kind = kind, value = value.trim(), site = site))
    }

    suspend fun unblock(site: Site, kind: BlockKind, value: String) = blockedDao.delete(site, kind, value)

    suspend fun addFavorite(site: Site, name: String, section: String, target: String = name) {
        if (name.isNotBlank()) favoritesDao.insert(FavoriteTag(site, name.trim(), section, target))
    }

    suspend fun removeFavorite(site: Site, name: String) = favoritesDao.delete(site, name)

    /** First launch: pin a few examples so the Categories tab isn't empty. */
    suspend fun seedDefaultsIfEmpty() {
        if (favoritesDao.count(Site.AO3) == 0) {
            listOf(
                "Dragon Ball Z" to SECTION_FANDOMS,
                "POV Male Character" to SECTION_POV,
                "Romance" to SECTION_GENRES,
                "Drama" to SECTION_GENRES,
            ).forEach { (name, section) -> favoritesDao.insert(FavoriteTag(Site.AO3, name, section)) }
        }
        if (favoritesDao.count(Site.FFN) == 0) {
            favoritesDao.insert(FavoriteTag(Site.FFN, "Dragon Ball Z", SECTION_FANDOMS, "/anime/Dragon-Ball-Z/"))
            favoritesDao.insert(FavoriteTag(Site.FFN, "Romance", SECTION_GENRES, "genre:2"))
            favoritesDao.insert(FavoriteTag(Site.FFN, "Drama", SECTION_GENRES, "genre:4"))
        }
    }

    companion object {
        const val SECTION_FANDOMS = "Fandoms & series"
        const val SECTION_GENRES = "Genres & tropes"
        const val SECTION_POV = "Point of view"
        const val SECTION_CHARACTERS = "Characters & ships"
        const val SECTION_OTHER = "Other"
        val SECTIONS = listOf(SECTION_FANDOMS, SECTION_GENRES, SECTION_POV, SECTION_CHARACTERS, SECTION_OTHER)
        val FFN_SECTIONS = listOf(SECTION_FANDOMS, SECTION_GENRES, SECTION_CHARACTERS, SECTION_OTHER)
    }
}
