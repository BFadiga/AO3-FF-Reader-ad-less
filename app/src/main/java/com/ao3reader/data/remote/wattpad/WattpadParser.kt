package com.ao3reader.data.remote.wattpad

import com.ao3reader.data.model.Chapter
import com.ao3reader.data.model.Review
import com.ao3reader.data.model.WorkDetail
import com.ao3reader.data.model.WorkIds
import com.ao3reader.data.model.WorkPage
import com.ao3reader.data.model.WorkSummary
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/** Reads Wattpad's JSON API answers into the app's models. */
object WattpadParser {

    fun parseStory(o: JSONObject): WorkSummary {
        val id = o.get("id").toString().toLong()
        val user = o.optJSONObject("user")
        val name = user?.optString("name").orEmpty()
        val parts = o.optInt("numParts", o.optJSONArray("parts")?.length() ?: 1).coerceAtLeast(1)
        val complete = o.optBoolean("completed")
        return WorkSummary(
            id = WorkIds.wattpad(id),
            title = o.optString("title").trim(),
            authors = listOfNotNull(name.ifBlank { null }),
            authorIds = listOfNotNull(name.ifBlank { null }),
            rating = if (o.optBoolean("mature")) "Mature" else "",
            freeforms = strings(o.optJSONArray("tags")),
            summaryHtml = descriptionHtml(o.optString("description")),
            language = o.optJSONObject("language")?.optString("name").orEmpty(),
            chaptersPosted = parts,
            chaptersTotal = if (complete) parts else null,
            kudos = o.optInt("voteCount"),
            hits = o.optInt("readCount"),
            comments = o.optInt("commentCount"),
            updated = o.optString("modifyDate").take(10),
            complete = complete,
            coverUrl = o.optString("cover").ifBlank { null },
        )
    }

    /** A page of stories from search, an author's list or a library. */
    fun parseStoryList(json: String, page: Int, pageSize: Int): WorkPage {
        val o = JSONObject(json)
        val stories = o.optJSONArray("stories") ?: JSONArray()
        val works = (0 until stories.length()).map { parseStory(stories.getJSONObject(it)) }
        val total = o.optInt("total", works.size)
        val pages = ((total + pageSize - 1) / pageSize).coerceAtLeast(1)
        return WorkPage(works, page, pages, heading = if (total > 0) "$total stories" else null)
    }

    /** Story details and its chapter list; chapter texts are fetched one by one. */
    fun parseStoryDetail(json: String): WorkDetail {
        val o = JSONObject(json)
        val summary = parseStory(o)
        val parts = o.optJSONArray("parts") ?: JSONArray()
        val chapters = (0 until parts.length()).map { i ->
            val p = parts.getJSONObject(i)
            Chapter(index = i + 1, id = p.get("id").toString().toLong(), title = p.optString("title").ifBlank { "Part ${i + 1}" }, contentHtml = "")
        }
        return WorkDetail(
            summary = summary.copy(chaptersPosted = chapters.size.coerceAtLeast(1)),
            published = o.optString("createDate").take(10).ifBlank { null },
            notesHtml = null,
            chapters = chapters,
        )
    }

    /** Keeps the writer's formatting (bold, italics, images) and drops Wattpad's reader markup. */
    fun cleanPartHtml(html: String): String {
        val safelist = Safelist.relaxed().addTags("hr", "center").removeTags("a")
        return Jsoup.clean(html, "https://www.wattpad.com/", safelist)
    }

    fun parseComments(json: String): List<Review> {
        val arr = JSONObject(json).optJSONArray("comments") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            Review(
                author = c.optJSONObject("user")?.optString("name").orEmpty(),
                meta = c.optString("created").take(10),
                text = c.optString("body"),
            )
        }
    }

    fun parseUsername(json: String): String? = runCatching { JSONObject(json).optString("username").ifBlank { null } }.getOrNull()

    /** The signed-in username embedded in a Wattpad web page, if any. */
    fun usernameFromPage(html: String): String? =
        Regex(""""currentUser"\s*:\s*\{[^{}]*?"username"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: Regex("""wattpad\.currentUser\s*=\s*\{[^{}]*?"username"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }.filter { it.isNotBlank() }

    private fun descriptionHtml(text: String): String =
        text.trim().split(Regex("""\n\s*\n""")).joinToString("") { p ->
            "<p>" + p.escapeHtml().replace("\n", "<br>") + "</p>"
        }

    private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
