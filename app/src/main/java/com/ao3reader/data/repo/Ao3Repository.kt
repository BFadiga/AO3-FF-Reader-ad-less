package com.ao3reader.data.repo

import com.ao3reader.data.model.ChapterRef
import com.ao3reader.data.model.FandomEntry
import com.ao3reader.data.model.WorkDetail
import com.ao3reader.data.model.WorkFilter
import com.ao3reader.data.model.WorkPage
import com.ao3reader.data.model.WorkSummary
import com.ao3reader.data.remote.Ao3Client
import com.ao3reader.data.remote.Ao3Exception
import com.ao3reader.data.remote.Ao3Parser
import com.ao3reader.data.remote.Ao3Urls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything that comes from AO3 itself. */
class Ao3Repository(private val client: Ao3Client) {

    private val workCache = object : LinkedHashMap<Long, WorkDetail>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, WorkDetail>?) = size > 6
    }
    private val fandomCache = mutableMapOf<String, List<FandomEntry>>()

    suspend fun tagWorks(tag: String, filter: WorkFilter, page: Int): WorkPage =
        parseList(client.get(Ao3Urls.tagWorks(tag, filter, page)))

    /**
     * Free-text search. When tags are included, AO3's tag filter is used instead because it
     * supports exclusions and matches tags exactly.
     */
    suspend fun search(filter: WorkFilter, page: Int): WorkPage {
        val url = if (filter.includeTags.isNotEmpty() && filter.title.isBlank() && filter.author.isBlank()) {
            Ao3Urls.tagWorks(filter.includeTags.first(), filter.copy(includeTags = filter.includeTags.drop(1)), page)
        } else {
            Ao3Urls.search(filter, page)
        }
        return parseList(client.get(url))
    }

    suspend fun fullWork(id: Long, forceRefresh: Boolean = false): WorkDetail {
        if (!forceRefresh) synchronized(workCache) { workCache[id] }?.let { return it }
        val html = client.get(Ao3Urls.fullWork(id))
        val work = withContext(Dispatchers.Default) { Ao3Parser.parseFullWork(html, id) }
        synchronized(workCache) { workCache[id] = work }
        return work
    }

    suspend fun chapterIndex(id: Long): List<ChapterRef> {
        val html = client.get(Ao3Urls.chapterIndex(id))
        return withContext(Dispatchers.Default) { Ao3Parser.parseChapterIndex(html) }
    }

    suspend fun fandoms(mediaPath: String): List<FandomEntry> {
        fandomCache[mediaPath]?.let { return it }
        val html = client.get(Ao3Urls.mediaFandoms(mediaPath))
        val list = withContext(Dispatchers.Default) { Ao3Parser.parseFandomIndex(html) }
        fandomCache[mediaPath] = list
        return list
    }

    /** Just the metadata of a work (reads its first chapter page). */
    suspend fun workMeta(id: Long): WorkSummary {
        val html = client.get(Ao3Urls.workMeta(id))
        return withContext(Dispatchers.Default) { Ao3Parser.parseFullWork(html, id).summary }
    }

    suspend fun authorWorks(authorPath: String?, byline: String, page: Int): WorkPage =
        parseList(client.get(Ao3Urls.authorWorks(authorPath, byline, page)))

    /** Leaves kudos. Returns false when AO3 says they were already left. */
    suspend fun leaveKudos(id: Long): Boolean {
        val form = fullWork(id).actions.kudosForm ?: fullWork(id, forceRefresh = true).actions.kudosForm
            ?: throw Ao3Exception("AO3 didn't offer a kudos button for this work.")
        val code = client.submit(form, Ao3Urls.work(id))
        return when (code) {
            in 200..399 -> true
            422 -> false
            else -> throw Ao3Exception("AO3 didn't accept the kudos (error $code).")
        }
    }

    /** Subscribes to or unsubscribes from a work on AO3 (signed in only). Returns the new state. */
    suspend fun setSubscribed(id: Long, subscribed: Boolean): Boolean {
        val actions = fullWork(id, forceRefresh = true).actions
        if (actions.subscribed == subscribed) return subscribed
        val form = actions.subscribeForm ?: throw Ao3Exception("Sign in to AO3 to subscribe.")
        val code = client.submit(form, Ao3Urls.work(id))
        if (code !in 200..399) throw Ao3Exception("AO3 didn't accept that (error $code).")
        synchronized(workCache) { workCache.remove(id) }
        return subscribed
    }

    /** The signed-in AO3 username, or null when signed out. */
    suspend fun signedInUser(): String? = Ao3Parser.signedInUser(client.get(Ao3Urls.BASE + "/"))

    /** Every work the user is subscribed to (titles and authors only). */
    suspend fun subscriptions(user: String): List<WorkSummary> {
        val all = mutableListOf<WorkSummary>()
        var page = 1
        do {
            val result = withContext(Dispatchers.Default) { Ao3Parser.parseSubscriptions(client.get(Ao3Urls.subscriptions(user, page))) }
            all += result.works
            page++
        } while (page <= result.totalPages && page <= 50)
        return all.distinctBy { it.id }
    }

    fun forget(id: Long) = synchronized(workCache) { workCache.remove(id) }

    suspend fun autocompleteTags(term: String): List<String> =
        if (term.length < 2) emptyList() else client.autocomplete(Ao3Urls.tagAutocomplete(term))

    private suspend fun parseList(html: String) = withContext(Dispatchers.Default) { Ao3Parser.parseWorkList(html) }
}
