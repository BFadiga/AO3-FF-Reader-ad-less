package com.ao3reader.data.remote.ffn

import com.ao3reader.data.model.FfnFandom
import com.ao3reader.data.model.FfnFilter
import com.ao3reader.data.model.FfnGenres
import com.ao3reader.data.model.FfnOption
import com.ao3reader.data.model.FfnRating
import com.ao3reader.data.model.WorkIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfnParserTest {
    private fun res(name: String) = javaClass.getResource("/ffn/$name")!!.readText()

    @Test
    fun storyPage() {
        val w = FfnParser.parseStoryPage(res("story.html"), 13579, 2)
        val s = w.summary
        assertEquals(WorkIds.ffn(13579), s.id)
        assertEquals("The Saiyan Path", s.title)
        assertEquals(listOf("SomeAuthor"), s.authors)
        assertEquals(listOf("1234567"), s.authorIds)
        assertEquals(listOf("Dragon Ball Z"), s.fandoms)
        assertEquals("T", s.rating)
        assertEquals("English", s.language)
        assertEquals(listOf("Adventure", "Hurt/Comfort"), s.freeforms)
        assertEquals(listOf("Goku", "Chi-Chi", "Vegeta"), s.characters)
        assertEquals(12, s.chaptersPosted)
        assertEquals(45321, s.words)
        assertEquals(123, s.comments)
        assertEquals(456, s.kudos)
        assertEquals(789, s.follows)
        assertEquals("2020-09-13", s.updated)
        assertEquals("2019-01-01", w.published)
        assertTrue(s.complete)
        assertEquals("https://www.fanfiction.net/image/6543210/180/", s.coverUrl)
        assertTrue(s.summaryHtml.contains("&lt;Vegeta&gt;"))
        assertEquals(12, w.chapters.size)
        assertEquals("Training", w.chapters[1].title)
        assertTrue(w.chapters[1].contentHtml.contains("<strong>trained</strong>"))
        assertFalse(w.chapters[1].contentHtml.contains("script"))
        assertEquals("", w.chapters[0].contentHtml)
        assertTrue(FfnParser.parseChapterText(res("story.html")).contains("text-align:center"))
    }

    @Test
    fun fandomListing() {
        val page = FfnParser.parseStoryList(res("list.html"), 1, "Dragon Ball Z")
        assertEquals(2, page.works.size)
        assertEquals(1520, page.totalPages)
        val a = page.works[0]
        assertEquals("First Story", a.title)
        assertEquals(WorkIds.ffn(111), a.id)
        assertEquals(listOf("Writer One"), a.authors)
        assertEquals(listOf("333"), a.authorIds)
        assertEquals(listOf("Dragon Ball Z"), a.fandoms)
        assertEquals("T", a.rating)
        assertEquals(listOf("Romance", "Drama"), a.freeforms)
        assertEquals(listOf("Goku", "Vegeta"), a.characters)
        assertEquals(5, a.chaptersPosted)
        assertEquals("https://www.fanfiction.net/image/222/75/", a.coverUrl)
        assertEquals("A summary about Goku.", a.summaryHtml)
        assertFalse(a.complete)
        val b = page.works[1]
        assertEquals("K+", b.rating)
        assertEquals("Spanish", b.language)
        assertEquals(listOf("Humor"), b.freeforms)
        assertEquals(1, b.chaptersPosted)
        assertTrue(b.complete)
        assertNull(b.coverUrl)

        val opts = FfnParser.parseFilterOptions(res("list.html"))
        assertEquals(listOf(FfnOption("1234", "Goku"), FfnOption("5678", "Vegeta")), opts.characters)
        assertEquals(2, opts.languages.size)
        assertEquals(2, opts.genres.size)
    }

    @Test
    fun searchResultsNameTheirFandom() {
        val page = FfnParser.parseStoryList(res("search.html"), 1)
        assertEquals(7, page.totalPages)
        assertEquals(listOf("Naruto", "Dragon Ball Z"), page.works[0].fandoms)
        assertEquals("M", page.works[0].rating)
        assertEquals("https://ffcdn.example.net/image/888/75/", page.works[0].coverUrl)
        assertEquals(listOf("Dragon Ball Z"), page.works[1].fandoms)
        assertEquals(listOf("Goku"), page.works[1].characters)
        assertTrue(page.works[1].freeforms.isEmpty())
    }

    @Test
    fun fandomsAndReviews() {
        val f = FfnParser.parseFandoms(res("fandoms.html"))
        assertEquals(listOf(FfnFandom("Dragon Ball Z", "/anime/Dragon-Ball-Z/", "15.2K"), FfnFandom("Naruto", "/anime/Naruto/", "436K")), f)
        val r = FfnParser.parseReviews(res("reviews.html"), 1)
        assertEquals(2, r.reviews.size)
        assertEquals("Fan", r.reviews[0].author)
        assertEquals("Loved it!", r.reviews[0].text)
        assertEquals("Guest", r.reviews[1].author)
        assertEquals(4, r.totalPages)
    }

    @Test
    fun filterUrl() {
        val romance = FfnGenres.byName("Romance")!!
        val angst = FfnGenres.byName("Angst")!!
        val url = FfnUrls.fandomStories(
            "/anime/Dragon-Ball-Z/",
            FfnFilter(rating = FfnRating.T, includeGenres = listOf(romance), excludeGenres = listOf(angst), includeCharacters = listOf(FfnOption("1234", "Goku"))),
            3,
        )
        assertEquals("https://www.fanfiction.net/anime/Dragon-Ball-Z/?&srt=1&r=3&g1=2&_g1=10&c1=1234&p=3", url)
    }
}
