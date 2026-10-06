package com.ao3reader.data.remote

import com.ao3reader.data.model.CompletionFilter
import com.ao3reader.data.model.Rating
import com.ao3reader.data.model.WorkFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ao3ParserTest {
    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun parsesWorkListing() {
        val page = Ao3Parser.parseWorkList(fixture("works_list.html"))
        assertEquals(2, page.works.size)
        assertEquals(1, page.page)
        assertEquals(617, page.totalPages)

        val w = page.works[0]
        assertEquals(111L, w.id)
        assertEquals("Saiyan Pride", w.title)
        assertEquals(listOf("writer"), w.authors)
        assertEquals(listOf("Dragon Ball Z"), w.fandoms)
        assertEquals("Teen And Up Audiences", w.rating)
        assertEquals(listOf("Gen", "M/M"), w.categories)
        assertEquals(listOf("No Archive Warnings Apply"), w.warnings)
        assertEquals(listOf("POV Male Character", "Slow Burn"), w.freeforms)
        assertEquals(listOf("Part 2 of Prince Saga"), w.series)
        assertEquals(45210, w.words)
        assertEquals(7, w.chaptersPosted)
        assertNull(w.chaptersTotal)
        assertEquals(1204, w.kudos)
        assertEquals(20001, w.hits)
        assertEquals(31, w.comments)
        assertEquals("2026-10-05", w.updated)
        assertFalse(w.complete)
        assertTrue(w.summaryHtml.contains("The prince trains."))

        val anon = page.works[1]
        assertEquals(listOf("Anonymous"), anon.authors)
        assertEquals("Explicit", anon.rating)
        assertTrue(anon.complete)
        assertTrue("Explicit" in anon.allTags())
    }

    @Test
    fun parsesMultiChapterWork() {
        val work = Ao3Parser.parseFullWork(fixture("full_work.html"), 111)
        assertEquals("Saiyan Pride", work.summary.title)
        assertEquals(listOf("writer"), work.summary.authors)
        assertEquals("Teen And Up Audiences", work.summary.rating)
        assertEquals(listOf("Vegeta & Son Goku"), work.summary.relationships)
        assertEquals(2, work.summary.chaptersPosted)
        assertEquals(5, work.summary.chaptersTotal)
        assertEquals("2026-10-05", work.summary.updated)
        assertEquals("2026-01-01", work.published)
        assertEquals(listOf("Part 2 of Prince Saga"), work.summary.series)
        assertTrue(work.notesHtml!!.contains("Thanks for reading"))
        assertEquals(2, work.chapters.size)

        val c1 = work.chapters[0]
        assertEquals("Chapter 1: Gravity Room", c1.title)
        assertEquals(8L, c1.id)
        assertTrue(c1.contentHtml.contains("<em>500</em>"))
        assertFalse(c1.contentHtml.contains("Chapter Text"))
        assertTrue(c1.endNotesHtml!!.contains("More soon"))
        assertTrue(c1.summaryHtml!!.contains("Training."))
        assertEquals("Chapter 2", work.chapters[1].title)
    }

    @Test
    fun parsesOneShot() {
        val work = Ao3Parser.parseFullWork(fixture("oneshot.html"), 5)
        assertEquals(1, work.chapters.size)
        assertTrue(work.chapters[0].contentHtml.contains("Hello."))
        assertFalse(work.chapters[0].contentHtml.contains("Work Text"))
        assertTrue(work.summary.complete)
        assertEquals(listOf("Anonymous"), work.summary.authors)
    }

    @Test
    fun parsesChapterIndex() {
        val chapters = Ao3Parser.parseChapterIndex(fixture("navigate.html"))
        assertEquals(2, chapters.size)
        assertEquals("1. Gravity Room", chapters[0].title)
        assertEquals("2026-10-05", chapters[1].date)
        assertEquals(9L, chapters[1].id)
    }

    @Test
    fun parsesFandoms() {
        val fandoms = Ao3Parser.parseFandomIndex(fixture("fandoms.html"))
        assertEquals(2, fandoms.size)
        assertEquals("Dragon Ball Z", fandoms[0].name)
        assertEquals(5432, fandoms[0].count)
    }

    @Test
    fun reportsLockedWork() {
        val e = runCatching { Ao3Parser.parseFullWork(fixture("locked.html"), 1) }.exceptionOrNull()
        assertTrue(e is Ao3Exception)
        assertTrue(e!!.message!!.contains("logged-in"))
    }

    @Test
    fun escapesTagsLikeAo3() {
        assertEquals("Vegeta*s*Son%20Goku%20%7C%20Kakarot", Ao3Urls.escapeTag("Vegeta/Son Goku | Kakarot"))
        assertEquals("Anime%20*a*%20Manga", Ao3Urls.escapeTag("Anime & Manga"))
        assertEquals("Dr*d*%20Stone", Ao3Urls.escapeTag("Dr. Stone"))
    }

    @Test
    fun buildsFilterUrls() {
        val url = Ao3Urls.tagWorks(
            "Dragon Ball Z",
            WorkFilter(excludeTags = listOf("Major Character Death"), rating = Rating.TEEN, completion = CompletionFilter.COMPLETE),
            page = 3,
        )
        assertTrue(url.startsWith("https://archiveofourown.org/works?"))
        assertTrue(url.contains("tag_id=Dragon+Ball+Z"))
        assertTrue(url.contains("work_search%5Bexcluded_tag_names%5D=Major+Character+Death"))
        assertTrue(url.contains("work_search%5Brating_ids%5D%5B%5D=11"))
        assertTrue(url.contains("work_search%5Bcomplete%5D=T"))
        assertTrue(url.endsWith("page=3"))

        val search = Ao3Urls.search(WorkFilter(query = "vegeta", excludeTags = listOf("Angst")), 1)
        assertTrue(search.contains("work_search%5Bquery%5D=vegeta+-%22Angst%22"))
        assertEquals(42L, Ao3Urls.workIdFrom("/works/42/chapters/7"))
    }
}
