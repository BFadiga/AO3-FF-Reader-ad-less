package com.ao3reader.data.repo

import com.ao3reader.data.model.ReviewPage
import com.ao3reader.data.model.WattpadFilter
import com.ao3reader.data.model.WattpadSort
import com.ao3reader.data.model.WorkDetail
import com.ao3reader.data.model.WorkIds
import com.ao3reader.data.model.WorkPage
import com.ao3reader.data.model.WorkSummary
import com.ao3reader.data.remote.wattpad.WattpadClient
import com.ao3reader.data.remote.wattpad.WattpadException
import com.ao3reader.data.remote.wattpad.WattpadParser
import com.ao3reader.data.remote.wattpad.WattpadUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything that comes from Wattpad. Ids passed in and out are the app's ids (see [WorkIds]). */
class WattpadRepository(private val client: WattpadClient) {

    private val workCache = object : LinkedHashMap<Long, WorkDetail>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, WorkDetail>?) = size > 6
    }

    /**
     * Searches by text and tags, then applies what Wattpad can't filter. Wattpad only ranks search
     * results by relevance, so "most votes/reads" sorts a pool of the top [POOL] matches per page.
     */
    suspend fun search(filter: WattpadFilter, page: Int): WorkPage {
        if (filter.isEmpty) throw WattpadException("Type something or add a tag to search for.")
        if (filter.sort == WattpadSort.HOT) return hot(filter, page)
        val query = (listOf(filter.query.trim()) + filter.includeTags.map { "#" + it.replace(" ", "") })
            .filter { it.isNotBlank() }.joinToString(" ")
        val pooled = filter.sort != WattpadSort.BEST_MATCH
        val size = if (pooled) POOL else WattpadUrls.PAGE_SIZE
        val step = if (pooled) 50 else WattpadUrls.PAGE_SIZE
        var p = page
        while (true) {
            val works = mutableListOf<WorkSummary>()
            var total = 0
            var offset = (p - 1) * size
            while (offset < p * size) {
                val json = client.get(WattpadUrls.search(query, offset, filter.mature, step, filter.completeOnly, filter.updatedWithinDays))
                val result = parse { WattpadParser.parseStoryList(json, p, size) }
                works += result.works
                total = parse { org.json.JSONObject(json).optInt("total", works.size) }
                offset += step
                if (result.works.isEmpty() || offset >= total) break
            }
            val totalPages = ((total + size - 1) / size).coerceAtLeast(1)
            val kept = sorted(works.distinctBy { it.id }.filter { matches(it, filter) }, filter.sort)
            if (kept.isNotEmpty() || p >= totalPages || p - page >= 3) {
                val heading = when {
                    total == 0 -> null
                    pooled -> "$total stories · top ${(p - 1) * size + 1}–${(p * size).coerceAtMost(total)} matches by ${filter.sort.label.lowercase().removePrefix("most ")}"
                    else -> "$total stories"
                }
                return WorkPage(kept, p, totalPages, heading = heading)
            }
            p++
        }
    }

    /** The trending list for the first tag (or the search words as a tag). */
    private suspend fun hot(filter: WattpadFilter, page: Int): WorkPage {
        val tag = filter.includeTags.firstOrNull() ?: filter.query.trim().lowercase().replace(Regex("""\s+"""), "")
        var p = page
        while (true) {
            val json = client.get(WattpadUrls.hot(tag, (p - 1) * WattpadUrls.PAGE_SIZE))
            val result = parse { WattpadParser.parseStoryList(json, p, WattpadUrls.PAGE_SIZE) }
            val kept = result.works.filter { matches(it, filter) && filter.includeTags.drop(1).all { t -> t in it.freeforms.map(String::lowercase) } }
            if (kept.isNotEmpty() || p >= result.totalPages || p - page >= 3 || result.works.isEmpty()) {
                return result.copy(works = kept, page = p, heading = "Hot in #$tag")
            }
            p++
        }
    }

    private fun sorted(works: List<WorkSummary>, sort: WattpadSort) = when (sort) {
        WattpadSort.MOST_VOTES -> works.sortedByDescending { it.kudos }
        WattpadSort.MOST_READS -> works.sortedByDescending { it.hits }
        else -> works
    }

    private fun matches(w: WorkSummary, f: WattpadFilter): Boolean {
        val tags = w.freeforms.map { it.lowercase() }.toSet()
        if (f.excludeTags.any { it.lowercase().replace(" ", "") in tags }) return false
        if (f.completeOnly && !w.complete) return false
        if (!f.mature && w.rating.isNotBlank()) return false
        if (w.chaptersPosted !in f.length.parts) return false
        if (f.updatedWithinDays != null && f.sort == WattpadSort.HOT && !updatedWithin(w.updated, f.updatedWithinDays)) return false
        return true
    }

    private fun updatedWithin(date: String, days: Int): Boolean = runCatching {
        !java.time.LocalDate.parse(date).isBefore(java.time.LocalDate.now().minusDays(days.toLong()))
    }.getOrDefault(true)

    /** Story details and its parts; part texts load with [chapterText]. */
    suspend fun fullWork(id: Long, forceRefresh: Boolean = false): WorkDetail {
        if (!forceRefresh) synchronized(workCache) { workCache[id] }?.let { return it }
        val json = client.get(WattpadUrls.story(WorkIds.remote(id)))
        val work = parse { WattpadParser.parseStoryDetail(json) }
        synchronized(workCache) { workCache[id] = work }
        return work
    }

    suspend fun chapterText(id: Long, chapter: Int): String {
        val work = fullWork(id)
        val ref = work.chapters.getOrNull(chapter - 1) ?: return ""
        if (ref.contentHtml.isNotBlank()) return ref.contentHtml
        val partId = ref.id ?: return ""
        val raw = client.get(WattpadUrls.partText(partId))
        val text = parse { WattpadParser.cleanPartHtml(raw) }
        synchronized(workCache) {
            workCache[id]?.let { w ->
                workCache[id] = w.copy(chapters = w.chapters.map { if (it.index == chapter) it.copy(contentHtml = text) else it })
            }
        }
        return text
    }

    suspend fun authorStories(username: String): List<WorkSummary> = allPages(50) { WattpadUrls.authorStories(username, it) }

    /** Comments on one part; "page" n is part n, the way Wattpad keeps them. */
    suspend fun comments(id: Long, part: Int): ReviewPage {
        val work = fullWork(id)
        val partId = work.chapters.getOrNull(part - 1)?.id ?: return ReviewPage(emptyList(), part, work.chapters.size)
        val json = client.get(WattpadUrls.comments(partId))
        val reviews = parse { WattpadParser.parseComments(json) }
            .map { it.copy(meta = listOf("Part $part", it.meta).filter { m -> m.isNotBlank() }.joinToString(" · ")) }
        return ReviewPage(reviews, part, work.chapters.size.coerceAtLeast(1))
    }

    /** Null when signed out; otherwise the username ("" when signed in but the name couldn't be read). */
    suspend fun signedInUser(): String? {
        runCatching { WattpadParser.parseUsername(client.get(WattpadUrls.CURRENT_USER)) }.getOrNull()?.let { return it }
        return runCatching { WattpadParser.usernameFromPage(client.get(WattpadUrls.HOME)) }.getOrNull()
    }

    /** The stories in the user's Wattpad library (the app's "follows"). */
    suspend fun library(username: String): List<WorkSummary> = allPages(100) { WattpadUrls.library(username, it) }

    suspend fun setInLibrary(username: String, id: Long, add: Boolean) {
        val storyId = WorkIds.remote(id)
        if (add) client.post(WattpadUrls.libraryAdd(username), mapOf("stories" to storyId.toString()))
        else client.delete(WattpadUrls.libraryItem(username, storyId))
    }

    /** Votes for the latest part, Wattpad's way of liking a story. */
    suspend fun vote(id: Long) {
        val work = fullWork(id)
        val partId = work.chapters.lastOrNull()?.id ?: throw WattpadException("This story has no parts to vote on.")
        client.post(WattpadUrls.vote(WorkIds.remote(id), partId))
    }

    private suspend fun allPages(pageSize: Int, url: (offset: Int) -> String): List<WorkSummary> {
        val all = mutableListOf<WorkSummary>()
        var page = 1
        do {
            val json = client.get(url((page - 1) * pageSize))
            val result = parse { WattpadParser.parseStoryList(json, page, pageSize) }
            all += result.works
            page++
        } while (page <= result.totalPages && page <= 30 && result.works.isNotEmpty())
        return all.distinctBy { it.id }
    }

    private companion object {
        const val POOL = 100
    }

    private suspend fun <T> parse(block: () -> T): T = withContext(Dispatchers.Default) { block() }
}
