package com.ao3reader.wattpad

import com.ao3reader.data.model.WorkIds
import com.ao3reader.data.remote.wattpad.WattpadParser
import com.ao3reader.data.remote.wattpad.WattpadUrls
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Hits the real Wattpad API with the same addresses and parser the app uses. Skipped unless
 * LIVE_WATTPAD=1: `LIVE_WATTPAD=1 ./gradlew testDebugUnitTest --tests '*WattpadLiveTest*'`.
 */
class WattpadLiveTest {
    private val http = OkHttpClient()

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36")
            .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
            .build()
        http.newCall(request).execute().use { r ->
            val body = r.body!!.string()
            println("GET $url -> ${r.code} (${body.length} chars): ${body.take(300).replace("\n", " ")}")
            assertTrue("$url answered ${r.code}", r.isSuccessful)
            return body
        }
    }

    @Test
    fun searchStoryPartsCommentsAndAuthor() {
        assumeTrue(System.getenv("LIVE_WATTPAD") == "1")

        val search = WattpadParser.parseStoryList(get(WattpadUrls.search("dragon ball #romance", 0, true)), 1, WattpadUrls.PAGE_SIZE)
        println("search: ${search.works.size} works, ${search.totalPages} pages; first: ${search.works.firstOrNull()}")
        assertTrue(search.works.isNotEmpty())

        val first = search.works.first()
        val detail = WattpadParser.parseStoryDetail(get(WattpadUrls.story(WorkIds.remote(first.id))))
        println("story: ${detail.summary.title} by ${detail.summary.authors}, ${detail.chapters.size} parts, tags ${detail.summary.freeforms.take(5)}")
        assertTrue(detail.chapters.isNotEmpty())

        val part = detail.chapters.first()
        val text = WattpadParser.cleanPartHtml(get(WattpadUrls.partText(part.id!!)))
        println("part 1 text: ${text.length} chars: ${text.take(200)}")
        assertTrue(text.length > 200)

        val rawComments = get(WattpadUrls.comments(part.id!!))
        println("comment json: " + org.json.JSONObject(rawComments).optJSONArray("comments")?.optJSONObject(0)?.toString()?.take(900))
        val comments = WattpadParser.parseComments(rawComments)
        println("comments on part 1: ${comments.size}; first: ${comments.firstOrNull()}")

        val author = first.authors.first()
        val fields = "stories(id,title,user(name),numParts),total"
        listOf(
            WattpadUrls.authorStories(author, 0),
            "https://www.wattpad.com/v4/users/$author/stories/published?offset=0&limit=50&fields=$fields",
            "https://www.wattpad.com/v4/users/$author/stories?offset=0&limit=50&fields=$fields",
            "https://www.wattpad.com/api/v3/users/$author/stories?offset=0&limit=50&fields=$fields",
            "https://www.wattpad.com/api/v3/stories?filter=published&username=$author&fields=$fields",
            WattpadUrls.library(author, 0),
            "https://www.wattpad.com/api/v3/users/$author/library?fields=$fields",
            "https://www.wattpad.com/api/v3/users/$author?fields=username,numStoriesPublished",
            WattpadUrls.CURRENT_USER,
            "https://www.wattpad.com/v4/users/me",
        ).forEach { url -> runCatching { get(url) }.onFailure { println("FAILED: ${it.message?.take(200)}") } }
    }
}
