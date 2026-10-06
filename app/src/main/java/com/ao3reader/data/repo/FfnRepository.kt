package com.ao3reader.data.repo

import com.ao3reader.data.model.FfnFandom
import com.ao3reader.data.model.FfnFilter
import com.ao3reader.data.model.FfnFilterOptions
import com.ao3reader.data.model.FfnGenres
import com.ao3reader.data.model.FfnMedia
import com.ao3reader.data.model.FfnRating
import com.ao3reader.data.model.FfnStatus
import com.ao3reader.data.model.ReviewPage
import com.ao3reader.data.model.WorkDetail
import com.ao3reader.data.model.WorkIds
import com.ao3reader.data.model.WorkPage
import com.ao3reader.data.model.WorkSummary
import com.ao3reader.data.remote.ffn.FfnClient
import com.ao3reader.data.remote.ffn.FfnException
import com.ao3reader.data.remote.ffn.FfnParser
import com.ao3reader.data.remote.ffn.FfnUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything that comes from FanFiction.net. Ids passed in and out are the app's (negated) ids. */
class FfnRepository(private val client: FfnClient) {

    private val workCache = object : LinkedHashMap<Long, WorkDetail>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, WorkDetail>?) = size > 6
    }
    private val fandomCache = mutableMapOf<String, List<FfnFandom>>()
    private val optionsCache = mutableMapOf<String, FfnFilterOptions>()

    /** Characters and languages offered for a fandom, once its first page has been loaded. */
    fun cachedOptions(fandomPath: String): FfnFilterOptions? = optionsCache[fandomPath]

    suspend fun filterOptions(fandomPath: String): FfnFilterOptions {
        optionsCache[fandomPath]?.let { return it }
        val html = client.get(FfnUrls.fandomStories(fandomPath, FfnFilter(), 1))
        return parse { FfnParser.parseFilterOptions(html) }.also { optionsCache[fandomPath] = it }
    }

    /**
     * Browses a fandom (FanFiction.net's own filters) or searches by keywords, then applies what the
     * site can't filter itself. Skips ahead a few pages when every result on a page was filtered out.
     */
    suspend fun search(filter: FfnFilter, page: Int): WorkPage {
        val fandom = filter.fandom
        if ((fandom == null || fandom.path.isBlank()) && filter.keywords.isBlank()) {
            throw FfnException("Pick a fandom or type something to search for.")
        }
        var p = page
        while (true) {
            val url = if (filter.keywords.isBlank() && fandom != null) {
                FfnUrls.fandomStories(fandom.path, filter, p)
            } else {
                FfnUrls.search(listOfNotNull(filter.keywords, fandom?.name).joinToString(" "), p)
            }
            val html = client.get(url)
            if (fandom != null && p == 1 && filter.keywords.isBlank()) {
                optionsCache.getOrPut(fandom.path) { parse { FfnParser.parseFilterOptions(html) } }
            }
            val result = parse { FfnParser.parseStoryList(html, p, fandom?.name.takeIf { filter.keywords.isBlank() }) }
            val kept = result.works.filter { matches(it, filter, serverFiltered = filter.keywords.isBlank() && fandom != null) }
            if (kept.isNotEmpty() || p >= result.totalPages || p - page >= 3) {
                return result.copy(works = kept, page = p)
            }
            p++
        }
    }

    /** True if [w] passes the parts of [f] FanFiction.net didn't already apply. */
    private fun matches(w: WorkSummary, f: FfnFilter, serverFiltered: Boolean): Boolean {
        val genres = w.freeforms.map { it.lowercase() }.toSet()
        val chars = w.characters.map { it.lowercase() }
        if (f.excludeGenres.any { it.label.lowercase() in genres }) return false
        f.minWords?.let { if (w.words < it) return false }
        f.maxWords?.let { if (w.words > it) return false }
        if (serverFiltered) return true
        if (f.includeGenres.any { it.label.lowercase() !in genres }) return false
        if (f.includeCharacters.any { c -> chars.none { it.contains(c.label.lowercase()) } }) return false
        if (f.excludeCharacters.any { c -> chars.any { it.contains(c.label.lowercase()) } }) return false
        if (f.rating.short.isNotEmpty() && !w.rating.equals(f.rating.short, true)) return false
        if (f.rating == FfnRating.K_T && w.rating.equals("M", true)) return false
        if (f.status == FfnStatus.COMPLETE && !w.complete) return false
        if (f.status == FfnStatus.IN_PROGRESS && w.complete) return false
        return true
    }

    suspend fun fandoms(mediaPath: String): List<FfnFandom> {
        fandomCache[mediaPath]?.let { return it }
        val html = client.get(FfnUrls.media(mediaPath))
        return parse { FfnParser.parseFandoms(html) }.also { fandomCache[mediaPath] = it }
    }

    /**
     * Finds a fandom by its exact name across every media section (each section is one request,
     * cached). Used to turn a fandom name on a story into something browsable.
     */
    suspend fun findFandom(name: String): FfnFandom? {
        val wanted = name.trim().lowercase()
        if (wanted.isEmpty()) return null
        fandomCache.values.flatten().firstOrNull { it.name.lowercase() == wanted }?.let { return it }
        if (FfnGenres.byName(name) != null) return null
        for (media in FfnMedia.entries) {
            fandoms(media.path).firstOrNull { it.name.lowercase() == wanted }?.let { return it }
        }
        return null
    }

    /** Story metadata, the chapter list and chapter 1's text. Later chapters load with [chapterText]. */
    suspend fun fullWork(id: Long, forceRefresh: Boolean = false): WorkDetail {
        if (!forceRefresh) synchronized(workCache) { workCache[id] }?.let { return it }
        val storyId = WorkIds.remote(id)
        val html = client.get(FfnUrls.chapter(storyId, 1))
        val work = parse { FfnParser.parseStoryPage(html, storyId, 1) }
        synchronized(workCache) { workCache[id] = work }
        return work
    }

    suspend fun chapterText(id: Long, chapter: Int): String {
        synchronized(workCache) { workCache[id] }?.chapters?.getOrNull(chapter - 1)?.contentHtml
            ?.takeIf { it.isNotBlank() }?.let { return it }
        val html = client.get(FfnUrls.chapter(WorkIds.remote(id), chapter))
        val text = parse { FfnParser.parseChapterText(html) }
        synchronized(workCache) {
            workCache[id]?.let { w ->
                workCache[id] = w.copy(chapters = w.chapters.map { if (it.index == chapter) it.copy(contentHtml = text) else it })
            }
        }
        return text
    }

    suspend fun authorStories(userId: String): Pair<String, List<WorkSummary>> {
        val html = client.get(FfnUrls.author(userId))
        return parse { FfnParser.parseAuthorStories(html, userId) }
    }

    suspend fun reviews(id: Long, page: Int): ReviewPage {
        val html = client.get(FfnUrls.reviews(WorkIds.remote(id), page))
        return parse { FfnParser.parseReviews(html, page) }
    }

    /** The signed-in user's followed stories (story alerts), all pages. */
    suspend fun followedStories(): List<WorkSummary> = accountList(FfnUrls.FOLLOWS)

    /** The signed-in user's favorite stories, all pages. */
    suspend fun favoriteStories(): List<WorkSummary> = accountList(FfnUrls.FAVORITES)

    private suspend fun accountList(base: String): List<WorkSummary> {
        val all = mutableListOf<WorkSummary>()
        var page = 1
        do {
            val html = client.get(if (page == 1) base else "$base?p=$page")
            val result = parse { FfnParser.parseAccountList(html, page) }
            all += result.works
            page++
        } while (page <= result.totalPages && page <= 50 && result.works.isNotEmpty())
        return all.distinctBy { it.id }
    }

    /** Null when signed out; otherwise the username (or "" if the page doesn't show it). */
    suspend fun signedInUser(): String? {
        val html = client.get(FfnUrls.FOLLOWS)
        if (FfnParser.isLoginPage(html)) return null
        return FfnParser.signedInUser(html).orEmpty()
    }

    /**
     * Follows and/or favorites a story on FanFiction.net the way its own Follow/Fav button does,
     * from inside the story page so the signed-in session and site checks apply.
     */
    suspend fun addToAccount(id: Long, follow: Boolean, favorite: Boolean): Boolean {
        val storyId = WorkIds.remote(id)
        val script = """
            (function() {
              var author = (typeof userid !== 'undefined') ? userid : 0;
              var body = 'storyid=$storyId&userid=' + author + '&storyalert=${if (follow) 1 else 0}' +
                         '&favstory=${if (favorite) 1 else 0}&authoralert=0&favauthor=0';
              fetch('/api/ajax_subs.php', {
                method: 'POST', credentials: 'include',
                headers: {'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest'},
                body: body
              }).then(function(r) { return r.text(); })
                .then(function(t) { Bridge.done(t); })
                .catch(function(e) { Bridge.done('ERROR ' + e); });
            })();
        """.trimIndent()
        val answer = client.runOnPage(FfnUrls.chapter(storyId, 1), script)
        return answer.contains("\"error\":false") || answer.contains("\"error\": false")
    }

    private suspend fun <T> parse(block: () -> T): T = withContext(Dispatchers.Default) { block() }
}
