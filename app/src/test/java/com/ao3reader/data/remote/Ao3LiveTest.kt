package com.ao3reader.data.remote

import com.ao3reader.data.model.WorkFilter
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** Hits the real AO3. Skipped unless LIVE_AO3=1 is set: `LIVE_AO3=1 ./gradlew testDebugUnitTest --tests '*Ao3LiveTest*'`. */
class Ao3LiveTest {
    private fun live() = assumeTrue(System.getenv("LIVE_AO3") == "1")

    /** AO3's Cloudflare edge intermittently answers 52x; retry those a few times. */
    private fun fetch(url: String): String {
        repeat(8) {
            Thread.sleep(2_000L + it * 3_000L)
            val c = URL(url).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", Ao3Client.USER_AGENT)
            c.instanceFollowRedirects = true
            println("GET $url -> ${c.responseCode}")
            if (c.responseCode !in 520..529) return c.inputStream.bufferedReader().readText()
        }
        error("AO3 kept answering 52x for $url")
    }

    private fun dump(page: com.ao3reader.data.model.WorkPage) {
        println("heading=${page.heading} page=${page.page}/${page.totalPages} works=${page.works.size}")
        page.works.take(3).forEach { println("  $it") }
    }

    @Test
    fun searchPage() {
        live()
        val page = Ao3Parser.parseWorkList(fetch(Ao3Urls.search(WorkFilter(query = "Vegeta"), 1)))
        dump(page)
        assertTrue(page.works.size >= 10)
        assertTrue(page.totalPages > 1)
        assertTrue(page.works.all { it.title.isNotBlank() && it.words > 0 && it.updated.matches(Regex("""\d{4}-\d{2}-\d{2}""")) })
        assertTrue(page.works.any { it.freeforms.isNotEmpty() })
    }

    @Test
    fun tagPage() {
        live()
        val page = Ao3Parser.parseWorkList(fetch(Ao3Urls.tagWorks("Dragon Ball", WorkFilter(excludeTags = listOf("Explicit")), 1)))
        dump(page)
        assertTrue(page.works.size >= 10)
        assertTrue(page.works.none { it.rating == "Explicit" })
    }

    @Test
    fun workPages() {
        live()
        val list = Ao3Parser.parseWorkList(fetch(Ao3Urls.tagWorks("Dragon Ball Z", WorkFilter(), 1)))
        val multi = list.works.first { it.chaptersPosted > 1 }
        val single = list.works.firstOrNull { it.chaptersPosted == 1 }
        for (w in listOfNotNull(multi, single)) {
            val d = Ao3Parser.parseFullWork(fetch(Ao3Urls.fullWork(w.id)), w.id)
            println("work ${w.id}: ${d.summary.title} by ${d.summary.authors} chapters=${d.chapters.size} " +
                "posted=${d.summary.chaptersPosted}/${d.summary.chaptersTotal} updated=${d.summary.updated} published=${d.published}")
            d.chapters.take(3).forEach { println("   ch${it.index} id=${it.id} '${it.title}' len=${it.contentHtml.length}") }
            assertTrue(d.summary.title == w.title)
            assertTrue(d.chapters.size == w.chaptersPosted)
            assertTrue(d.chapters.all { it.contentHtml.length > 50 && !it.contentHtml.contains("landmark") })
            if (w.chaptersPosted > 1) {
                val idx = Ao3Parser.parseChapterIndex(fetch(Ao3Urls.chapterIndex(w.id)))
                println("   index: ${idx.take(3)}")
                assertTrue(idx.size == w.chaptersPosted && idx.all { it.id != null && it.date != null })
            }
        }
    }

    @Test
    fun fandomIndex() {
        live()
        val fandoms = Ao3Parser.parseFandomIndex(fetch(Ao3Urls.mediaFandoms("Anime & Manga")))
        println("fandoms=${fandoms.size} ${fandoms.take(5)}")
        assertTrue(fandoms.size > 100 && fandoms.any { it.name.startsWith("Dragon Ball") && it.count > 0 })
    }

    @Test
    fun authorPagesAndGuestActions() {
        live()
        val list = Ao3Parser.parseWorkList(fetch(Ao3Urls.tagWorks("Dragon Ball Z", WorkFilter(), 1)))
        val w = list.works.first { it.authorIds.firstOrNull()?.startsWith("/users/") == true }
        println("author ${w.authors} ids=${w.authorIds}")
        val byAuthor = Ao3Parser.parseWorkList(fetch(Ao3Urls.authorWorks(w.authorIds.first(), w.authors.first(), 1)))
        dump(byAuthor)
        assertTrue(byAuthor.works.any { it.id == w.id })
        val byName = Ao3Parser.parseWorkList(fetch(Ao3Urls.authorWorks(null, w.authors.first(), 1)))
        assertTrue(byName.works.isNotEmpty())

        val d = Ao3Parser.parseFullWork(fetch(Ao3Urls.fullWork(w.id)), w.id)
        println("actions: ${d.actions}")
        assertTrue(d.actions.signedInAs == null)
        val kudos = d.actions.kudosForm!!
        assertTrue(kudos.action == "/works/${w.id}/kudos" || kudos.action.endsWith("/kudos"))
        assertTrue(kudos.fields.any { it.first == "authenticity_token" && it.second.isNotBlank() })
        assertTrue(kudos.fields.any { it.first.contains("commentable_id") && it.second == w.id.toString() })
        assertTrue(d.summary.authorIds.isNotEmpty())
    }
}
